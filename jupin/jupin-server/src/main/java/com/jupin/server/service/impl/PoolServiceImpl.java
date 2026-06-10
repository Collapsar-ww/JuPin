package com.jupin.server.service.impl;

import cn.hutool.core.lang.TypeReference;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jupin.common.constant.*;
import com.jupin.common.exception.BaseException;
import com.jupin.pojo.dto.PoolCreateRequest;
import com.jupin.pojo.entity.*;
import com.jupin.pojo.vo.ConfirmVO;
import com.jupin.pojo.vo.MemberPoolVO;
import com.jupin.pojo.vo.RoleStatusVO;
import com.jupin.server.mapper.*;
import com.jupin.server.mq.TimeoutMessage;
import com.jupin.server.mq.TimeoutProducer;
import com.jupin.server.service.CreditService;
import com.jupin.server.service.PoolService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PoolServiceImpl implements PoolService {

    private final PoolMapper poolMapper;
    private final PoolMemberMapper memberMapper;
    private final UserMapper userMapper;
    private final ShopMapper shopMapper;
    private final ShopMemberMapper shopMemberMapper;
    private final ScriptMapper scriptMapper;
    private final ShopScriptMapper shopScriptMapper;
    private final PlayerPreferenceMapper preferenceMapper;
    private final OrderMapper orderMapper;
    private final PoolStateMachine stateMachine;
    private final RedissonClient redisson;
    private final StringRedisTemplate stringRedis;
    private final CreditService creditService;
    private final SimpMessagingTemplate messagingTemplate;
    private final TimeoutProducer timeoutProducer;

    // Recommend scoring weights
    private static final int SCORE_CITY = 50;
    private static final int SCORE_SCRIPT_TYPE = 35;
    private static final int SCORE_TIME_SLOT = 25;
    private static final int SCORE_PRICE = 15;
    private static final int SCORE_MEMBER_COUNT = 10;
    private static final int MAX_RECOMMEND_FETCH = 500;
    private static final long POOL_DETAIL_TTL_MINUTES = 10;
    private static final long POOL_DETAIL_NULL_TTL_SECONDS = 60;
    private static final long CONFIRM_TIMEOUT_HOURS = 2;

    @Override
    @Transactional
    public CarPool create(Long userId, PoolCreateRequest request) {
        User owner = userMapper.selectById(userId);
        if (owner.getCreditScore() < 60) {
            throw new BaseException(ErrorConstant.CREDIT_TOO_LOW);
        }

        Integer type = request.getType() != null ? request.getType() : 0;

        if (request.getScriptId() != null) {
            Script script = scriptMapper.selectById(request.getScriptId());
            if (script == null || Objects.equals(script.getStatus(), 0)) {
                throw new BaseException(ErrorConstant.SCRIPT_NOT_FOUND_OR_OFFLINE);
            }
        }

        if (type == 1) {
            if (request.getShopId() == null) throw new BaseException(ErrorConstant.SHOP_POOL_MUST_SPECIFY_SHOP);
            Long count = shopMemberMapper.selectCount(new QueryWrapper<ShopMember>()
                    .eq(DbFieldConstant.SHOP_ID, request.getShopId()).eq(DbFieldConstant.USER_ID, userId).in(DbFieldConstant.ROLE, 1, 2));
            if (count == 0) throw new BaseException(ErrorConstant.SHOP_ROLE_REQUIRED);

            if (request.getScriptId() != null) {
                Long scriptCount = shopScriptMapper.selectCount(new QueryWrapper<ShopScript>()
                        .eq(DbFieldConstant.SHOP_ID, request.getShopId()).eq(DbFieldConstant.SCRIPT_ID, request.getScriptId()));
                if (scriptCount == 0) throw new BaseException(ErrorConstant.SCRIPT_NOT_IN_LIBRARY);
            }
        }

        CarPool pool = CarPool.builder()
                .type(type)
                .ownerId(userId)
                .shopId(request.getShopId())
                .scriptId(request.getScriptId())
                .scriptName(request.getScriptName())
                .scriptType(request.getScriptType())
                .roles(request.getRoles())
                .city(request.getCity())
                .address(request.getAddress())
                .startTime(LocalDateTime.parse(request.getStartTime(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .endTime(StringUtils.hasText(request.getEndTime())
                        ? LocalDateTime.parse(request.getEndTime(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                        : null)
                .maxMembers(request.getMaxMembers())
                .price(request.getPrice())
                .deposit(request.getDeposit())
                .joinType(request.getJoinType())
                .dmId(resolveInitialDmId(type, userId, request.getDmId()))
                .currentMembers(type == 0 ? 1 : 0)
                .status(PoolStatus.OPEN)
                .build();
        poolMapper.insert(pool);

        if (type == 0) {
            PoolMember ownerMember = PoolMember.builder()
                    .poolId(pool.getId())
                    .userId(userId)
                    .role(1)
                    .status(MemberStatus.PENDING_PAYMENT)
                    .joinTime(LocalDateTime.now())
                    .build();
            memberMapper.insert(ownerMember);
            refreshSeatStatus(pool.getId());
        }
        sendPoolStartTimeout(pool);
        return pool;
    }

    @Override
    public CarPool getDetail(Long poolId) {
        CarPool pool = poolMapper.selectById(poolId);
        if (pool == null) {
            throw new BaseException(ErrorConstant.POOL_NOT_FOUND);
        }
        return pool;
    }

    @Override
    public List<CarPool> list(Long userId, String city, String scriptType, Integer type, Integer status,
                               BigDecimal priceMin, BigDecimal priceMax,
                               String startTimeAfter, String startTimeBefore,
                               Boolean recommend, Integer page, Integer size) {
        QueryWrapper<CarPool> queryWrapper = new QueryWrapper<CarPool>();
        queryWrapper.in(DbFieldConstant.STATUS, PoolStatus.OPEN, PoolStatus.FULL);
        if (StringUtils.hasText(city)) queryWrapper.eq(DbFieldConstant.CITY, city);
        if (StringUtils.hasText(scriptType)) queryWrapper.eq(DbFieldConstant.SCRIPT_TYPE, scriptType);
        if (type != null) queryWrapper.eq(DbFieldConstant.TYPE, type);
        if (status != null) queryWrapper.eq(DbFieldConstant.STATUS, status);
        if (priceMin != null) queryWrapper.ge("price", priceMin);
        if (priceMax != null) queryWrapper.le("price", priceMax);
        if (StringUtils.hasText(startTimeAfter)) queryWrapper.ge("start_time", startTimeAfter);
        if (StringUtils.hasText(startTimeBefore)) queryWrapper.le("start_time", startTimeBefore);
        if (!Boolean.TRUE.equals(recommend)) {
            queryWrapper.orderByDesc(DbFieldConstant.CREATE_TIME);

            Page<CarPool> pageResult = poolMapper.selectPage(new Page<>(page, size), queryWrapper);
            return pageResult.getRecords();
        }

        // Recommend mode: fetch up to MAX_RECOMMEND_FETCH rows, score in memory, sort, page
        queryWrapper.last("LIMIT " + MAX_RECOMMEND_FETCH);
        List<CarPool> pools = poolMapper.selectList(queryWrapper);
        if (pools.isEmpty()) return pools;

        PlayerPreference preference = getPreference(userId);
        Map<Long, Integer> scoreMap = pools.stream()
                .collect(Collectors.toMap(CarPool::getId, p -> calculateRecommendScore(p, preference)));

        List<CarPool> result = pools.stream()
                .sorted(Comparator.<CarPool, Integer>comparing(p -> scoreMap.getOrDefault(p.getId(), 0)).reversed()
                        .thenComparing(CarPool::getCreateTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .skip((long) Math.max(page - 1, 0) * size)
                .limit(size)
                .collect(Collectors.toList());

        for (CarPool pool : result) {
            pool.setRecommendScore(scoreMap.getOrDefault(pool.getId(), 0));
        }
        return result;
    }

    @Override
    public List<CarPool> listShopPools(Long shopId, Integer status, Integer page, Integer size) {
        QueryWrapper<CarPool> queryWrapper = new QueryWrapper<CarPool>()
                .eq(DbFieldConstant.SHOP_ID, shopId)
                .eq(status != null, DbFieldConstant.STATUS, status)
                .orderByDesc(DbFieldConstant.CREATE_TIME);
        Page<CarPool> pageResult = poolMapper.selectPage(new Page<>(page, size), queryWrapper);
        return pageResult.getRecords();
    }

    @Override
    @Transactional
    public void cancel(Long userId, Long poolId) {
        stateMachine.toCancelled(poolId, userId);
        orderMapper.update(null, new UpdateWrapper<Order>()
                .set(DbFieldConstant.STATUS, OrderStatus.REFUNDED)
                .set(DbFieldConstant.REFUND_TIME, LocalDateTime.now())
                .set(DbFieldConstant.REFUND_REASON, ErrorConstant.REFUND_REASON_POOL_CANCELLED)
                .eq(DbFieldConstant.POOL_ID, poolId)
                .eq(DbFieldConstant.STATUS, OrderStatus.PAID));
        evictPoolDetail(poolId);
    }

    @Override
    @Transactional
    public void join(Long userId, Long poolId) {
        String lockKey = RedisKeyConstant.POOL_LOCK_PREFIX + poolId;
        RLock lock = redisson.getLock(lockKey);
        try {
            if (!lock.tryLock(3, 10, TimeUnit.SECONDS)) {
                throw new BaseException(ErrorConstant.SYSTEM_BUSY);
            }
            CarPool pool = poolMapper.selectById(poolId);
            if (pool == null) throw new BaseException(ErrorConstant.POOL_NOT_FOUND);
            if (pool.getStatus() != PoolStatus.OPEN) throw new BaseException(ErrorConstant.POOL_CANNOT_JOIN);
            if (pool.getCurrentMembers() >= pool.getMaxMembers()) throw new BaseException(ErrorConstant.POOL_ALREADY_FULL);

            PoolMember existing = memberMapper.selectOne(new QueryWrapper<PoolMember>()
                    .eq(DbFieldConstant.POOL_ID, poolId).eq(DbFieldConstant.USER_ID, userId));
            if (existing != null) {
                if (existing.getStatus() == MemberStatus.JOINED
                        || existing.getStatus() == MemberStatus.PENDING_PAYMENT
                        || existing.getStatus() == MemberStatus.PENDING_REVIEW) {
                    throw new BaseException(ErrorConstant.ALREADY_IN_POOL_OR_PENDING);
                }
                int newStatus = pool.getJoinType() == 1 ? MemberStatus.PENDING_PAYMENT : MemberStatus.PENDING_REVIEW;
                if (newStatus == MemberStatus.PENDING_PAYMENT) {
                    reserveSeat(poolId);
                }
                existing.setStatus(newStatus);
                existing.setJoinTime(LocalDateTime.now());
                existing.setLeaveTime(null);
                memberMapper.updateById(existing);
            } else {
                int status = pool.getJoinType() == 1 ? MemberStatus.PENDING_PAYMENT : MemberStatus.PENDING_REVIEW;
                if (status == MemberStatus.PENDING_PAYMENT) {
                    reserveSeat(poolId);
                }
                PoolMember member = PoolMember.builder()
                        .poolId(poolId)
                        .userId(userId)
                        .role(0)
                        .status(status)
                        .joinTime(LocalDateTime.now())
                        .build();
                memberMapper.insert(member);
            }
            refreshSeatStatus(poolId);
            evictPoolDetail(poolId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BaseException(ErrorConstant.SYSTEM_BUSY);
        } finally {
            if (lock.isHeldByCurrentThread()) lock.unlock();
        }
    }

    @Override
    @Transactional
    public void leave(Long userId, Long poolId) {
        try {
            doLeave(userId, poolId);
        } catch (BaseException e) {
            throw e;
        } catch (Exception e) {
            log.error("leave error pool={} user={}: ", poolId, userId, e);
            throw new BaseException(ErrorConstant.LEAVE_FAILED + ": " + e.getMessage());
        }
    }

    private void doLeave(Long userId, Long poolId) {
        CarPool pool = poolMapper.selectById(poolId);
        if (pool == null) throw new BaseException(ErrorConstant.POOL_NOT_FOUND);

        PoolMember member = memberMapper.selectOne(new QueryWrapper<PoolMember>()
                .eq(DbFieldConstant.POOL_ID, poolId).eq(DbFieldConstant.USER_ID, userId));
        if (member == null) throw new BaseException(ErrorConstant.NOT_IN_POOL);

        if (pool.getStatus() == PoolStatus.OPEN || pool.getStatus() == PoolStatus.FULL) {
            boolean seatHeld = member.getStatus() == MemberStatus.JOINED
                    || member.getStatus() == MemberStatus.PENDING_PAYMENT;
            member.setStatus(MemberStatus.LEFT);
            member.setLeaveTime(LocalDateTime.now());
            memberMapper.updateById(member);

            if (seatHeld) {
                releaseSeat(poolId);
                evictPoolDetail(poolId);
            }
        } else if (pool.getStatus() == PoolStatus.COMPLETED) {
            long recentLeftCount = memberMapper.selectCount(new QueryWrapper<PoolMember>()
                    .eq(DbFieldConstant.USER_ID, userId)
                    .eq(DbFieldConstant.STATUS, MemberStatus.LEFT)
                    .ge("leave_time", LocalDateTime.now().minusDays(7)));

            member.setStatus(MemberStatus.LEFT);
            member.setLeaveTime(LocalDateTime.now());
            memberMapper.updateById(member);

            int penalty = calculateLeavePenalty(pool.getStartTime());
            String reason = buildLeavePenaltyReason(pool.getStartTime());
            creditService.deduct(userId, penalty, reason);

            if (recentLeftCount >= 2) {
                creditService.deduct(userId, 5, "7天内多次跳车额外扣分");
            }
        } else {
            throw new BaseException(ErrorConstant.POOL_CANNOT_LEAVE);
        }
    }

    @Override
    @Transactional
    public void approve(Long userId, Long poolId, Long targetUserId) {
        CarPool pool = poolMapper.selectById(poolId);
        if (pool == null || !pool.getOwnerId().equals(userId)) {
            throw new BaseException(ErrorConstant.NO_PERMISSION_TO_REVIEW);
        }
        String lockKey = RedisKeyConstant.POOL_LOCK_PREFIX + poolId;
        RLock lock = redisson.getLock(lockKey);
        try {
            if (!lock.tryLock(3, 10, TimeUnit.SECONDS)) {
                throw new BaseException(ErrorConstant.SYSTEM_BUSY);
            }
            int updated = memberMapper.update(null, new UpdateWrapper<PoolMember>()
                    .set("status", MemberStatus.PENDING_PAYMENT)
                    .eq("pool_id", poolId).eq("user_id", targetUserId).eq("status", MemberStatus.PENDING_REVIEW));
            if (updated > 0) {
                reserveSeat(poolId);
                refreshSeatStatus(poolId);
                evictPoolDetail(poolId);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BaseException(ErrorConstant.SYSTEM_BUSY);
        } finally {
            if (lock.isHeldByCurrentThread()) lock.unlock();
        }
    }

    @Override
    @Transactional
    public void reject(Long userId, Long poolId, Long targetUserId) {
        CarPool pool = poolMapper.selectById(poolId);
        if (pool == null || !pool.getOwnerId().equals(userId)) {
            throw new BaseException(ErrorConstant.NO_PERMISSION_TO_REVIEW);
        }
        memberMapper.update(null, new UpdateWrapper<PoolMember>()
                .set("status", MemberStatus.REJECTED)
                .eq("pool_id", poolId).eq("user_id", targetUserId).eq("status", MemberStatus.PENDING_REVIEW));
        evictPoolDetail(poolId);
    }

    @Override
    @Transactional
    public void updatePrice(Long userId, Long poolId, BigDecimal price) {
        CarPool pool = poolMapper.selectById(poolId);
        if (pool == null || !pool.getOwnerId().equals(userId)) {
            throw new BaseException(ErrorConstant.NO_PERMISSION_UPDATE_PRICE);
        }
        if (pool.getStatus() != PoolStatus.OPEN && pool.getStatus() != PoolStatus.FULL) {
            throw new BaseException(ErrorConstant.CANNOT_UPDATE_PRICE_AFTER_COMPLETED);
        }
        pool.setPrice(price);
        poolMapper.updateById(pool);
        evictPoolDetail(poolId);
    }

    @Override
    @Transactional
    public void transferDm(Long userId, Long poolId, Long newDmId) {
        CarPool pool = poolMapper.selectById(poolId);
        if (pool == null || !pool.getOwnerId().equals(userId)) {
            throw new BaseException(ErrorConstant.NO_PERMISSION_TRANSFER_DM);
        }
        if (pool.getType() != 0) throw new BaseException(ErrorConstant.ONLY_PLAYER_POOL_CAN_TRANSFER_DM);
        if (pool.getStatus() != PoolStatus.OPEN && pool.getStatus() != PoolStatus.FULL) {
            throw new BaseException(ErrorConstant.DM_CANNOT_TRANSFER_AFTER_COMPLETED);
        }
        User newDm = userMapper.selectById(newDmId);
        if (newDm == null) throw new BaseException(ErrorConstant.USER_NOT_FOUND);

        Long count = memberMapper.selectCount(new QueryWrapper<PoolMember>()
                .eq("pool_id", poolId).eq("user_id", newDmId).eq("status", MemberStatus.JOINED));
        if (count == 0) throw new BaseException(ErrorConstant.NEW_DM_NOT_POOL_MEMBER);

        pool.setDmId(newDmId);
        poolMapper.updateById(pool);
        evictPoolDetail(poolId);
    }

    @Override
    @Transactional
    public void assignDm(Long userId, Long poolId, Long dmId) {
        CarPool pool = poolMapper.selectById(poolId);
        if (pool == null) throw new BaseException(ErrorConstant.POOL_NOT_FOUND);
        if (pool.getType() != 1) throw new BaseException(ErrorConstant.ONLY_SHOP_POOL_CAN_ASSIGN_DM);

        Long count = shopMemberMapper.selectCount(new QueryWrapper<ShopMember>()
                .eq("shop_id", pool.getShopId()).eq("user_id", userId).in("role", 1, 2));
        if (count == 0) throw new BaseException(ErrorConstant.NO_PERMISSION_ASSIGN_DM);

        count = shopMemberMapper.selectCount(new QueryWrapper<ShopMember>()
                .eq("shop_id", pool.getShopId()).eq("user_id", dmId));
        if (count == 0) throw new BaseException(ErrorConstant.USER_NOT_SHOP_MEMBER);

        pool.setDmId(dmId);
        poolMapper.updateById(pool);
        evictPoolDetail(poolId);
    }

    @Override
    @Transactional
    public ConfirmVO complete(Long userId, Long poolId) {
        CarPool pool = poolMapper.selectById(poolId);
        if (pool == null) throw new BaseException(ErrorConstant.POOL_NOT_FOUND);
        if (!pool.getOwnerId().equals(userId)) throw new BaseException(ErrorConstant.ONLY_OWNER_CAN_CONFIRM);
        if (pool.getStatus() != PoolStatus.FULL) throw new BaseException(ErrorConstant.POOL_NOT_FULL);
        ensureDmSpecified(pool);

        List<PoolMember> members = memberMapper.selectList(new QueryWrapper<PoolMember>()
                .eq("pool_id", poolId).eq("status", MemberStatus.JOINED));
        if (members.size() != pool.getMaxMembers()) {
            Long lockedCount = memberMapper.selectCount(new QueryWrapper<PoolMember>()
                    .eq("pool_id", poolId)
                    .in("status", MemberStatus.PENDING_PAYMENT, MemberStatus.JOINED));
            syncCurrentMembers(pool, lockedCount.intValue());
            throw new BaseException(ErrorConstant.POOL_NOT_FULL);
        }
        boolean confirmationStarted = members.stream()
                .anyMatch(member -> member.getCompletedConfirmTime() != null);
        boolean anyRejected = members.stream()
                .anyMatch(member -> member.getCompletedConfirmed() == ConfirmStatus.REJECTED);
        if (confirmationStarted && !anyRejected) {
            throw new BaseException(ErrorConstant.CONFIRM_ALREADY_STARTED);
        }
        LocalDateTime now = LocalDateTime.now();
        memberMapper.update(null, new UpdateWrapper<PoolMember>()
                .set("completed_confirmed", ConfirmStatus.UNCONFIRMED)
                .set("completed_confirm_time", now)
                .eq("pool_id", poolId).eq("status", MemberStatus.JOINED));

        publishPoolEvent(poolId, "COMPLETED_CONFIRM_STARTED");
        timeoutProducer.send(new TimeoutMessage(TimeoutMessage.COMPLETED_CONFIRM, null, poolId, null),
                TimeUnit.HOURS.toMillis(CONFIRM_TIMEOUT_HOURS));
        evictPoolDetail(poolId);
        return new ConfirmVO(poolId, 0, members.size(), false);
    }

    private Long resolveInitialDmId(Integer type, Long ownerId, Long requestDmId) {
        if (requestDmId != null) return requestDmId;
        return Objects.equals(type, 0) ? ownerId : null;
    }

    private void ensureDmSpecified(CarPool pool) {
        if (pool.getDmId() != null) return;
        if (Objects.equals(pool.getType(), 0)) {
            PoolMember ownerMember = memberMapper.selectOne(new QueryWrapper<PoolMember>()
                    .eq(DbFieldConstant.POOL_ID, pool.getId())
                    .eq(DbFieldConstant.USER_ID, pool.getOwnerId())
                    .eq(DbFieldConstant.STATUS, MemberStatus.JOINED));
            if (ownerMember != null) {
                pool.setDmId(pool.getOwnerId());
                poolMapper.updateById(pool);
                return;
            }
        }
        throw new BaseException(ErrorConstant.DM_NOT_SPECIFIED);
    }

    private PlayerPreference getPreference(Long userId) {
        if (userId == null) return null;
        return preferenceMapper.selectOne(new QueryWrapper<PlayerPreference>().eq(DbFieldConstant.USER_ID, userId));
    }

    private int calculateRecommendScore(CarPool pool, PlayerPreference preference) {
        if (preference == null) return 0;
        int score = 0;
        if (StringUtils.hasText(preference.getCity()) && Objects.equals(preference.getCity(), pool.getCity())) {
            score += SCORE_CITY;
        }
        if (StringUtils.hasText(preference.getScriptType()) && Objects.equals(preference.getScriptType(), pool.getScriptType())) {
            score += SCORE_SCRIPT_TYPE;
        }
        if (isPriceMatched(pool.getPrice(), preference.getPriceMin(), preference.getPriceMax())) {
            score += SCORE_PRICE;
        }
        if (isMemberCountMatched(pool.getMaxMembers(), preference.getMinMembers(), preference.getMaxMembers())) {
            score += SCORE_MEMBER_COUNT;
        }
        if (isTimeSlotMatched(pool.getStartTime(), preference.getTimeSlot())) {
            score += SCORE_TIME_SLOT;
        }
        return score;
    }

    private boolean isTimeSlotMatched(LocalDateTime startTime, String timeSlot) {
        if (startTime == null || !StringUtils.hasText(timeSlot)) return false;
        DayOfWeek day = startTime.getDayOfWeek();
        int hour = startTime.getHour();
        switch (timeSlot) {
            case "WEEKDAY_NIGHT":
                return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY && hour >= 18;
            case "WEEKEND_AFTERNOON":
                return (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) && hour >= 12 && hour < 18;
            case "WEEKEND_NIGHT":
                return (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) && hour >= 18;
            default:
                return false;
        }
    }

    private boolean isPriceMatched(BigDecimal price, BigDecimal priceMin, BigDecimal priceMax) {
        if (price == null || (priceMin == null && priceMax == null)) return false;
        if (priceMin != null && price.compareTo(priceMin) < 0) return false;
        return priceMax == null || price.compareTo(priceMax) <= 0;
    }

    private boolean isMemberCountMatched(Integer maxMembers, Integer minMembers, Integer preferredMaxMembers) {
        if (maxMembers == null || (minMembers == null && preferredMaxMembers == null)) return false;
        if (minMembers != null && maxMembers < minMembers) return false;
        return preferredMaxMembers == null || maxMembers <= preferredMaxMembers;
    }

    private void syncCurrentMembers(CarPool pool, int joinedCount) {
        if (pool.getCurrentMembers() != null && pool.getCurrentMembers() == joinedCount) return;
        pool.setCurrentMembers(joinedCount);
        poolMapper.updateById(pool);
    }

    private void reserveSeat(Long poolId) {
        int updated = poolMapper.update(null, new UpdateWrapper<CarPool>()
                .setSql("current_members = current_members + 1")
                .eq(DbFieldConstant.ID, poolId)
                .eq(DbFieldConstant.STATUS, PoolStatus.OPEN)
                .apply("current_members < max_members"));
        if (updated == 0) {
            throw new BaseException(ErrorConstant.POOL_ALREADY_FULL);
        }
    }

    private void releaseSeat(Long poolId) {
        poolMapper.update(null, new UpdateWrapper<CarPool>()
                .setSql("current_members = GREATEST(current_members - 1, 0)")
                .eq(DbFieldConstant.ID, poolId)
                .in(DbFieldConstant.STATUS, PoolStatus.OPEN, PoolStatus.FULL)
                .apply("current_members > 0"));
        refreshSeatStatus(poolId);
    }

    private void refreshSeatStatus(Long poolId) {
        CarPool latest = poolMapper.selectById(poolId);
        if (latest == null) return;
        if (latest.getCurrentMembers() != null && latest.getMaxMembers() != null
                && latest.getCurrentMembers() >= latest.getMaxMembers()
                && latest.getStatus() == PoolStatus.OPEN) {
            poolMapper.update(null, new UpdateWrapper<CarPool>()
                    .set(DbFieldConstant.STATUS, PoolStatus.FULL)
                    .eq(DbFieldConstant.ID, poolId)
                    .eq(DbFieldConstant.STATUS, PoolStatus.OPEN)
                    .apply("current_members >= max_members"));
        } else if (latest.getCurrentMembers() != null && latest.getMaxMembers() != null
                && latest.getCurrentMembers() < latest.getMaxMembers()
                && latest.getStatus() == PoolStatus.FULL) {
            stateMachine.rollbackToOpen(poolId);
        }
    }

    @Override
    @Transactional
    public ConfirmVO confirm(Long userId, Long poolId, boolean confirmed) {
        try {
            return doConfirm(userId, poolId, confirmed);
        } catch (BaseException e) {
            throw e;
        } catch (Exception e) {
            log.error("confirm error pool={} user={}: ", poolId, userId, e);
            throw new BaseException(ErrorConstant.CONFIRM_FAILED + ": " + e.getMessage());
        }
    }

    private ConfirmVO doConfirm(Long userId, Long poolId, boolean confirmed) {
        String lockKey = RedisKeyConstant.POOL_LOCK_PREFIX + poolId;
        RLock lock = redisson.getLock(lockKey);
        try {
            if (!lock.tryLock(3, 10, TimeUnit.SECONDS)) {
                throw new BaseException(ErrorConstant.SYSTEM_BUSY);
            }

            CarPool pool = poolMapper.selectById(poolId);
            if (pool == null) throw new BaseException(ErrorConstant.POOL_NOT_FOUND);

            PoolMember member = memberMapper.selectOne(new QueryWrapper<PoolMember>()
                    .eq("pool_id", poolId).eq("user_id", userId));
            if (member == null || member.getStatus() != MemberStatus.JOINED) {
                throw new BaseException(ErrorConstant.NOT_POOL_FORMAL_MEMBER);
            }

            int confirmedCount;
            boolean isCompleteConfirm;

            if (pool.getStatus() == PoolStatus.FULL) {
                if (member.getCompletedConfirmTime() == null) {
                    throw new BaseException(ErrorConstant.CONFIRM_NOT_STARTED);
                }
                if (member.getCompletedConfirmed() != ConfirmStatus.UNCONFIRMED) {
                    throw new BaseException(ErrorConstant.ALREADY_CONFIRMED);
                }
                int confirmValue = confirmed ? ConfirmStatus.CONFIRMED : ConfirmStatus.REJECTED;
                member.setCompletedConfirmed(confirmValue);
                member.setCompletedConfirmTime(LocalDateTime.now());
                memberMapper.updateById(member);

                isCompleteConfirm = true;
            } else if (pool.getStatus() == PoolStatus.COMPLETED) {
                if (member.getFinishedConfirmTime() == null) {
                    throw new BaseException(ErrorConstant.CONFIRM_NOT_STARTED);
                }
                if (member.getFinishedConfirmed() != ConfirmStatus.UNCONFIRMED) {
                    throw new BaseException(ErrorConstant.ALREADY_CONFIRMED);
                }
                int confirmValue = confirmed ? ConfirmStatus.CONFIRMED : ConfirmStatus.REJECTED;
                member.setFinishedConfirmed(confirmValue);
                member.setFinishedConfirmTime(LocalDateTime.now());
                memberMapper.updateById(member);

                isCompleteConfirm = false;
            } else {
                throw new BaseException(ErrorConstant.CURRENT_STATUS_NO_CONFIRM_REQUIRED);
            }

            List<PoolMember> allMembers = memberMapper.selectList(new QueryWrapper<PoolMember>()
                    .eq("pool_id", poolId).eq("status", MemberStatus.JOINED));

            if (isCompleteConfirm) {
                confirmedCount = (int) allMembers.stream().filter(poolMember -> poolMember.getCompletedConfirmed() == ConfirmStatus.CONFIRMED).count();
                boolean anyRejected = allMembers.stream().anyMatch(poolMember -> poolMember.getCompletedConfirmed() == ConfirmStatus.REJECTED);
                if (!anyRejected && confirmedCount == allMembers.size()) {
                    stateMachine.toCompleted(poolId);
                    publishPoolEvent(poolId, "POOL_COMPLETED");
                    evictPoolDetail(poolId);
                    return new ConfirmVO(poolId, confirmedCount, allMembers.size(), true);
                }
                publishPoolEvent(poolId, "COMPLETED_CONFIRM_UPDATED");
                evictPoolDetail(poolId);
                return new ConfirmVO(poolId, confirmedCount, allMembers.size(), false);
            } else {
                confirmedCount = (int) allMembers.stream().filter(poolMember -> poolMember.getFinishedConfirmed() == ConfirmStatus.CONFIRMED).count();
                long rejected = allMembers.stream().filter(poolMember -> poolMember.getFinishedConfirmed() == ConfirmStatus.REJECTED).count();

                boolean timeElapsed = pool.getEndTime() != null && LocalDateTime.now().isAfter(pool.getEndTime());
                boolean allConfirmed = confirmedCount == allMembers.size();
                boolean canFinish = timeElapsed ? (confirmedCount > allMembers.size() / 2) : allConfirmed;
                if (canFinish) {
                    stateMachine.toFinished(poolId);
                    publishPoolEvent(poolId, "POOL_FINISHED");
                    evictPoolDetail(poolId);
                    return new ConfirmVO(poolId, confirmedCount, allMembers.size(), true);
                }
                publishPoolEvent(poolId, "FINISHED_CONFIRM_UPDATED");
                evictPoolDetail(poolId);
                return new ConfirmVO(poolId, confirmedCount, allMembers.size(), false);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BaseException(ErrorConstant.SYSTEM_BUSY);
        } finally {
            if (lock.isHeldByCurrentThread()) lock.unlock();
        }
    }

    @Override
    @Transactional
    public ConfirmVO finish(Long userId, Long poolId) {
        CarPool pool = poolMapper.selectById(poolId);
        if (pool == null) throw new BaseException(ErrorConstant.POOL_NOT_FOUND);
        if (!pool.getOwnerId().equals(userId)) throw new BaseException(ErrorConstant.ONLY_OWNER_CAN_FINISH_CONFIRM);
        if (pool.getStatus() != PoolStatus.COMPLETED) throw new BaseException(ErrorConstant.POOL_NOT_COMPLETED);

        List<PoolMember> members = memberMapper.selectList(new QueryWrapper<PoolMember>()
                .eq("pool_id", poolId).eq("status", MemberStatus.JOINED));
        boolean confirmationStarted = members.stream()
                .anyMatch(member -> member.getFinishedConfirmTime() != null);
        boolean anyRejected = members.stream()
                .anyMatch(member -> member.getFinishedConfirmed() == ConfirmStatus.REJECTED);
        if (confirmationStarted && !anyRejected) {
            throw new BaseException(ErrorConstant.CONFIRM_ALREADY_STARTED);
        }
        LocalDateTime now = LocalDateTime.now();
        memberMapper.update(null, new UpdateWrapper<PoolMember>()
                .set("finished_confirmed", ConfirmStatus.UNCONFIRMED)
                .set("finished_confirm_time", now)
                .eq("pool_id", poolId).eq("status", MemberStatus.JOINED));

        publishPoolEvent(poolId, "FINISHED_CONFIRM_STARTED");
        timeoutProducer.send(new TimeoutMessage(TimeoutMessage.FINISHED_CONFIRM, null, poolId, null),
                TimeUnit.HOURS.toMillis(CONFIRM_TIMEOUT_HOURS));
        evictPoolDetail(poolId);
        return new ConfirmVO(poolId, 0, members.size(), false);
    }

    private void publishPoolEvent(Long poolId, String event) {
        try {
            messagingTemplate.convertAndSend("/topic/pool/" + poolId, Map.of(
                    "event", event,
                    "poolId", poolId,
                    "time", LocalDateTime.now().toString()
            ));
        } catch (Exception e) {
            log.warn("publish pool event failed, poolId={}, event={}", poolId, event, e);
        }
    }

    @Override
    public List<PoolMember> getMembers(Long poolId) {
        return memberMapper.selectList(new QueryWrapper<PoolMember>()
                .eq("pool_id", poolId).orderByAsc("join_time"));
    }

    @Override
    public void selectRole(Long userId, Long poolId, String roleName) {
        String hashKey = RedisKeyConstant.POOL_ROLE_PREFIX + poolId;
        Boolean success = stringRedis.opsForHash().putIfAbsent(hashKey, roleName, String.valueOf(userId));
        if (Boolean.FALSE.equals(success)) {
            throw new BaseException(ErrorConstant.ROLE_ALREADY_SELECTED);
        }
        memberMapper.update(null, new UpdateWrapper<PoolMember>()
                .set("selected_role", roleName)
                .eq("pool_id", poolId).eq("user_id", userId));
        evictPoolDetail(poolId);
    }

    @Override
    public List<RoleStatusVO> getRoles(Long poolId) {
        CarPool pool = poolMapper.selectById(poolId);
        if (pool == null || pool.getRoles() == null) return Collections.emptyList();
        List<Map<String, String>> roleList = JSONUtil.toBean(pool.getRoles(),
                new TypeReference<List<Map<String, String>>>() {}, false);

        Map<Object, Object> selected = stringRedis.opsForHash().entries(RedisKeyConstant.POOL_ROLE_PREFIX + poolId);
        return roleList.stream().map(role -> {
            String name = role.get("name");
            boolean isSelected = selected.containsKey(name);
            return new RoleStatusVO(name, role.get("desc"), isSelected,
                    isSelected ? Long.valueOf((String) selected.get(name)) : null);
        }).collect(Collectors.toList());
    }

    @Override
    public List<MemberPoolVO> getMyMemberPools(Long userId) {
        List<PoolMember> members = memberMapper.selectList(new QueryWrapper<PoolMember>()
                .eq(DbFieldConstant.USER_ID, userId)
                .in(DbFieldConstant.STATUS, MemberStatus.PENDING_PAYMENT, MemberStatus.JOINED));
        if (members.isEmpty()) return List.of();

        List<Long> poolIds = members.stream().map(PoolMember::getPoolId).collect(Collectors.toList());
        List<CarPool> pools = poolMapper.selectBatchIds(poolIds);

        Map<Long, CarPool> poolMap = pools.stream().collect(Collectors.toMap(CarPool::getId, pool -> pool));
        return members.stream()
                .filter(member -> poolMap.containsKey(member.getPoolId()))
                .map(member -> {
                    CarPool pool = poolMap.get(member.getPoolId());
                    return new MemberPoolVO(pool.getId(), member.getStatus(), pool.getStatus(),
                            pool.getScriptName(), pool.getStartTime(), pool.getType(), pool.getDeposit(),
                            member.getCompletedConfirmed(), member.getFinishedConfirmed());
                })
                .collect(Collectors.toList());
    }

    private int calculateLeavePenalty(LocalDateTime startTime) {
        if (startTime == null) return 30;
        long hoursUntilStart = ChronoUnit.HOURS.between(LocalDateTime.now(), startTime);
        if (hoursUntilStart > 24) return 10;
        if (hoursUntilStart > 2) return 20;
        return 30;
    }

    private String buildLeavePenaltyReason(LocalDateTime startTime) {
        if (startTime == null) return "跳车扣分";
        long hoursUntilStart = ChronoUnit.HOURS.between(LocalDateTime.now(), startTime);
        if (hoursUntilStart > 24) return "距开团超过24小时跳车";
        if (hoursUntilStart > 2) return "距开团不足24小时跳车";
        return "距开团不足2小时跳车";
    }

    private void sendPoolStartTimeout(CarPool pool) {
        if (pool.getStartTime() == null) return;
        long delayMillis = Duration.between(LocalDateTime.now(), pool.getStartTime()).toMillis();
        try {
            timeoutProducer.send(new TimeoutMessage(TimeoutMessage.POOL_START, null, pool.getId(), null), delayMillis);
        } catch (Exception e) {
            log.warn("send pool start timeout message failed, poolId={}", pool.getId(), e);
        }
    }

    private void evictPoolDetail(Long poolId) {
        stringRedis.delete(RedisKeyConstant.POOL_DETAIL_PREFIX + poolId);
    }

}
