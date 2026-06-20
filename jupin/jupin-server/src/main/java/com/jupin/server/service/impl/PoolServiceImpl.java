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

    // 推荐列表的各项打分权重。
    // 城市、剧本类型、时间段等越匹配，推荐分越高。
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
                .status(type == 0 && request.getMaxMembers() != null && request.getMaxMembers() <= 1
                        ? PoolStatus.FULL
                        : PoolStatus.OPEN)
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
        }
        sendPoolStartTimeout(pool);
        return pool;
    }

    @Override
    public CarPool getDetail(Long poolId) {
        // 拼出组局详情缓存 Key：同一个组局只会落到同一个 Redis Key 上。
        String cacheKey = RedisKeyConstant.POOL_DETAIL_PREFIX + poolId;
        // 先查 Redis，这是 Cache Aside 的读路径：缓存命中直接返回，缓存未命中再查 MySQL。
        String cached = stringRedis.opsForValue().get(cacheKey);
        // 如果命中的是空值缓存，说明这个不存在的 poolId 最近已经查过一次。
        // 这里直接返回“不存在”，避免大量无效 ID 每次都穿透到数据库。
        if (RedisKeyConstant.CACHE_NULL.equals(cached)) {
            throw new BaseException(ErrorConstant.POOL_NOT_FOUND);
        }
        // 如果 Redis 里有正常 JSON 字符串，直接反序列化成 CarPool 返回，不再访问数据库。
        if (StringUtils.hasText(cached)) {
            return JSONUtil.toBean(cached, CarPool.class);
        }

        // Redis 未命中时再查 MySQL，MySQL 仍然是组局详情的最终数据源。
        CarPool pool = poolMapper.selectById(poolId);
        if (pool == null) {
            // 数据库也查不到时，写入一个短 TTL 的空值缓存。
            // 这样同一个不存在 ID 在 60 秒内再次被查询时，会被 Redis 拦截，不会反复打到 MySQL。
            stringRedis.opsForValue().set(cacheKey, RedisKeyConstant.CACHE_NULL, POOL_DETAIL_NULL_TTL_SECONDS, TimeUnit.SECONDS);
            throw new BaseException(ErrorConstant.POOL_NOT_FOUND);
        }
        // 查到真实数据后写入 Redis。
        // TTL 使用“固定 10 分钟 + 0~119 秒随机偏移”，避免大量热点 Key 在同一秒集中失效。
        stringRedis.opsForValue().set(cacheKey, JSONUtil.toJsonStr(pool),
                POOL_DETAIL_TTL_MINUTES * 60 + new Random().nextInt(120), TimeUnit.SECONDS);
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

        // 推荐模式：先最多取 MAX_RECOMMEND_FETCH 条候选组局。
        // 然后在内存里根据用户偏好打分、排序、分页。
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
        // 按 poolId 生成分布式锁 Key：同一个组局的加入请求会抢同一把锁。
        // 不同组局的 lockKey 不同，所以它们可以并发加入，互不影响。
        String lockKey = RedisKeyConstant.POOL_LOCK_PREFIX + poolId;
        // 从 Redisson 拿到 RLock 对象；真正加锁时才会访问 Redis。
        RLock lock = redisson.getLock(lockKey);
        try {
            // 最多等待 3 秒获取锁，拿到锁后最多持有 10 秒。
            // 等不到锁说明当前组局加入请求太密集，直接返回系统繁忙，避免线程一直堆积。
            if (!lock.tryLock(3, 10, TimeUnit.SECONDS)) {
                throw new BaseException(ErrorConstant.SYSTEM_BUSY);
            }
            // 拿到锁后重新读取组局数据。
            // 因为加锁前读到的人数可能已经过期，锁内读取才能基于最新座位数判断。
            CarPool pool = poolMapper.selectById(poolId);
            // 组局不存在时终止加入流程。
            if (pool == null) throw new BaseException(ErrorConstant.POOL_NOT_FOUND);
            // 只有开放中的组局允许加入，已取消、已成团、已结束的组局都不能再占座。
            if (pool.getStatus() != PoolStatus.OPEN) throw new BaseException(ErrorConstant.POOL_CANNOT_JOIN);
            // 当前已占用名额达到最大人数时拒绝加入，避免业务层明显超员。
            if (pool.getCurrentMembers() >= pool.getMaxMembers()) throw new BaseException(ErrorConstant.POOL_ALREADY_FULL);

            // 查询当前用户在这个组局里是否已经有成员记录。
            // 这样可以区分“第一次加入”和“之前退出后重新加入”。
            PoolMember existing = memberMapper.selectOne(new QueryWrapper<PoolMember>()
                    .eq(DbFieldConstant.POOL_ID, poolId).eq(DbFieldConstant.USER_ID, userId));
            if (existing != null) {
                // JOINED、PENDING_PAYMENT、PENDING_REVIEW 都表示用户已经占用一个名额。
                // 这些状态下重复点击加入，需要直接拒绝，避免重复占用名额或重复申请。
                if (existing.getStatus() == MemberStatus.JOINED
                        || existing.getStatus() == MemberStatus.PENDING_PAYMENT
                        || existing.getStatus() == MemberStatus.PENDING_REVIEW) {
                    throw new BaseException(ErrorConstant.ALREADY_IN_POOL_OR_PENDING);
                }
                // 加入阶段就占用名额。
                // 这条 SQL 在 MySQL 内部完成 current_members + 1 和 current_members < max_members 判断。
                occupyPoolSlot(poolId);
                // 历史记录存在但不是有效占位状态时，复用原成员记录。
                // 免审核局进入待支付，审核局进入待审核。
                int newStatus = pool.getJoinType() == 1 ? MemberStatus.PENDING_PAYMENT : MemberStatus.PENDING_REVIEW;
                existing.setStatus(newStatus);
                // 更新加入时间，表示这是一次新的加入动作。
                existing.setJoinTime(LocalDateTime.now());
                // 清空离开时间，避免旧的退出时间影响当前成员状态展示。
                existing.setLeaveTime(null);
                // 按主键更新成员记录，保留原来的成员 id。
                memberMapper.updateById(existing);
            } else {
                // 加入阶段就占用名额。
                // 如果并发下名额已满，这里会更新 0 行并抛出已满异常。
                occupyPoolSlot(poolId);
                // 用户从未加入过这个组局时，新建成员记录。
                PoolMember member = PoolMember.builder()
                        // 关联当前组局。
                        .poolId(poolId)
                        // 关联当前用户。
                        .userId(userId)
                        // 默认普通玩家角色，后续可以再选择具体剧本角色。
                        .role(0)
                        // 根据组局加入方式决定初始状态：免审核则待支付，需审核则待审核。
                        .status(pool.getJoinType() == 1 ? MemberStatus.PENDING_PAYMENT : MemberStatus.PENDING_REVIEW)
                        // 记录加入动作发生时间。
                        .joinTime(LocalDateTime.now())
                        .build();
                // 插入成员表，完成业务上的“加入/申请加入”。
                memberMapper.insert(member);
            }
            // 成员状态变化后删除组局详情缓存。
            // 下一次读取会重新查 MySQL 并回填 Redis，避免用户看到旧的座位信息。
            evictPoolDetail(poolId);
        } catch (InterruptedException e) {
            // tryLock 等待期间线程被中断时，恢复中断标记，交给上层线程池感知。
            Thread.currentThread().interrupt();
            throw new BaseException(ErrorConstant.SYSTEM_BUSY);
        } finally {
            // 解锁前确认这把锁仍然属于当前线程。
            // 如果锁已经过期并被其他线程拿到，当前线程不能误删别人的锁。
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
            boolean occupied = isOccupyingMember(member.getStatus());
            member.setStatus(MemberStatus.LEFT);
            member.setLeaveTime(LocalDateTime.now());
            memberMapper.updateById(member);

            if (occupied) {
                releasePoolSlot(poolId);
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

    private void occupyPoolSlot(Long poolId) {
        int rows = poolMapper.update(null, new UpdateWrapper<CarPool>()
                .setSql("current_members = current_members + 1")
                .eq(DbFieldConstant.ID, poolId)
                .eq(DbFieldConstant.STATUS, PoolStatus.OPEN)
                .apply("current_members < max_members"));
        if (rows == 0) {
            throw new BaseException(ErrorConstant.POOL_ALREADY_FULL);
        }
        poolMapper.update(null, new UpdateWrapper<CarPool>()
                .set(DbFieldConstant.STATUS, PoolStatus.FULL)
                .eq(DbFieldConstant.ID, poolId)
                .eq(DbFieldConstant.STATUS, PoolStatus.OPEN)
                .apply("current_members >= max_members"));
    }

    private void releasePoolSlot(Long poolId) {
        poolMapper.update(null, new UpdateWrapper<CarPool>()
                .setSql("current_members = GREATEST(current_members - 1, 0)")
                .eq(DbFieldConstant.ID, poolId)
                .in(DbFieldConstant.STATUS, PoolStatus.OPEN, PoolStatus.FULL)
                .apply("current_members > 0"));
        stateMachine.rollbackToOpen(poolId);
    }

    private boolean isOccupyingMember(Integer status) {
        return status != null
                && (status == MemberStatus.JOINED
                || status == MemberStatus.PENDING_PAYMENT
                || status == MemberStatus.PENDING_REVIEW);
    }

    @Override
    @Transactional
    public void approve(Long userId, Long poolId, Long targetUserId) {
        CarPool pool = poolMapper.selectById(poolId);
        if (pool == null || !pool.getOwnerId().equals(userId)) {
            throw new BaseException(ErrorConstant.NO_PERMISSION_TO_REVIEW);
        }
        memberMapper.update(null, new UpdateWrapper<PoolMember>()
                .set("status", MemberStatus.PENDING_PAYMENT)
                .eq("pool_id", poolId).eq("user_id", targetUserId).eq("status", MemberStatus.PENDING_REVIEW));
        evictPoolDetail(poolId);
    }

    @Override
    @Transactional
    public void reject(Long userId, Long poolId, Long targetUserId) {
        CarPool pool = poolMapper.selectById(poolId);
        if (pool == null || !pool.getOwnerId().equals(userId)) {
            throw new BaseException(ErrorConstant.NO_PERMISSION_TO_REVIEW);
        }
        int rows = memberMapper.update(null, new UpdateWrapper<PoolMember>()
                .set("status", MemberStatus.REJECTED)
                .eq("pool_id", poolId).eq("user_id", targetUserId).eq("status", MemberStatus.PENDING_REVIEW));
        if (rows > 0) {
            releasePoolSlot(poolId);
        }
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
            syncCurrentMembers(pool, members.size());
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
