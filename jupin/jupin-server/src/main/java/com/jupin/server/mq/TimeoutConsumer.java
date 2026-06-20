package com.jupin.server.mq;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.jupin.common.constant.*;
import com.jupin.pojo.entity.CarPool;
import com.jupin.pojo.entity.Order;
import com.jupin.pojo.entity.PoolMember;
import com.jupin.server.config.RabbitConfig;
import com.jupin.server.mapper.OrderMapper;
import com.jupin.server.mapper.PoolMapper;
import com.jupin.server.mapper.PoolMemberMapper;
import com.jupin.server.service.MessageService;
import com.jupin.server.service.CreditService;
import com.jupin.server.service.impl.OrderStateMachine;
import com.jupin.server.service.impl.PoolStateMachine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class TimeoutConsumer {

    private final OrderMapper orderMapper;
    private final PoolMapper poolMapper;
    private final PoolMemberMapper memberMapper;
    private final OrderStateMachine orderStateMachine;
    private final PoolStateMachine poolStateMachine;
    private final CreditService creditService;
    private final StringRedisTemplate stringRedis;
    private final MessageService messageService;
    private final SimpMessagingTemplate messagingTemplate;

    @RabbitListener(queues = RabbitConfig.QUEUE_TIMEOUT)
    @Transactional
    public void handle(String body) {
        // 消费统一超时队列中的 JSON 消息。
        // 这些消息已经在延迟队列中过了 TTL，所以到这里时代表“需要检查是否超时”。
        TimeoutMessage message = JSONUtil.toBean(body, TimeoutMessage.class);
        // 根据消息类型分发到不同处理逻辑。
        // 注意这里不是直接判定业务一定超时，具体方法里还会重新查数据库状态。
        if (isOrderPaymentTimeout(message.getType())) {
            handleOrderPaymentTimeout(message);
        } else if (TimeoutMessage.POOL_START.equals(message.getType())) {
            handlePoolStartTimeout(message);
        } else if (TimeoutMessage.COMPLETED_CONFIRM.equals(message.getType())) {
            handleCompletedConfirmTimeout(message);
        } else if (TimeoutMessage.FINISHED_CONFIRM.equals(message.getType())) {
            handleFinishedConfirmTimeout(message);
        }
    }

    private boolean isOrderPaymentTimeout(String type) {
        // 兼容旧的 ORDER_PAYMENT 类型，同时支持押金和尾款两种更细的支付超时类型。
        return TimeoutMessage.ORDER_PAYMENT.equals(type)
                || TimeoutMessage.ORDER_DEPOSIT_PAYMENT.equals(type)
                || TimeoutMessage.ORDER_FINAL_PAYMENT.equals(type);
    }

    private void handleOrderPaymentTimeout(TimeoutMessage message) {
        // 超时消息到达后先回查订单最新状态。
        // 如果订单不存在，或者已经支付、退款、逾期，就不再处理。
        Order order = orderMapper.selectById(message.getOrderId());
        if (order == null || order.getStatus() != OrderStatus.PENDING) return;

        // 用订单状态机把 PENDING 改成 OVERDUE。
        // markOverdue 内部带 where status = PENDING，防止支付成功和超时消费并发时误改状态。
        if (!orderStateMachine.markOverdue(order)) return;

        if (order.getType() != null && order.getType() == 0) {
            // 押金逾期：释放 join 阶段占用的名额，并把成员状态从待支付改成已退出。
            int rows = memberMapper.update(null, new UpdateWrapper<PoolMember>()
                    .set(DbFieldConstant.STATUS, MemberStatus.LEFT)
                    .set("leave_time", LocalDateTime.now())
                    .eq(DbFieldConstant.POOL_ID, order.getPoolId())
                    .eq(DbFieldConstant.USER_ID, order.getUserId())
                    .eq(DbFieldConstant.STATUS, MemberStatus.PENDING_PAYMENT));
            if (rows > 0) {
                poolMapper.update(null, new UpdateWrapper<CarPool>()
                        .setSql("current_members = GREATEST(current_members - 1, 0)")
                        .eq(DbFieldConstant.ID, order.getPoolId())
                        .in(DbFieldConstant.STATUS, PoolStatus.OPEN, PoolStatus.FULL)
                        .apply("current_members > 0"));
                poolMapper.update(null, new UpdateWrapper<CarPool>()
                        .set(DbFieldConstant.STATUS, PoolStatus.OPEN)
                        .eq(DbFieldConstant.ID, order.getPoolId())
                        .eq(DbFieldConstant.STATUS, PoolStatus.FULL));
            }
            // 成员状态变化后清理详情缓存，避免页面继续展示用户待支付占位。
            evictPoolDetail(order.getPoolId());
            // 给用户发送站内消息，说明押金超时导致退出。
            messageService.sendMessage("timeout_deposit_" + order.getId() + "_" + order.getUserId(),
                    order.getUserId(), 0, "押金订单逾期",
                    "押金订单逾期未支付，您已退出拼车", order.getPoolId());
            // 事务提交后再推送 WebSocket 事件，避免前端收到事件时数据库事务还没提交。
            publishPoolEventAfterCommit(order.getPoolId(), "DEPOSIT_PAYMENT_OVERDUE");
        } else {
            // 尾款逾期：组局已经成团，不释放座位，而是扣减用户信用分。
            creditService.deduct(order.getUserId(), 10, "尾款逾期未支付");
            // 给用户发送尾款逾期通知。
            messageService.sendMessage("timeout_final_" + order.getId() + "_" + order.getUserId(),
                    order.getUserId(), 0, "尾款逾期",
                    "尾款逾期未支付，信用分已扣减 10 分", order.getPoolId());
            // 事务提交后广播尾款逾期事件。
            publishPoolEventAfterCommit(order.getPoolId(), "FINAL_PAYMENT_OVERDUE");
        }
    }

    private void handlePoolStartTimeout(TimeoutMessage message) {
        // 到达开局时间后回查组局最新状态。
        // 只有仍处于开放中的组局才可能被自动取消。
        CarPool pool = poolMapper.selectById(message.getPoolId());
        if (pool == null || pool.getStatus() != PoolStatus.OPEN) return;
        // 如果当前时间还没到开始时间，说明消息提前到达，直接忽略。
        if (pool.getStartTime() != null && LocalDateTime.now().isBefore(pool.getStartTime())) return;
        // 已经有人加入的组局不能因为开始时间到达而自动取消。
        if (pool.getCurrentMembers() != null && pool.getCurrentMembers() > 0) return;

        // 数据库层再次兜底：只有 OPEN 且 current_members = 0 的组局才能取消。
        int rows = poolMapper.update(null, new UpdateWrapper<CarPool>()
                .set(DbFieldConstant.STATUS, PoolStatus.CANCELLED)
                .eq(DbFieldConstant.ID, pool.getId())
                .eq(DbFieldConstant.STATUS, PoolStatus.OPEN)
                .eq("current_members", 0));
        // 更新 0 行说明状态或人数已变化，直接结束。
        if (rows == 0) return;
        // 状态变化后清理详情缓存。
        evictPoolDetail(pool.getId());
        // 通知发起人组局已自动取消。
        messageService.sendMessage("timeout_pool_start_" + pool.getId() + "_" + pool.getOwnerId(),
                pool.getOwnerId(), 0, "拼车自动取消",
                "拼车因无人加入已自动取消", pool.getId());
        // 事务提交后通知前端刷新组局状态。
        publishPoolEventAfterCommit(pool.getId(), "POOL_START_TIMEOUT_CANCELLED");
    }

    private void handleCompletedConfirmTimeout(TimeoutMessage message) {
        // 成团确认超时后回查组局。
        // 只有满员状态才需要判断是否可以兜底成团。
        CarPool pool = poolMapper.selectById(message.getPoolId());
        if (pool == null || pool.getStatus() != PoolStatus.FULL) return;

        // 统计正式成员总数。
        long total = memberMapper.selectCount(new QueryWrapper<PoolMember>()
                .eq(DbFieldConstant.POOL_ID, pool.getId())
                .eq(DbFieldConstant.STATUS, MemberStatus.JOINED));
        // 统计已经确认成团的人数。
        long confirmed = memberMapper.selectCount(new QueryWrapper<PoolMember>()
                .eq(DbFieldConstant.POOL_ID, pool.getId())
                .eq(DbFieldConstant.STATUS, MemberStatus.JOINED)
                .eq("completed_confirmed", ConfirmStatus.CONFIRMED));
        // 如果所有正式成员都确认了，超时兜底把组局推进到已成团。
        if (total > 0 && confirmed == total) {
            poolStateMachine.toCompleted(pool.getId());
            evictPoolDetail(pool.getId());
            notifyJoinedMembers(pool.getId(), 2, "拼车已成功",
                    "成团确认超时兜底已完成，拼车已成功", "timeout_completed_confirm_");
            publishPoolEventAfterCommit(pool.getId(), "COMPLETED_CONFIRM_TIMEOUT_COMPLETED");
        }
    }

    private void handleFinishedConfirmTimeout(TimeoutMessage message) {
        // 结束确认超时后回查组局。
        // 只有已成团的组局才可能被推进到已结束。
        CarPool pool = poolMapper.selectById(message.getPoolId());
        if (pool == null || pool.getStatus() != PoolStatus.COMPLETED) return;

        // 统计正式成员总数。
        long total = memberMapper.selectCount(new QueryWrapper<PoolMember>()
                .eq(DbFieldConstant.POOL_ID, pool.getId())
                .eq(DbFieldConstant.STATUS, MemberStatus.JOINED));
        // 统计已经确认结束的人数。
        long confirmed = memberMapper.selectCount(new QueryWrapper<PoolMember>()
                .eq(DbFieldConstant.POOL_ID, pool.getId())
                .eq(DbFieldConstant.STATUS, MemberStatus.JOINED)
                .eq("finished_confirmed", ConfirmStatus.CONFIRMED));
        // 只有线下结束时间已经过去，才允许走结束兜底。
        boolean endTimeElapsed = pool.getEndTime() != null && LocalDateTime.now().isAfter(pool.getEndTime());
        // 超过半数确认且已到结束时间时，推进为已结束。
        if (total > 0 && endTimeElapsed && confirmed > total / 2) {
            poolStateMachine.toFinished(pool.getId());
            evictPoolDetail(pool.getId());
            notifyJoinedMembers(pool.getId(), 4, "剧本杀已完成",
                    "结束确认超时兜底已完成，剧本杀已完成，请评价", "timeout_finished_confirm_");
            publishPoolEventAfterCommit(pool.getId(), "FINISHED_CONFIRM_TIMEOUT_FINISHED");
        }
    }

    private void evictPoolDetail(Long poolId) {
        // 删除组局详情缓存。
        // 超时处理改变订单、成员或组局状态后，详情页必须重新加载最新数据。
        stringRedis.delete(RedisKeyConstant.POOL_DETAIL_PREFIX + poolId);
    }

    private void notifyJoinedMembers(Long poolId, int type, String title, String content, String keyPrefix) {
        // 查询当前组局所有正式成员。
        List<PoolMember> members = memberMapper.selectList(new QueryWrapper<PoolMember>()
                .eq(DbFieldConstant.POOL_ID, poolId)
                .eq(DbFieldConstant.STATUS, MemberStatus.JOINED));
        for (PoolMember member : members) {
            // 给每个正式成员发送一条站内消息。
            // keyPrefix + poolId + userId 用作业务幂等键，避免同一通知重复插入。
            messageService.sendMessage(keyPrefix + poolId + "_" + member.getUserId(),
                    member.getUserId(), type, title, content, poolId);
        }
    }

    private void publishPoolEventAfterCommit(Long poolId, String event) {
        // 如果当前方法在事务中执行，就注册 afterCommit 回调。
        // 这样 WebSocket 推送一定发生在数据库提交之后，前端刷新时能读到新状态。
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publishPoolEvent(poolId, event);
                }
            });
            return;
        }
        // 没有事务时直接推送事件。
        publishPoolEvent(poolId, event);
    }

    private void publishPoolEvent(Long poolId, String event) {
        try {
            // 向组局维度的 STOMP topic 推送事件。
            // 订阅 /topic/pool/{poolId} 的前端页面会收到状态变化通知。
            messagingTemplate.convertAndSend("/topic/pool/" + poolId, Map.of(
                    "event", event,
                    "poolId", poolId,
                    "time", LocalDateTime.now().toString()
            ));
        } catch (Exception e) {
            // WebSocket 推送失败不回滚数据库事务，只记录日志。
            // 因为超时状态处理已经完成，前端后续刷新仍能拿到正确数据。
            log.warn("publish timeout pool event failed, poolId={}, event={}", poolId, event, e);
        }
    }
}
