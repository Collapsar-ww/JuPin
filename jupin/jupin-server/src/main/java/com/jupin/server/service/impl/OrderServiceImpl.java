package com.jupin.server.service.impl;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jupin.common.constant.DbFieldConstant;
import com.jupin.common.constant.ErrorConstant;
import com.jupin.common.constant.MemberStatus;
import com.jupin.common.constant.OrderStatus;
import com.jupin.common.constant.PoolStatus;
import com.jupin.common.constant.RedisKeyConstant;
import com.jupin.common.exception.BaseException;
import com.jupin.pojo.entity.CarPool;
import com.jupin.pojo.entity.Order;
import com.jupin.pojo.entity.PoolMember;
import com.jupin.server.mapper.OrderMapper;
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
    private final PoolMapper poolMapper;
    private final PoolMemberMapper memberMapper;
    private final RedissonClient redisson;
    private final StringRedisTemplate stringRedis;
    private final TimeoutProducer timeoutProducer;
    private final Snowflake snowflake = IdUtil.getSnowflake(1, 1);
    private static final long DEPOSIT_PAY_TIMEOUT_MINUTES = 15;
    private static final long FINAL_PAY_TIMEOUT_HOURS = 24;

    @Override
    @Transactional
    public Order create(Long userId, Long poolId, Integer type, String idempotentKey) {
        CarPool pool = poolMapper.selectById(poolId);
        if (pool == null) throw new BaseException(ErrorConstant.POOL_NOT_FOUND);
        if (type == null || (type != 0 && type != 1)) {
            throw new BaseException(ErrorConstant.INVALID_ORDER_TYPE);
        }
        String normalizedIdempotentKey = resolveIdempotentKey(userId, poolId, type, idempotentKey);
        Order idempotentOrder = orderMapper.selectOne(new QueryWrapper<Order>()
                .eq("idempotent_key", normalizedIdempotentKey)
                .eq(DbFieldConstant.USER_ID, userId)
                .last("LIMIT 1"));
        if (idempotentOrder != null) {
            return idempotentOrder;
        }
        Long conflictCount = orderMapper.selectCount(new QueryWrapper<Order>()
                .eq("idempotent_key", normalizedIdempotentKey)
                .ne(DbFieldConstant.USER_ID, userId));
        if (conflictCount > 0) throw new BaseException(ErrorConstant.IDEMPOTENT_KEY_CONFLICT);

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

        Long count = orderMapper.selectCount(new QueryWrapper<Order>()
                .eq(DbFieldConstant.USER_ID, userId).eq(DbFieldConstant.POOL_ID, poolId)
                .eq(DbFieldConstant.TYPE, type).in(DbFieldConstant.STATUS, OrderStatus.PENDING, OrderStatus.PAID));
        if (count > 0) throw new BaseException(ErrorConstant.ORDER_ALREADY_CREATED);

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
            orderMapper.insert(order);
        } catch (DuplicateKeyException e) {
            Order existing = orderMapper.selectOne(new QueryWrapper<Order>()
                    .eq("idempotent_key", normalizedIdempotentKey)
                    .eq(DbFieldConstant.USER_ID, userId)
                    .last("LIMIT 1"));
            if (existing != null) return existing;
            throw e;
        }
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
        if (order.getType() != null && order.getType() == 0) {
            payDeposit(order);
            return;
        }
        markPaid(order);
    }

    @Override
    @Transactional
    public void refund(String orderNo) {
        Order order = orderMapper.selectOne(new QueryWrapper<Order>().eq(DbFieldConstant.ORDER_NO, orderNo));
        if (order == null) throw new BaseException(ErrorConstant.ORDER_NOT_FOUND);
        if (order.getStatus() != OrderStatus.PAID) throw new BaseException(ErrorConstant.ONLY_PAID_ORDER_CAN_REFUND);
        order.setStatus(OrderStatus.REFUNDED);
        order.setRefundTime(LocalDateTime.now());
        orderMapper.updateById(order);
    }

    @Override
    @Transactional
    public void release(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) throw new BaseException(ErrorConstant.ORDER_NOT_FOUND);
        if (order.getStatus() != OrderStatus.PAID) throw new BaseException(ErrorConstant.ONLY_PAID_ORDER_CAN_RELEASE);
        if (order.getReleaseStatus() == 1) return;

        order.setReleaseStatus(1);
        order.setReleaseTime(LocalDateTime.now());
        orderMapper.updateById(order);
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

    private void markPaid(Order order) {
        int rows = orderMapper.update(null, new UpdateWrapper<Order>()
                .set(DbFieldConstant.STATUS, OrderStatus.PAID)
                .set("pay_time", LocalDateTime.now())
                .set("pay_request_no", "MOCK-" + snowflake.nextIdStr())
                .eq(DbFieldConstant.ID, order.getId())
                .eq(DbFieldConstant.STATUS, OrderStatus.PENDING));
        if (rows == 0) {
            Order latest = orderMapper.selectById(order.getId());
            if (latest != null && latest.getStatus() == OrderStatus.PAID) return;
            throw new BaseException(ErrorConstant.ORDER_STATUS_INVALID);
        }
    }

    private void payDeposit(Order order) {
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
                markPaid(latest);
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
            if (pool.getCurrentMembers() >= pool.getMaxMembers()) {
                throw new BaseException(ErrorConstant.POOL_ALREADY_FULL);
            }

            int seatUpdated = poolMapper.update(null, new UpdateWrapper<CarPool>()
                    .setSql("current_members = current_members + 1")
                    .eq(DbFieldConstant.ID, pool.getId())
                    .in(DbFieldConstant.STATUS, PoolStatus.OPEN, PoolStatus.FULL)
                    .apply("current_members < max_members"));
            if (seatUpdated == 0) {
                throw new BaseException(ErrorConstant.POOL_ALREADY_FULL);
            }

            markPaid(latest);

            int updated = memberMapper.update(null, new UpdateWrapper<PoolMember>()
                    .set(DbFieldConstant.STATUS, MemberStatus.JOINED)
                    .eq(DbFieldConstant.ID, member.getId())
                    .eq(DbFieldConstant.STATUS, MemberStatus.PENDING_PAYMENT));
            if (updated == 0) throw new BaseException(ErrorConstant.MEMBER_STATUS_CHANGED);

            Long joinedCount = memberMapper.selectCount(new QueryWrapper<PoolMember>()
                    .eq(DbFieldConstant.POOL_ID, pool.getId())
                    .eq(DbFieldConstant.STATUS, MemberStatus.JOINED));
            poolMapper.update(null, new UpdateWrapper<CarPool>()
                    .set("current_members", joinedCount.intValue())
                    .eq(DbFieldConstant.ID, pool.getId()));
            stringRedis.delete(RedisKeyConstant.POOL_DETAIL_PREFIX + pool.getId());
            if (joinedCount.equals(pool.getMaxMembers().longValue())) {
                int rows = poolMapper.update(null, new UpdateWrapper<CarPool>()
                        .set(DbFieldConstant.STATUS, PoolStatus.FULL)
                        .eq(DbFieldConstant.ID, pool.getId())
                        .eq(DbFieldConstant.STATUS, PoolStatus.OPEN));
                if (rows == 0 && pool.getStatus() == PoolStatus.OPEN) {
                    throw new BaseException(ErrorConstant.POOL_STATUS_ABNORMAL_CANNOT_SET_FULL);
                }
            } else if (pool.getStatus() == PoolStatus.FULL) {
                poolMapper.update(null, new UpdateWrapper<CarPool>()
                        .set(DbFieldConstant.STATUS, PoolStatus.OPEN)
                        .eq(DbFieldConstant.ID, pool.getId())
                        .eq(DbFieldConstant.STATUS, PoolStatus.FULL));
            }
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
}
