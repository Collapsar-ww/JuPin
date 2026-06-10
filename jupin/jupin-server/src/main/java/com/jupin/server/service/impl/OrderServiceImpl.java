package com.jupin.server.service.impl;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderMapper orderMapper;
    private final PaymentEventMapper paymentEventMapper;
    private final PoolMapper poolMapper;
    private final PoolMemberMapper memberMapper;
    private final OrderStateMachine orderStateMachine;
    private final RedissonClient redisson;
    private final StringRedisTemplate stringRedis;
    private final TimeoutProducer timeoutProducer;
    private final Snowflake snowflake = IdUtil.getSnowflake(1, 1);
    private static final long DEPOSIT_PAY_TIMEOUT_MINUTES = 15;
    private static final long FINAL_PAY_TIMEOUT_HOURS = 24;
    private static final String PAY_STATUS_SUCCESS = "SUCCESS";
    private static final String EVENT_TYPE_PAY_CALLBACK = "PAY_CALLBACK";
    private static final int IDEMPOTENT_RETRY_TIMES = 5;
    private static final long IDEMPOTENT_RETRY_SLEEP_MS = 30;

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Order create(Long userId, Long poolId, Integer type, String idempotentKey) {
        CarPool pool = poolMapper.selectById(poolId);
        if (pool == null) throw new BaseException(ErrorConstant.POOL_NOT_FOUND);
        if (type == null || (type != 0 && type != 1)) {
            throw new BaseException(ErrorConstant.INVALID_ORDER_TYPE);
        }
        String normalizedIdempotentKey = resolveIdempotentKey(userId, poolId, type, idempotentKey);

        PoolMember member = memberMapper.selectOne(new QueryWrapper<PoolMember>()
                .eq(DbFieldConstant.POOL_ID, poolId)
                .eq(DbFieldConstant.USER_ID, userId));
        if (member == null) {
            throw new BaseException(ErrorConstant.NOT_POOL_MEMBER_CANNOT_CREATE_ORDER);
        }
        if (type == 0) {
            if (member.getStatus() != MemberStatus.PENDING_PAYMENT) {
                throw new BaseException(ErrorConstant.MEMBER_STATUS_CANNOT_CREATE_DEPOSIT_ORDER);
            }
            if (pool.getStatus() != PoolStatus.OPEN && pool.getStatus() != PoolStatus.FULL) {
                throw new BaseException(ErrorConstant.CURRENT_POOL_STATUS_CANNOT_PAY_DEPOSIT);
            }
        } else {
            if (member.getStatus() != MemberStatus.JOINED) {
                throw new BaseException(ErrorConstant.NOT_FORMAL_MEMBER_CANNOT_CREATE_FEE_ORDER);
            }
            if (pool.getStatus() != PoolStatus.COMPLETED) {
                throw new BaseException(ErrorConstant.POOL_NOT_COMPLETED_CANNOT_CREATE_FEE_ORDER);
            }
        }

        Order existingOrder = orderMapper.selectOne(new QueryWrapper<Order>()
                .eq(DbFieldConstant.USER_ID, userId).eq(DbFieldConstant.POOL_ID, poolId)
                .eq(DbFieldConstant.TYPE, type).in(DbFieldConstant.STATUS, OrderStatus.PENDING, OrderStatus.PAID)
                .last("LIMIT 1"));
        if (existingOrder != null) return existingOrder;

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
        orderMapper.insert(order);
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
    @Transactional(isolation = Isolation.READ_COMMITTED)
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

        String eventKey = "callback:" + request.getChannelTxnId();
        PaymentEvent event = insertPaymentEvent(eventKey, request);
        if (event.getStatus() != null && event.getStatus() != PaymentEventStatus.PROCESSING) {
            return orderMapper.selectOne(new QueryWrapper<Order>().eq(DbFieldConstant.ORDER_NO, request.getOrderNo()));
        }

        if (!PAY_STATUS_SUCCESS.equalsIgnoreCase(request.getPayStatus())) {
            updatePaymentEventStatus(event.getId(), PaymentEventStatus.IGNORED);
            return order;
        }
        if (order.getStatus() == OrderStatus.PAID) {
            updatePaymentEventStatus(event.getId(), PaymentEventStatus.SUCCESS);
            return order;
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            updatePaymentEventStatus(event.getId(), PaymentEventStatus.IGNORED);
            return order;
        }

        if (order.getType() != null && order.getType() == 0) {
            payDeposit(order, request);
        } else {
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
        String lockKey = RedisKeyConstant.POOL_LOCK_PREFIX + order.getPoolId();
        RLock lock = redisson.getLock(lockKey);
        try {
            if (!lock.tryLock(3, 10, TimeUnit.SECONDS)) {
                throw new BaseException(ErrorConstant.SYSTEM_BUSY);
            }

            Order latest = orderMapper.selectOne(new QueryWrapper<Order>().eq(DbFieldConstant.ORDER_NO, order.getOrderNo()));
            if (latest == null) throw new BaseException(ErrorConstant.ORDER_NOT_FOUND);
            if (latest.getStatus() != OrderStatus.PENDING) throw new BaseException(ErrorConstant.ORDER_STATUS_INVALID);

            PoolMember member = memberMapper.selectOne(new QueryWrapper<PoolMember>()
                    .eq(DbFieldConstant.POOL_ID, latest.getPoolId())
                    .eq(DbFieldConstant.USER_ID, latest.getUserId()));
            if (member == null) throw new BaseException(ErrorConstant.POOL_MEMBER_NOT_FOUND);
            if (member.getStatus() == MemberStatus.JOINED) {
                boolean paid = orderStateMachine.paySuccess(latest, callback.getPayRequestNo(), callback.getCallbackRequestNo(), callback.getChannelTxnId());
                if (!paid) throw new BaseException(ErrorConstant.ORDER_STATUS_INVALID);
                return;
            }
            if (member.getStatus() != MemberStatus.PENDING_PAYMENT) {
                throw new BaseException(ErrorConstant.MEMBER_STATUS_CANNOT_PAY_DEPOSIT);
            }

            CarPool pool = poolMapper.selectById(latest.getPoolId());
            if (pool == null) throw new BaseException(ErrorConstant.POOL_NOT_FOUND);
            if (pool.getStatus() != PoolStatus.OPEN && pool.getStatus() != PoolStatus.FULL) {
                throw new BaseException(ErrorConstant.CURRENT_POOL_STATUS_CANNOT_PAY_DEPOSIT);
            }
            boolean paid = orderStateMachine.paySuccess(latest, callback.getPayRequestNo(), callback.getCallbackRequestNo(), callback.getChannelTxnId());
            if (!paid) throw new BaseException(ErrorConstant.ORDER_STATUS_INVALID);

            int updated = memberMapper.update(null, new UpdateWrapper<PoolMember>()
                    .set(DbFieldConstant.STATUS, MemberStatus.JOINED)
                    .eq(DbFieldConstant.ID, member.getId())
                    .eq(DbFieldConstant.STATUS, MemberStatus.PENDING_PAYMENT));
            if (updated == 0) throw new BaseException(ErrorConstant.MEMBER_STATUS_CHANGED);

            stringRedis.delete(RedisKeyConstant.POOL_DETAIL_PREFIX + pool.getId());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BaseException(ErrorConstant.SYSTEM_BUSY);
        } finally {
            if (lock.isHeldByCurrentThread()) lock.unlock();
        }
    }

    private String resolveIdempotentKey(Long userId, Long poolId, Integer type, String idempotentKey) {
        if (StringUtils.hasText(idempotentKey)) {
            return idempotentKey.trim();
        }
        return userId + ":" + poolId + ":" + type;
    }

    private LocalDateTime resolveExpireTime(Integer type) {
        return type != null && type == 1
                ? LocalDateTime.now().plusHours(FINAL_PAY_TIMEOUT_HOURS)
                : LocalDateTime.now().plusMinutes(DEPOSIT_PAY_TIMEOUT_MINUTES);
    }

    private PaymentEvent insertPaymentEvent(String eventKey, MockPayCallbackRequest request) {
        PaymentEvent event = PaymentEvent.builder()
                .eventKey(eventKey)
                .orderNo(request.getOrderNo())
                .eventType(EVENT_TYPE_PAY_CALLBACK)
                .requestNo(request.getCallbackRequestNo())
                .channelTxnId(request.getChannelTxnId())
                .status(PaymentEventStatus.PROCESSING)
                .rawPayload(JSONUtil.toJsonStr(request))
                .build();
        paymentEventMapper.insert(event);
        return event;
    }

    private Order waitForExistingOrder(Long userId, Long poolId, Integer type, String idempotentKey) {
        for (int i = 0; i < IDEMPOTENT_RETRY_TIMES; i++) {
            Order existing = findExistingOrder(userId, poolId, type, idempotentKey);
            if (existing != null) return existing;
            sleepBeforeIdempotentRetry();
        }
        return findExistingOrder(userId, poolId, type, idempotentKey);
    }

    private Order findExistingOrder(Long userId, Long poolId, Integer type, String idempotentKey) {
        Order existing = orderMapper.selectOne(new QueryWrapper<Order>()
                .eq("idempotent_key", idempotentKey)
                .eq(DbFieldConstant.USER_ID, userId)
                .last("LIMIT 1"));
        if (existing != null) return existing;
        return orderMapper.selectOne(new QueryWrapper<Order>()
                .eq(DbFieldConstant.USER_ID, userId)
                .eq(DbFieldConstant.POOL_ID, poolId)
                .eq(DbFieldConstant.TYPE, type)
                .last("LIMIT 1"));
    }

    private PaymentEvent waitForExistingPaymentEvent(String eventKey, MockPayCallbackRequest request) {
        for (int i = 0; i < IDEMPOTENT_RETRY_TIMES; i++) {
            PaymentEvent existing = findExistingPaymentEvent(eventKey, request);
            if (existing != null) return existing;
            sleepBeforeIdempotentRetry();
        }
        return findExistingPaymentEvent(eventKey, request);
    }

    private PaymentEvent findExistingPaymentEvent(String eventKey, MockPayCallbackRequest request) {
        PaymentEvent existing = paymentEventMapper.selectOne(new QueryWrapper<PaymentEvent>()
                .eq(DbFieldConstant.EVENT_KEY, eventKey)
                .last("LIMIT 1"));
        if (existing != null) return existing;
        existing = paymentEventMapper.selectOne(new QueryWrapper<PaymentEvent>()
                .eq("channel_txn_id", request.getChannelTxnId())
                .last("LIMIT 1"));
        if (existing != null) return existing;
        return paymentEventMapper.selectOne(new QueryWrapper<PaymentEvent>()
                .eq("request_no", request.getCallbackRequestNo())
                .last("LIMIT 1"));
    }

    private void sleepBeforeIdempotentRetry() {
        try {
            Thread.sleep(IDEMPOTENT_RETRY_SLEEP_MS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new BaseException(ErrorConstant.SYSTEM_BUSY);
        }
    }

    private void updatePaymentEventStatus(Long eventId, int status) {
        paymentEventMapper.update(null, new UpdateWrapper<PaymentEvent>()
                .set(DbFieldConstant.STATUS, status)
                .eq(DbFieldConstant.ID, eventId));
    }
}
