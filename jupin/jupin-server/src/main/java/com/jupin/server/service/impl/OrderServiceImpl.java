package com.jupin.server.service.impl;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jupin.common.constant.ConfirmStatus;
import com.jupin.common.constant.DbFieldConstant;
import com.jupin.common.constant.ErrorConstant;
import com.jupin.common.constant.MemberStatus;
import com.jupin.common.constant.OrderStatus;
import com.jupin.common.constant.PaymentEventStatus;
import com.jupin.common.constant.PoolStatus;
import com.jupin.common.constant.RedisKeyConstant;
import com.jupin.common.exception.BaseException;
import com.jupin.pojo.dto.MockPayCallbackRequest;
import com.jupin.pojo.entity.CarPool;
import com.jupin.pojo.entity.Order;
import com.jupin.pojo.entity.PaymentEvent;
import com.jupin.pojo.entity.PoolMember;
import com.jupin.server.mapper.OrderMapper;
import com.jupin.server.mapper.PaymentEventMapper;
import com.jupin.server.mapper.PoolMemberMapper;
import com.jupin.server.mapper.PoolMapper;
import com.jupin.server.mq.TimeoutMessage;
import com.jupin.server.mq.TimeoutProducer;
import com.jupin.server.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderMapper orderMapper;
    private final PaymentEventMapper paymentEventMapper;
    private final PoolMapper poolMapper;
    private final PoolMemberMapper memberMapper;
    private final OrderStateMachine orderStateMachine;
    private final PoolStateMachine stateMachine;
    private final StringRedisTemplate stringRedis;
    private final TimeoutProducer timeoutProducer;
    private final SimpMessagingTemplate messagingTemplate;
    private final Snowflake snowflake = IdUtil.getSnowflake(1, 1);
    private static final long DEPOSIT_PAY_TIMEOUT_MINUTES = 15;
    private static final long FINAL_PAY_TIMEOUT_HOURS = 24;
    private static final String PAY_STATUS_SUCCESS = "SUCCESS";
    private static final String EVENT_TYPE_PAY_CALLBACK = "PAY_CALLBACK";

    @Override
    @Transactional
    public Order create(Long userId, Long poolId, Integer type, String idempotentKey) {
        CarPool pool = poolMapper.selectById(poolId);
        if (pool == null) throw new BaseException(ErrorConstant.POOL_NOT_FOUND);
        if (type == null || (type != 0 && type != 1)) {
            throw new BaseException(ErrorConstant.INVALID_ORDER_TYPE);
        }
        // 先把前端传入的幂等 Key 标准化。
        // 如果前端没有传，就用 userId + poolId + type 生成一个稳定业务 Key。
        String normalizedIdempotentKey = resolveIdempotentKey(userId, poolId, type, idempotentKey);
        // 第一层幂等：先按幂等 Key + 用户查询是否已有订单。
        // 同一个用户重复点击“创建订单”时，直接返回原订单，不再插入新记录。
        Order idempotentOrder = orderMapper.selectOne(new QueryWrapper<Order>()
                .eq("idempotent_key", normalizedIdempotentKey)
                .eq(DbFieldConstant.USER_ID, userId)
                .last("LIMIT 1"));
        if (idempotentOrder != null) {
            return idempotentOrder;
        }
        // 防止不同用户复用同一个幂等 Key。
        // 如果这个 Key 已经被其他用户使用，说明请求参数异常，直接拒绝。
        Long conflictCount = orderMapper.selectCount(new QueryWrapper<Order>()
                .eq("idempotent_key", normalizedIdempotentKey)
                .ne(DbFieldConstant.USER_ID, userId));
        if (conflictCount > 0) throw new BaseException(ErrorConstant.IDEMPOTENT_KEY_CONFLICT);

        // 创建订单前先确认用户确实是该组局成员。
        // 非成员不能绕过加入流程直接创建押金或尾款订单。
        PoolMember member = memberMapper.selectOne(new QueryWrapper<PoolMember>()
                .eq(DbFieldConstant.POOL_ID, poolId)
                .eq(DbFieldConstant.USER_ID, userId));
        if (member == null) {
            throw new BaseException(ErrorConstant.NOT_POOL_MEMBER_CANNOT_CREATE_ORDER);
        }
        if (type == 0) {
            // 押金订单只允许待支付成员创建。
            // 已正式加入、已退出、待审核等状态都不能重复创建押金订单。
            if (member.getStatus() != MemberStatus.PENDING_PAYMENT) {
                throw new BaseException(ErrorConstant.MEMBER_STATUS_CANNOT_CREATE_DEPOSIT_ORDER);
            }
            // 押金支付发生在组局开放或满员阶段，取消、成团、结束后不能再补押金。
            if (pool.getStatus() != PoolStatus.OPEN && pool.getStatus() != PoolStatus.FULL) {
                throw new BaseException(ErrorConstant.CURRENT_POOL_STATUS_CANNOT_PAY_DEPOSIT);
            }
        } else {
            // 尾款订单只允许正式成员创建，防止未成团用户被要求支付尾款。
            if (member.getStatus() != MemberStatus.JOINED) {
                throw new BaseException(ErrorConstant.NOT_FORMAL_MEMBER_CANNOT_CREATE_FEE_ORDER);
            }
            // 尾款在成团后产生，所以组局必须处于已成团状态。
            if (pool.getStatus() != PoolStatus.COMPLETED) {
                throw new BaseException(ErrorConstant.POOL_NOT_COMPLETED_CANNOT_CREATE_FEE_ORDER);
            }
        }

        // 第二层防重：同一用户、同一组局、同一类型，只允许存在待支付或已支付订单。
        // 这样即使前端换了幂等 Key，也不会绕过业务规则创建多笔有效订单。
        Long count = orderMapper.selectCount(new QueryWrapper<Order>()
                .eq(DbFieldConstant.USER_ID, userId).eq(DbFieldConstant.POOL_ID, poolId)
                .eq(DbFieldConstant.TYPE, type).in(DbFieldConstant.STATUS, OrderStatus.PENDING, OrderStatus.PAID));
        if (count > 0) throw new BaseException(ErrorConstant.ORDER_ALREADY_CREATED);

        // 构造订单对象。
        // 收款方在创建订单时固化：玩家局付款给 DM，店家局付款给店家。
        // 这样后续 DM 或店家信息变化时，不会影响已经生成的订单资金归属。
        Order order = Order.builder()
                .orderNo(snowflake.nextIdStr())
                .idempotentKey(normalizedIdempotentKey)
                .userId(userId)
                .poolId(poolId)
                .type(type)
                .amount(resolveAmount(pool, type))
                .status(OrderStatus.PENDING)
                .payeeId(pool.getType() == 1 ? pool.getShopId() : pool.getDmId())
                .payeeType(pool.getType() == 1 ? 1 : 0)
                .releaseStatus(0)
                .expireTime(resolveExpireTime(type))
                .build();
        try {
            // 插入订单。
            // 数据库唯一索引是最后兜底，能挡住两个并发请求同时通过前置查询的情况。
            orderMapper.insert(order);
        } catch (DuplicateKeyException e) {
            // 第三层幂等：如果并发插入触发唯一索引冲突，就回查已有订单并返回。
            // 这样重复请求不会变成 500，而是得到和第一次请求一致的订单结果。
            Order existing = orderMapper.selectOne(new QueryWrapper<Order>()
                    .eq("idempotent_key", normalizedIdempotentKey)
                    .eq(DbFieldConstant.USER_ID, userId)
                    .last("LIMIT 1"));
            if (existing != null) return existing;
            throw e;
        }
        // 订单创建成功后发送超时消息。
        // 这里不在主链路里 sleep 或轮询，后续由 RabbitMQ 死信队列到期后触发消费者处理。
        // 消费者真正修改状态前还会重新检查订单状态，避免已支付订单被误标记逾期。
        timeoutProducer.send(new TimeoutMessage(resolvePaymentTimeoutType(type), order.getId(), poolId, userId),
                type == 0 ? TimeUnit.MINUTES.toMillis(DEPOSIT_PAY_TIMEOUT_MINUTES) : TimeUnit.HOURS.toMillis(FINAL_PAY_TIMEOUT_HOURS));
        return order;
    }

    private String resolvePaymentTimeoutType(Integer type) {
        return type != null && type == 0
                ? TimeoutMessage.ORDER_DEPOSIT_PAYMENT
                : TimeoutMessage.ORDER_FINAL_PAYMENT;
    }

    @Override
    @Transactional
    public void pay(Long userId, String orderNo) {
        Order order = orderMapper.selectOne(new QueryWrapper<Order>().eq(DbFieldConstant.ORDER_NO, orderNo));
        if (order == null) throw new BaseException(ErrorConstant.ORDER_NOT_FOUND);
        if (!order.getUserId().equals(userId)) throw new BaseException(ErrorConstant.ORDER_NOT_OWNED);
        if (order.getStatus() == OrderStatus.PAID) return;
        if (order.getStatus() != OrderStatus.PENDING) throw new BaseException(ErrorConstant.ORDER_STATUS_INVALID);

        String payRequestNo = StringUtils.hasText(order.getPayRequestNo())
                ? order.getPayRequestNo()
                : "PAY-" + snowflake.nextIdStr();
        MockPayCallbackRequest callback = new MockPayCallbackRequest();
        callback.setOrderNo(orderNo);
        callback.setPayRequestNo(payRequestNo);
        callback.setCallbackRequestNo("CALLBACK-" + payRequestNo);
        callback.setChannelTxnId("MOCK-" + payRequestNo);
        callback.setPayStatus(PAY_STATUS_SUCCESS);
        mockPayCallback(userId, callback);
    }

    @Override
    @Transactional
    public Order mockPayCallback(Long userId, MockPayCallbackRequest request) {
        if (!StringUtils.hasText(request.getPayRequestNo())) {
            request.setPayRequestNo("PAY-" + request.getCallbackRequestNo());
        }
        if (!StringUtils.hasText(request.getPayStatus())) {
            request.setPayStatus(PAY_STATUS_SUCCESS);
        }
        Order order = orderMapper.selectOne(new QueryWrapper<Order>().eq(DbFieldConstant.ORDER_NO, request.getOrderNo()));
        if (order == null) throw new BaseException(ErrorConstant.ORDER_NOT_FOUND);
        if (userId != null && !order.getUserId().equals(userId)) throw new BaseException(ErrorConstant.ORDER_NOT_OWNED);

        // 支付回调幂等单独记录在 payment_event 表。
        // 这样既能保护订单状态，又能保留第三方渠道重复通知的原始事件。
        String eventKey = "callback:" + request.getChannelTxnId();
        // 先插入回调事件。
        // 如果 eventKey、渠道流水号或回调请求号重复，insertPaymentEvent 会回查已有事件。
        PaymentEvent event = insertPaymentEvent(eventKey, request);
        // 如果事件已经处理过，说明这是重复回调。
        // 直接回查订单最新状态返回，不再重复推进订单状态机。
        if (event.getStatus() != null && event.getStatus() != PaymentEventStatus.PROCESSING) {
            return orderMapper.selectOne(new QueryWrapper<Order>().eq(DbFieldConstant.ORDER_NO, request.getOrderNo()));
        }

        // 当前模拟支付只处理成功回调。
        // 非 SUCCESS 的通知标记为忽略，不改变订单状态。
        if (!PAY_STATUS_SUCCESS.equalsIgnoreCase(request.getPayStatus())) {
            updatePaymentEventStatus(event.getId(), PaymentEventStatus.IGNORED);
            return order;
        }
        // 如果订单已经支付成功，说明之前的回调已经完成了状态推进。
        // 本次事件标记成功后直接返回，保证重复成功回调结果一致。
        if (order.getStatus() == OrderStatus.PAID) {
            updatePaymentEventStatus(event.getId(), PaymentEventStatus.SUCCESS);
            return order;
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            // 如果订单已经逾期、退款或处于其他非待支付状态，晚到的成功回调不能把订单改回已支付。
            // 这里标记忽略，保护订单状态机只能按允许的方向流转。
            updatePaymentEventStatus(event.getId(), PaymentEventStatus.IGNORED);
            return order;
        }

        if (order.getType() != null && order.getType() == 0) {
            // 名额已经在加入阶段占用；押金支付只确认订单和成员状态。
            payDeposit(order, request);
        } else {
            // 尾款支付只推进订单状态，不再影响成员名额。
            // paySuccess 内部用“where status = PENDING”做乐观更新。
            boolean paid = orderStateMachine.paySuccess(order, request.getPayRequestNo(), request.getCallbackRequestNo(), request.getChannelTxnId());
            if (!paid) {
                updatePaymentEventStatus(event.getId(), PaymentEventStatus.IGNORED);
                return orderMapper.selectOne(new QueryWrapper<Order>().eq(DbFieldConstant.ORDER_NO, request.getOrderNo()));
            }
        }
        updatePaymentEventStatus(event.getId(), PaymentEventStatus.SUCCESS);
        return orderMapper.selectOne(new QueryWrapper<Order>().eq(DbFieldConstant.ORDER_NO, request.getOrderNo()));
    }

    @Override
    @Transactional
    public void refund(String orderNo) {
        Order order = orderMapper.selectOne(new QueryWrapper<Order>().eq(DbFieldConstant.ORDER_NO, orderNo));
        if (order == null) throw new BaseException(ErrorConstant.ORDER_NOT_FOUND);
        orderStateMachine.refund(order);
    }

    @Override
    @Transactional
    public void release(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) throw new BaseException(ErrorConstant.ORDER_NOT_FOUND);
        orderStateMachine.release(order);
    }

    @Override
    public Order getByNo(String orderNo) {
        Order order = orderMapper.selectOne(new QueryWrapper<Order>().eq(DbFieldConstant.ORDER_NO, orderNo));
        if (order == null) throw new BaseException(ErrorConstant.ORDER_NOT_FOUND);
        return order;
    }

    @Override
    public List<Order> myOrders(Long userId, Integer type, Integer status, Integer page, Integer size) {
        QueryWrapper<Order> queryWrapper = new QueryWrapper<Order>()
                .eq(DbFieldConstant.USER_ID, userId)
                .eq(type != null, DbFieldConstant.TYPE, type)
                .eq(status != null, DbFieldConstant.STATUS, status)
                .orderByDesc(DbFieldConstant.CREATE_TIME);
        Page<Order> pageResult = orderMapper.selectPage(new Page<>(page, size), queryWrapper);
        return pageResult.getRecords();
    }

    @Override
    public List<Order> shopOrders(Long shopId, Integer status, Integer page, Integer size) {
        QueryWrapper<Order> queryWrapper = new QueryWrapper<Order>()
                .eq(DbFieldConstant.PAYEE_ID, shopId).eq(DbFieldConstant.PAYEE_TYPE, 1)
                .eq(status != null, DbFieldConstant.STATUS, status)
                .orderByDesc(DbFieldConstant.CREATE_TIME);
        Page<Order> pageResult = orderMapper.selectPage(new Page<>(page, size), queryWrapper);
        return pageResult.getRecords();
    }

    private BigDecimal resolveAmount(CarPool pool, Integer type) {
        BigDecimal price = pool.getPrice() == null ? BigDecimal.ZERO : pool.getPrice();
        BigDecimal deposit = pool.getDeposit() == null ? BigDecimal.ZERO : pool.getDeposit();
        if (type != null && type == 1) {
            BigDecimal rest = price.subtract(deposit);
            return rest.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : rest;
        }
        return deposit;
    }

    private void payDeposit(Order order, MockPayCallbackRequest callback) {
        // 名额已经在 join 阶段占用；押金支付只负责确认订单和成员状态。
        Order latest = orderMapper.selectOne(new QueryWrapper<Order>().eq(DbFieldConstant.ORDER_NO, order.getOrderNo()));
        if (latest == null) throw new BaseException(ErrorConstant.ORDER_NOT_FOUND);
        if (latest.getStatus() != OrderStatus.PENDING && latest.getStatus() != OrderStatus.PAID) {
            throw new BaseException(ErrorConstant.ORDER_STATUS_INVALID);
        }

        PoolMember member = memberMapper.selectOne(new QueryWrapper<PoolMember>()
                .eq(DbFieldConstant.POOL_ID, latest.getPoolId())
                .eq(DbFieldConstant.USER_ID, latest.getUserId()));
        if (member == null) throw new BaseException(ErrorConstant.POOL_MEMBER_NOT_FOUND);

        CarPool pool = poolMapper.selectById(latest.getPoolId());
        if (pool == null) throw new BaseException(ErrorConstant.POOL_NOT_FOUND);
        if (pool.getStatus() != PoolStatus.OPEN && pool.getStatus() != PoolStatus.FULL) {
            throw new BaseException(ErrorConstant.CURRENT_POOL_STATUS_CANNOT_PAY_DEPOSIT);
        }

        if (member.getStatus() == MemberStatus.JOINED) {
            if (latest.getStatus() == OrderStatus.PENDING) {
                boolean paid = orderStateMachine.paySuccess(latest, callback.getPayRequestNo(), callback.getCallbackRequestNo(), callback.getChannelTxnId());
                if (!paid) throw new BaseException(ErrorConstant.ORDER_STATUS_INVALID);
            }
            return;
        }
        if (member.getStatus() != MemberStatus.PENDING_PAYMENT) {
            throw new BaseException(ErrorConstant.MEMBER_STATUS_CANNOT_PAY_DEPOSIT);
        }

        boolean paid = latest.getStatus() == OrderStatus.PAID
                || orderStateMachine.paySuccess(latest, callback.getPayRequestNo(), callback.getCallbackRequestNo(), callback.getChannelTxnId());
        if (!paid) throw new BaseException(ErrorConstant.ORDER_STATUS_INVALID);

        int updated = memberMapper.update(null, new UpdateWrapper<PoolMember>()
                .set(DbFieldConstant.STATUS, MemberStatus.JOINED)
                .eq(DbFieldConstant.ID, member.getId())
                .eq(DbFieldConstant.STATUS, MemberStatus.PENDING_PAYMENT));
        if (updated == 0) {
            PoolMember latestMember = memberMapper.selectById(member.getId());
            if (latestMember != null && latestMember.getStatus() == MemberStatus.JOINED) {
                return;
            }
            throw new BaseException(ErrorConstant.MEMBER_STATUS_CHANGED);
        }

        // 押金支付即自动确认成团
        memberMapper.update(null, new UpdateWrapper<PoolMember>()
                .set("completed_confirmed", ConfirmStatus.CONFIRMED)
                .set("completed_confirm_time", LocalDateTime.now())
                .eq(DbFieldConstant.ID, member.getId())
                .eq("completed_confirmed", ConfirmStatus.UNCONFIRMED));

        // 当组局已满且所有名额均已支付并确认，自动推进到 COMPLETED
        if (pool.getStatus() == PoolStatus.FULL) {
            long totalJoined = memberMapper.selectCount(new QueryWrapper<PoolMember>()
                    .eq(DbFieldConstant.POOL_ID, pool.getId())
                    .eq(DbFieldConstant.STATUS, MemberStatus.JOINED));
            if (totalJoined == pool.getMaxMembers()) {
                long confirmedCount = memberMapper.selectCount(new QueryWrapper<PoolMember>()
                        .eq(DbFieldConstant.POOL_ID, pool.getId())
                        .eq(DbFieldConstant.STATUS, MemberStatus.JOINED)
                        .eq("completed_confirmed", ConfirmStatus.CONFIRMED));
                if (confirmedCount == totalJoined) {
                    stateMachine.toCompleted(pool.getId());
                    messagingTemplate.convertAndSend("/topic/pool/" + pool.getId(), Map.of(
                            "event", "POOL_COMPLETED",
                            "poolId", pool.getId(),
                            "time", LocalDateTime.now().toString()));
                }
            }
        }

        stringRedis.delete(RedisKeyConstant.POOL_DETAIL_PREFIX + pool.getId());
    }

    private String resolveIdempotentKey(Long userId, Long poolId, Integer type, String idempotentKey) {
        if (StringUtils.hasText(idempotentKey)) {
            return idempotentKey.trim();
        }
        // 前端没有显式传幂等 Key 时，用用户、组局、订单类型拼出稳定业务 Key。
        // 同一个用户对同一个组局创建同一种订单，多次请求会落到同一个 Key。
        return userId + ":" + poolId + ":" + type;
    }

    private LocalDateTime resolveExpireTime(Integer type) {
        return type != null && type == 1
                ? LocalDateTime.now().plusHours(FINAL_PAY_TIMEOUT_HOURS)
                : LocalDateTime.now().plusMinutes(DEPOSIT_PAY_TIMEOUT_MINUTES);
    }

    private PaymentEvent insertPaymentEvent(String eventKey, MockPayCallbackRequest request) {
        // 先把原始回调事件落库，状态设置为处理中。
        // payment_event 表上的唯一索引会把重复通知变成 DuplicateKeyException，再通过回查实现幂等。
        PaymentEvent event = PaymentEvent.builder()
                .eventKey(eventKey)
                .orderNo(request.getOrderNo())
                .eventType(EVENT_TYPE_PAY_CALLBACK)
                .requestNo(request.getCallbackRequestNo())
                .channelTxnId(request.getChannelTxnId())
                .status(PaymentEventStatus.PROCESSING)
                .rawPayload(JSONUtil.toJsonStr(request))
                .build();
        try {
            // 第一次收到该回调事件时插入成功，后续由业务逻辑继续处理订单。
            paymentEventMapper.insert(event);
            return event;
        } catch (DuplicateKeyException e) {
            // 重复回调可能命中 event_key 唯一索引，优先按 event_key 回查。
            PaymentEvent existing = paymentEventMapper.selectOne(new QueryWrapper<PaymentEvent>()
                    .eq(DbFieldConstant.EVENT_KEY, eventKey)
                    .last("LIMIT 1"));
            if (existing != null) return existing;
            // 如果渠道流水号重复，也认为是同一个支付事件，回查已有记录。
            existing = paymentEventMapper.selectOne(new QueryWrapper<PaymentEvent>()
                    .eq("channel_txn_id", request.getChannelTxnId())
                    .last("LIMIT 1"));
            if (existing != null) return existing;
            // 如果回调请求号重复，也回查已有记录，覆盖不同唯一索引触发冲突的情况。
            existing = paymentEventMapper.selectOne(new QueryWrapper<PaymentEvent>()
                    .eq("request_no", request.getCallbackRequestNo())
                    .last("LIMIT 1"));
            if (existing != null) return existing;
            throw e;
        }
    }

    private void updatePaymentEventStatus(Long eventId, int status) {
        paymentEventMapper.update(null, new UpdateWrapper<PaymentEvent>()
                .set(DbFieldConstant.STATUS, status)
                .eq(DbFieldConstant.ID, eventId));
    }
}
