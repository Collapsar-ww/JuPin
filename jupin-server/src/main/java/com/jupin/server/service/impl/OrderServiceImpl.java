package com.jupin.server.service.impl;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jupin.common.constant.MemberStatus;
import com.jupin.common.constant.OrderStatus;
import com.jupin.common.constant.PoolStatus;
import com.jupin.common.exception.BaseException;
import com.jupin.pojo.entity.CarPool;
import com.jupin.pojo.entity.Order;
import com.jupin.pojo.entity.PoolMember;
import com.jupin.server.mapper.OrderMapper;
import com.jupin.server.mapper.PoolMemberMapper;
import com.jupin.server.mapper.PoolMapper;
import com.jupin.server.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final Snowflake snowflake = IdUtil.getSnowflake(1, 1);

    @Override
    @Transactional
    public Order create(Long userId, Long poolId, Integer type) {
        CarPool pool = poolMapper.selectById(poolId);
        if (pool == null) throw new BaseException("拼车不存在");

        Long count = orderMapper.selectCount(new QueryWrapper<Order>()
                .eq("user_id", userId).eq("pool_id", poolId)
                .eq("type", type).in("status", 0, 1));
        if (count > 0) throw new BaseException("已创建过该类型的订单");

        Order order = Order.builder()
                .orderNo(snowflake.nextIdStr())
                .userId(userId)
                .poolId(poolId)
                .type(type)
                .amount(resolveAmount(pool, type))
                .status(OrderStatus.PENDING)
                .payeeId(pool.getType() == 1 ? pool.getShopId() : pool.getDmId())
                .payeeType(pool.getType() == 1 ? 1 : 0)
                .build();
        orderMapper.insert(order);
        return order;
    }

    @Override
    @Transactional
    public void pay(String orderNo) {
        Order order = orderMapper.selectOne(new QueryWrapper<Order>().eq("order_no", orderNo));
        if (order == null) throw new BaseException("订单不存在");
        if (order.getStatus() != OrderStatus.PENDING) throw new BaseException("订单状态异常");
        if (order.getType() != null && order.getType() == 0) {
            payDeposit(order);
            return;
        }
        markPaid(order);
    }

    @Override
    @Transactional
    public void refund(String orderNo) {
        Order order = orderMapper.selectOne(new QueryWrapper<Order>().eq("order_no", orderNo));
        if (order == null) throw new BaseException("订单不存在");
        if (order.getStatus() != OrderStatus.PAID) throw new BaseException("仅已支付订单可退款");
        order.setStatus(OrderStatus.REFUNDED);
        order.setRefundTime(LocalDateTime.now());
        orderMapper.updateById(order);
    }

    @Override
    @Transactional
    public void release(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) throw new BaseException("订单不存在");
        if (order.getStatus() != OrderStatus.PAID) throw new BaseException("仅已支付订单可释放");
        if (order.getReleaseStatus() == 1) return;

        order.setReleaseStatus(1);
        order.setReleaseTime(LocalDateTime.now());
        orderMapper.updateById(order);
    }

    @Override
    public Order getByNo(String orderNo) {
        Order order = orderMapper.selectOne(new QueryWrapper<Order>().eq("order_no", orderNo));
        if (order == null) throw new BaseException("订单不存在");
        return order;
    }

    @Override
    public List<Order> myOrders(Long userId, Integer type, Integer status, Integer page, Integer size) {
        QueryWrapper<Order> q = new QueryWrapper<Order>()
                .eq("user_id", userId)
                .eq(type != null, "type", type)
                .eq(status != null, "status", status)
                .orderByDesc("create_time");
        Page<Order> p = orderMapper.selectPage(new Page<>(page, size), q);
        return p.getRecords();
    }

    @Override
    public List<Order> shopOrders(Long shopId, Integer status, Integer page, Integer size) {
        QueryWrapper<Order> q = new QueryWrapper<Order>()
                .eq("payee_id", shopId).eq("payee_type", 1)
                .eq(status != null, "status", status)
                .orderByDesc("create_time");
        Page<Order> p = orderMapper.selectPage(new Page<>(page, size), q);
        return p.getRecords();
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
        order.setStatus(OrderStatus.PAID);
        order.setPayTime(LocalDateTime.now());
        orderMapper.updateById(order);
    }

    private void payDeposit(Order order) {
        String lockKey = "pool:lock:" + order.getPoolId();
        RLock lock = redisson.getLock(lockKey);
        try {
            if (!lock.tryLock(3, 10, TimeUnit.SECONDS)) {
                throw new BaseException("系统繁忙，请稍后再试");
            }

            Order latest = orderMapper.selectOne(new QueryWrapper<Order>().eq("order_no", order.getOrderNo()));
            if (latest == null) throw new BaseException("订单不存在");
            if (latest.getStatus() != OrderStatus.PENDING) throw new BaseException("订单状态异常");

            PoolMember member = memberMapper.selectOne(new QueryWrapper<PoolMember>()
                    .eq("pool_id", latest.getPoolId())
                    .eq("user_id", latest.getUserId()));
            if (member == null) throw new BaseException("拼车成员不存在");
            if (member.getStatus() == MemberStatus.JOINED) {
                markPaid(latest);
                return;
            }
            if (member.getStatus() != MemberStatus.PENDING_PAYMENT) {
                throw new BaseException("成员状态不允许支付押金");
            }

            CarPool pool = poolMapper.selectById(latest.getPoolId());
            if (pool == null) throw new BaseException("拼车不存在");
            if (pool.getStatus() != PoolStatus.OPEN && pool.getStatus() != PoolStatus.FULL) {
                throw new BaseException("当前拼车状态不允许支付押金");
            }
            if (pool.getCurrentMembers() >= pool.getMaxMembers()) {
                throw new BaseException("拼车已满员");
            }

            markPaid(latest);

            int updated = memberMapper.update(null, new UpdateWrapper<PoolMember>()
                    .set("status", MemberStatus.JOINED)
                    .eq("id", member.getId())
                    .eq("status", MemberStatus.PENDING_PAYMENT));
            if (updated == 0) throw new BaseException("成员状态已变化，请刷新后重试");

            pool.setCurrentMembers(pool.getCurrentMembers() + 1);
            poolMapper.updateById(pool);
            if (pool.getCurrentMembers().equals(pool.getMaxMembers())) {
                int rows = poolMapper.update(null, new UpdateWrapper<CarPool>()
                        .set("status", PoolStatus.FULL)
                        .eq("id", pool.getId())
                        .eq("status", PoolStatus.OPEN));
                if (rows == 0 && pool.getStatus() == PoolStatus.OPEN) {
                    throw new BaseException("拼车状态异常，无法设为满员");
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BaseException("系统繁忙，请稍后再试");
        } finally {
            if (lock.isHeldByCurrentThread()) lock.unlock();
        }
    }
}
