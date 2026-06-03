package com.jupin.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.jupin.common.constant.DbFieldConstant;
import com.jupin.common.constant.ErrorConstant;
import com.jupin.common.constant.OrderStatus;
import com.jupin.common.exception.BaseException;
import com.jupin.pojo.entity.Order;
import com.jupin.server.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class OrderStateMachine {

    private final OrderMapper orderMapper;

    public boolean paySuccess(Order order, String payRequestNo, String callbackRequestNo, String channelTxnId) {
        int rows = orderMapper.update(null, new UpdateWrapper<Order>()
                .set(DbFieldConstant.STATUS, OrderStatus.PAID)
                .set("pay_time", LocalDateTime.now())
                .set("pay_request_no", payRequestNo)
                .set("callback_request_no", callbackRequestNo)
                .set("channel_txn_id", channelTxnId)
                .eq(DbFieldConstant.ID, order.getId())
                .eq(DbFieldConstant.STATUS, OrderStatus.PENDING));
        if (rows > 0) return true;

        Order latest = orderMapper.selectById(order.getId());
        return latest != null && latest.getStatus() == OrderStatus.PAID;
    }

    public boolean markOverdue(Order order) {
        int rows = orderMapper.update(null, new UpdateWrapper<Order>()
                .set(DbFieldConstant.STATUS, OrderStatus.OVERDUE)
                .eq(DbFieldConstant.ID, order.getId())
                .eq(DbFieldConstant.STATUS, OrderStatus.PENDING));
        return rows > 0;
    }

    public void refund(Order order) {
        int rows = orderMapper.update(null, new UpdateWrapper<Order>()
                .set(DbFieldConstant.STATUS, OrderStatus.REFUNDED)
                .set(DbFieldConstant.REFUND_TIME, LocalDateTime.now())
                .eq(DbFieldConstant.ID, order.getId())
                .eq(DbFieldConstant.STATUS, OrderStatus.PAID));
        if (rows == 0) throw new BaseException(ErrorConstant.ONLY_PAID_ORDER_CAN_REFUND);
    }

    public void release(Order order) {
        int rows = orderMapper.update(null, new UpdateWrapper<Order>()
                .set("release_status", 1)
                .set("release_time", LocalDateTime.now())
                .eq(DbFieldConstant.ID, order.getId())
                .eq(DbFieldConstant.STATUS, OrderStatus.PAID)
                .eq("release_status", 0));
        if (rows == 0) {
            Order latest = orderMapper.selectById(order.getId());
            if (latest != null && latest.getReleaseStatus() != null && latest.getReleaseStatus() == 1) return;
            throw new BaseException(ErrorConstant.ONLY_PAID_ORDER_CAN_RELEASE);
        }
    }
}
