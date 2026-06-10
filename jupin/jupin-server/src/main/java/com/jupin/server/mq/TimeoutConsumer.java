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
        TimeoutMessage message = JSONUtil.toBean(body, TimeoutMessage.class);
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
        return TimeoutMessage.ORDER_PAYMENT.equals(type)
                || TimeoutMessage.ORDER_DEPOSIT_PAYMENT.equals(type)
                || TimeoutMessage.ORDER_FINAL_PAYMENT.equals(type);
    }

    private void handleOrderPaymentTimeout(TimeoutMessage message) {
        Order order = orderMapper.selectById(message.getOrderId());
        if (order == null || order.getStatus() != OrderStatus.PENDING) return;

        if (!orderStateMachine.markOverdue(order)) return;

        if (order.getType() != null && order.getType() == 0) {
            int memberUpdated = memberMapper.update(null, new UpdateWrapper<PoolMember>()
                    .set(DbFieldConstant.STATUS, MemberStatus.LEFT)
                    .set("leave_time", LocalDateTime.now())
                    .eq(DbFieldConstant.POOL_ID, order.getPoolId())
                    .eq(DbFieldConstant.USER_ID, order.getUserId())
                    .eq(DbFieldConstant.STATUS, MemberStatus.PENDING_PAYMENT));
            if (memberUpdated > 0) {
                poolMapper.update(null, new UpdateWrapper<CarPool>()
                        .setSql("current_members = GREATEST(current_members - 1, 0)")
                        .eq(DbFieldConstant.ID, order.getPoolId())
                        .in(DbFieldConstant.STATUS, PoolStatus.OPEN, PoolStatus.FULL)
                        .apply("current_members > 0"));
                poolStateMachine.rollbackToOpen(order.getPoolId());
            }
            evictPoolDetail(order.getPoolId());
            messageService.sendMessage("timeout_deposit_" + order.getId() + "_" + order.getUserId(),
                    order.getUserId(), 0, "押金订单逾期",
                    "押金订单逾期未支付，您已退出拼车", order.getPoolId());
            publishPoolEventAfterCommit(order.getPoolId(), "DEPOSIT_PAYMENT_OVERDUE");
        } else {
            creditService.deduct(order.getUserId(), 10, "尾款逾期未支付");
            messageService.sendMessage("timeout_final_" + order.getId() + "_" + order.getUserId(),
                    order.getUserId(), 0, "尾款逾期",
                    "尾款逾期未支付，信用分已扣减 10 分", order.getPoolId());
            publishPoolEventAfterCommit(order.getPoolId(), "FINAL_PAYMENT_OVERDUE");
        }
    }

    private void handlePoolStartTimeout(TimeoutMessage message) {
        CarPool pool = poolMapper.selectById(message.getPoolId());
        if (pool == null || pool.getStatus() != PoolStatus.OPEN) return;
        if (pool.getStartTime() != null && LocalDateTime.now().isBefore(pool.getStartTime())) return;
        if (pool.getCurrentMembers() != null && pool.getCurrentMembers() > 0) return;

        int rows = poolMapper.update(null, new UpdateWrapper<CarPool>()
                .set(DbFieldConstant.STATUS, PoolStatus.CANCELLED)
                .eq(DbFieldConstant.ID, pool.getId())
                .eq(DbFieldConstant.STATUS, PoolStatus.OPEN)
                .eq("current_members", 0));
        if (rows == 0) return;
        evictPoolDetail(pool.getId());
        messageService.sendMessage("timeout_pool_start_" + pool.getId() + "_" + pool.getOwnerId(),
                pool.getOwnerId(), 0, "拼车自动取消",
                "拼车因无人加入已自动取消", pool.getId());
        publishPoolEventAfterCommit(pool.getId(), "POOL_START_TIMEOUT_CANCELLED");
    }

    private void handleCompletedConfirmTimeout(TimeoutMessage message) {
        CarPool pool = poolMapper.selectById(message.getPoolId());
        if (pool == null || pool.getStatus() != PoolStatus.FULL) return;

        long total = memberMapper.selectCount(new QueryWrapper<PoolMember>()
                .eq(DbFieldConstant.POOL_ID, pool.getId())
                .eq(DbFieldConstant.STATUS, MemberStatus.JOINED));
        long confirmed = memberMapper.selectCount(new QueryWrapper<PoolMember>()
                .eq(DbFieldConstant.POOL_ID, pool.getId())
                .eq(DbFieldConstant.STATUS, MemberStatus.JOINED)
                .eq("completed_confirmed", ConfirmStatus.CONFIRMED));
        if (total > 0 && confirmed == total) {
            poolStateMachine.toCompleted(pool.getId());
            evictPoolDetail(pool.getId());
            notifyJoinedMembers(pool.getId(), 2, "拼车已成功",
                    "成团确认超时兜底已完成，拼车已成功", "timeout_completed_confirm_");
            publishPoolEventAfterCommit(pool.getId(), "COMPLETED_CONFIRM_TIMEOUT_COMPLETED");
        }
    }

    private void handleFinishedConfirmTimeout(TimeoutMessage message) {
        CarPool pool = poolMapper.selectById(message.getPoolId());
        if (pool == null || pool.getStatus() != PoolStatus.COMPLETED) return;

        long total = memberMapper.selectCount(new QueryWrapper<PoolMember>()
                .eq(DbFieldConstant.POOL_ID, pool.getId())
                .eq(DbFieldConstant.STATUS, MemberStatus.JOINED));
        long confirmed = memberMapper.selectCount(new QueryWrapper<PoolMember>()
                .eq(DbFieldConstant.POOL_ID, pool.getId())
                .eq(DbFieldConstant.STATUS, MemberStatus.JOINED)
                .eq("finished_confirmed", ConfirmStatus.CONFIRMED));
        boolean endTimeElapsed = pool.getEndTime() != null && LocalDateTime.now().isAfter(pool.getEndTime());
        if (total > 0 && endTimeElapsed && confirmed > total / 2) {
            poolStateMachine.toFinished(pool.getId());
            evictPoolDetail(pool.getId());
            notifyJoinedMembers(pool.getId(), 4, "剧本杀已完成",
                    "结束确认超时兜底已完成，剧本杀已完成，请评价", "timeout_finished_confirm_");
            publishPoolEventAfterCommit(pool.getId(), "FINISHED_CONFIRM_TIMEOUT_FINISHED");
        }
    }

    private void evictPoolDetail(Long poolId) {
        stringRedis.delete(RedisKeyConstant.POOL_DETAIL_PREFIX + poolId);
    }

    private void notifyJoinedMembers(Long poolId, int type, String title, String content, String keyPrefix) {
        List<PoolMember> members = memberMapper.selectList(new QueryWrapper<PoolMember>()
                .eq(DbFieldConstant.POOL_ID, poolId)
                .eq(DbFieldConstant.STATUS, MemberStatus.JOINED));
        for (PoolMember member : members) {
            messageService.sendMessage(keyPrefix + poolId + "_" + member.getUserId(),
                    member.getUserId(), type, title, content, poolId);
        }
    }

    private void publishPoolEventAfterCommit(Long poolId, String event) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publishPoolEvent(poolId, event);
                }
            });
            return;
        }
        publishPoolEvent(poolId, event);
    }

    private void publishPoolEvent(Long poolId, String event) {
        try {
            messagingTemplate.convertAndSend("/topic/pool/" + poolId, Map.of(
                    "event", event,
                    "poolId", poolId,
                    "time", LocalDateTime.now().toString()
            ));
        } catch (Exception e) {
            log.warn("publish timeout pool event failed, poolId={}, event={}", poolId, event, e);
        }
    }
}
