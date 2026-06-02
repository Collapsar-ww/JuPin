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
import com.jupin.server.service.CreditService;
import com.jupin.server.service.impl.PoolStateMachine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class TimeoutConsumer {

    private final OrderMapper orderMapper;
    private final PoolMapper poolMapper;
    private final PoolMemberMapper memberMapper;
    private final PoolStateMachine poolStateMachine;
    private final CreditService creditService;
    private final StringRedisTemplate stringRedis;

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

        int rows = orderMapper.update(null, new UpdateWrapper<Order>()
                .set(DbFieldConstant.STATUS, OrderStatus.OVERDUE)
                .eq(DbFieldConstant.ID, order.getId())
                .eq(DbFieldConstant.STATUS, OrderStatus.PENDING));
        if (rows == 0) return;

        if (order.getType() != null && order.getType() == 0) {
            memberMapper.update(null, new UpdateWrapper<PoolMember>()
                    .set(DbFieldConstant.STATUS, MemberStatus.LEFT)
                    .set("leave_time", LocalDateTime.now())
                    .eq(DbFieldConstant.POOL_ID, order.getPoolId())
                    .eq(DbFieldConstant.USER_ID, order.getUserId())
                    .eq(DbFieldConstant.STATUS, MemberStatus.PENDING_PAYMENT));
            evictPoolDetail(order.getPoolId());
        } else {
            creditService.deduct(order.getUserId(), 10, "尾款逾期未支付");
        }
    }

    private void handlePoolStartTimeout(TimeoutMessage message) {
        CarPool pool = poolMapper.selectById(message.getPoolId());
        if (pool == null || pool.getStatus() != PoolStatus.OPEN) return;
        if (pool.getStartTime() != null && LocalDateTime.now().isBefore(pool.getStartTime())) return;
        if (pool.getCurrentMembers() != null && pool.getCurrentMembers() > 0) return;

        poolMapper.update(null, new UpdateWrapper<CarPool>()
                .set(DbFieldConstant.STATUS, PoolStatus.CANCELLED)
                .eq(DbFieldConstant.ID, pool.getId())
                .eq(DbFieldConstant.STATUS, PoolStatus.OPEN)
                .eq("current_members", 0));
        evictPoolDetail(pool.getId());
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
        }
    }

    private void evictPoolDetail(Long poolId) {
        stringRedis.delete(RedisKeyConstant.POOL_DETAIL_PREFIX + poolId);
    }
}
