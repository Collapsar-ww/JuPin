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
        // 支付成功只允许从 PENDING 推进到 PAID。
        // 这里把“状态判断”和“状态更新”写在同一条 SQL 里，避免先查后改带来的并发窗口。
        int rows = orderMapper.update(null, new UpdateWrapper<Order>()
                // 将订单状态改为已支付。
                .set(DbFieldConstant.STATUS, OrderStatus.PAID)
                // 记录支付成功时间，后续可用于订单展示和资金释放判断。
                .set("pay_time", LocalDateTime.now())
                // 保存支付请求号，用于排查一次支付请求对应的业务订单。
                .set("pay_request_no", payRequestNo)
                // 保存回调请求号，用于识别支付渠道重复通知。
                .set("callback_request_no", callbackRequestNo)
                // 保存渠道交易流水号，用于和第三方支付流水对账。
                .set("channel_txn_id", channelTxnId)
                // 精确定位要更新的订单。
                .eq(DbFieldConstant.ID, order.getId())
                // 乐观条件：只有待支付订单才能变成已支付。
                // 如果重复回调或超时任务已经改过状态，这条 SQL 会更新 0 行。
                .eq(DbFieldConstant.STATUS, OrderStatus.PENDING));
        // 更新行数大于 0，说明本次请求成功完成了状态推进。
        if (rows > 0) return true;

        // 如果没有更新成功，回查最新订单状态。
        // 可能是另一个重复回调已经先一步把订单改成 PAID，这种情况也视为幂等成功。
        Order latest = orderMapper.selectById(order.getId());
        return latest != null && latest.getStatus() == OrderStatus.PAID;
    }

    public boolean markOverdue(Order order) {
        // 超时只允许从 PENDING 推进到 OVERDUE。
        // 如果用户已经支付成功，这里的 where status = PENDING 会阻止误改为逾期。
        int rows = orderMapper.update(null, new UpdateWrapper<Order>()
                .set(DbFieldConstant.STATUS, OrderStatus.OVERDUE)
                .eq(DbFieldConstant.ID, order.getId())
                .eq(DbFieldConstant.STATUS, OrderStatus.PENDING));
        // 返回是否真的完成了逾期状态变更，消费者据此决定是否继续释放座位或扣信用分。
        return rows > 0;
    }

    public void refund(Order order) {
        // 退款只允许已支付订单执行。
        // 待支付、逾期、已退款订单都不能再次退款。
        int rows = orderMapper.update(null, new UpdateWrapper<Order>()
                .set(DbFieldConstant.STATUS, OrderStatus.REFUNDED)
                .set(DbFieldConstant.REFUND_TIME, LocalDateTime.now())
                .eq(DbFieldConstant.ID, order.getId())
                .eq(DbFieldConstant.STATUS, OrderStatus.PAID));
        // 更新失败说明订单不是已支付状态，直接抛业务异常。
        if (rows == 0) throw new BaseException(ErrorConstant.ONLY_PAID_ORDER_CAN_REFUND);
    }

    public void release(Order order) {
        // 资金释放不改变订单支付状态，而是改变 release_status。
        // 只有已支付且尚未释放的订单，才能把资金释放给 DM 或店家。
        int rows = orderMapper.update(null, new UpdateWrapper<Order>()
                .set("release_status", 1)
                .set("release_time", LocalDateTime.now())
                .eq(DbFieldConstant.ID, order.getId())
                .eq(DbFieldConstant.STATUS, OrderStatus.PAID)
                .eq("release_status", 0));
        if (rows == 0) {
            // 如果更新失败，回查一次最新 release_status。
            // 已经释放过时直接返回，保证重复释放请求具备幂等性。
            Order latest = orderMapper.selectById(order.getId());
            if (latest != null && latest.getReleaseStatus() != null && latest.getReleaseStatus() == 1) return;
            // 既不是已释放，也不满足释放条件，说明当前订单不能释放资金。
            throw new BaseException(ErrorConstant.ONLY_PAID_ORDER_CAN_RELEASE);
        }
    }
}
