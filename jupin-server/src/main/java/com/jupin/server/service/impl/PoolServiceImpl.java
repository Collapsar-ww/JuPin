package com.jupin.server.service.impl;

import cn.hutool.core.lang.TypeReference;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jupin.common.constant.ConfirmStatus;
import com.jupin.common.constant.MemberStatus;
import com.jupin.common.constant.PoolStatus;
import com.jupin.common.exception.BaseException;
import com.jupin.pojo.dto.PoolCreateRequest;
import com.jupin.pojo.entity.*;
import com.jupin.pojo.vo.ConfirmVO;
import com.jupin.pojo.vo.RoleStatusVO;
import com.jupin.server.mapper.*;
import com.jupin.server.service.PoolService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
    private final PoolStateMachine stateMachine;
    private final RedissonClient redisson;
    private final StringRedisTemplate stringRedis;

    private static final String LOCK_KEY_PREFIX = "pool:lock:";
    private static final String ROLE_KEY_PREFIX = "pool:roles:";

    @Override
    @Transactional
    public CarPool create(Long userId, PoolCreateRequest request) {
        User owner = userMapper.selectById(userId);
        if (owner.getCreditScore() < 60) {
            throw new BaseException("信用分过低，无法发布拼车");
        }

        Integer type = request.getType() != null ? request.getType() : 0;

        if (type == 1) {
            if (request.getShopId() == null) throw new BaseException("店家局必须指定店铺");
            Long count = shopMemberMapper.selectCount(new QueryWrapper<ShopMember>()
                    .eq("shop_id", request.getShopId()).eq("user_id", userId).in("role", 1, 2));
            if (count == 0) throw new BaseException("你不是该店铺的店长或管理员");
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
                .dmId(request.getDmId() != null ? request.getDmId() : userId)
                .currentMembers(0)
                .status(PoolStatus.OPEN)
                .build();
        poolMapper.insert(pool);

        PoolMember ownerMember = PoolMember.builder()
                .poolId(pool.getId())
                .userId(userId)
                .role(1)
                .status(MemberStatus.PENDING_PAYMENT)
                .joinTime(LocalDateTime.now())
                .build();
        memberMapper.insert(ownerMember);
        return pool;
    }

    @Override
    public CarPool getDetail(Long poolId) {
        CarPool pool = poolMapper.selectById(poolId);
        if (pool == null) throw new BaseException("拼车不存在");
        return pool;
    }

    @Override
    public List<CarPool> list(String city, String scriptType, Integer type, Integer status,
                               BigDecimal priceMin, BigDecimal priceMax,
                               String startTimeAfter, String startTimeBefore,
                               Boolean recommend, Integer page, Integer size) {
        QueryWrapper<CarPool> q = new QueryWrapper<CarPool>();
        q.in("status", PoolStatus.OPEN, PoolStatus.FULL);
        if (StringUtils.hasText(city)) q.eq("city", city);
        if (StringUtils.hasText(scriptType)) q.eq("script_type", scriptType);
        if (type != null) q.eq("type", type);
        if (status != null) q.eq("status", status);
        if (priceMin != null) q.ge("price", priceMin);
        if (priceMax != null) q.le("price", priceMax);
        if (StringUtils.hasText(startTimeAfter)) q.ge("start_time", startTimeAfter);
        if (StringUtils.hasText(startTimeBefore)) q.le("start_time", startTimeBefore);
        q.orderByDesc("create_time");

        Page<CarPool> p = poolMapper.selectPage(new Page<>(page, size), q);
        return p.getRecords();
    }

    @Override
    public List<CarPool> listShopPools(Long shopId, Integer status, Integer page, Integer size) {
        QueryWrapper<CarPool> q = new QueryWrapper<CarPool>()
                .eq("shop_id", shopId)
                .eq(status != null, "status", status)
                .orderByDesc("create_time");
        Page<CarPool> p = poolMapper.selectPage(new Page<>(page, size), q);
        return p.getRecords();
    }

    @Override
    @Transactional
    public void cancel(Long userId, Long poolId) {
        stateMachine.toCancelled(poolId, userId);
    }

    @Override
    @Transactional
    public void join(Long userId, Long poolId) {
        String lockKey = LOCK_KEY_PREFIX + poolId;
        RLock lock = redisson.getLock(lockKey);
        try {
            if (!lock.tryLock(3, 10, TimeUnit.SECONDS)) {
                throw new BaseException("系统繁忙，请稍后再试");
            }
            CarPool pool = poolMapper.selectById(poolId);
            if (pool == null) throw new BaseException("拼车不存在");
            if (pool.getStatus() != PoolStatus.OPEN) throw new BaseException("该拼车已无法加入");
            if (pool.getCurrentMembers() >= pool.getMaxMembers()) throw new BaseException("拼车已满员");

            Long count = memberMapper.selectCount(new QueryWrapper<PoolMember>()
                    .eq("pool_id", poolId).eq("user_id", userId)
                    .in("status", MemberStatus.PENDING_REVIEW, MemberStatus.PENDING_PAYMENT, MemberStatus.JOINED));
            if (count > 0) throw new BaseException("你已在拼车中或等待审核");

            PoolMember member = PoolMember.builder()
                    .poolId(poolId)
                    .userId(userId)
                    .role(0)
                    .status(pool.getJoinType() == 1 ? MemberStatus.PENDING_PAYMENT : MemberStatus.PENDING_REVIEW)
                    .joinTime(LocalDateTime.now())
                    .build();
            memberMapper.insert(member);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BaseException("系统繁忙，请稍后再试");
        } finally {
            if (lock.isHeldByCurrentThread()) lock.unlock();
        }
    }

    @Override
    @Transactional
    public void leave(Long userId, Long poolId) {
        CarPool pool = poolMapper.selectById(poolId);
        if (pool == null) throw new BaseException("拼车不存在");

        PoolMember member = memberMapper.selectOne(new QueryWrapper<PoolMember>()
                .eq("pool_id", poolId).eq("user_id", userId));
        if (member == null) throw new BaseException("你不在该拼车中");

        if (pool.getStatus() == PoolStatus.OPEN || pool.getStatus() == PoolStatus.FULL) {
            member.setStatus(MemberStatus.LEFT);
            member.setLeaveTime(LocalDateTime.now());
            memberMapper.updateById(member);

            pool.setCurrentMembers(Math.max(0, pool.getCurrentMembers() - 1));
            poolMapper.updateById(pool);

            if (pool.getStatus() == PoolStatus.FULL) {
                stateMachine.rollbackToOpen(poolId);
            }
        } else if (pool.getStatus() == PoolStatus.COMPLETED) {
            member.setStatus(MemberStatus.LEFT);
            member.setLeaveTime(LocalDateTime.now());
            memberMapper.updateById(member);
        } else {
            throw new BaseException("当前状态下不允许退出");
        }
    }

    @Override
    @Transactional
    public void approve(Long userId, Long poolId, Long targetUserId) {
        CarPool pool = poolMapper.selectById(poolId);
        if (pool == null || !pool.getOwnerId().equals(userId)) {
            throw new BaseException("无权限审核");
        }
        memberMapper.update(null, new UpdateWrapper<PoolMember>()
                .set("status", MemberStatus.PENDING_PAYMENT)
                .eq("pool_id", poolId).eq("user_id", targetUserId).eq("status", MemberStatus.PENDING_REVIEW));
    }

    @Override
    @Transactional
    public void reject(Long userId, Long poolId, Long targetUserId) {
        CarPool pool = poolMapper.selectById(poolId);
        if (pool == null || !pool.getOwnerId().equals(userId)) {
            throw new BaseException("无权限审核");
        }
        memberMapper.update(null, new UpdateWrapper<PoolMember>()
                .set("status", MemberStatus.REJECTED)
                .eq("pool_id", poolId).eq("user_id", targetUserId).eq("status", MemberStatus.PENDING_REVIEW));
    }

    @Override
    @Transactional
    public void updatePrice(Long userId, Long poolId, BigDecimal price) {
        CarPool pool = poolMapper.selectById(poolId);
        if (pool == null || !pool.getOwnerId().equals(userId)) {
            throw new BaseException("无权限修改价格");
        }
        if (pool.getStatus() != PoolStatus.OPEN && pool.getStatus() != PoolStatus.FULL) {
            throw new BaseException("成团后不能修改价格");
        }
        pool.setPrice(price);
        poolMapper.updateById(pool);
    }

    @Override
    @Transactional
    public void transferDm(Long userId, Long poolId, Long newDmId) {
        CarPool pool = poolMapper.selectById(poolId);
        if (pool == null || !pool.getOwnerId().equals(userId)) {
            throw new BaseException("无权限转让DM");
        }
        if (pool.getType() != 0) throw new BaseException("仅玩家局可转让DM");
        if (pool.getStatus() != PoolStatus.OPEN && pool.getStatus() != PoolStatus.FULL) {
            throw new BaseException("成团后不能转让DM");
        }
        User newDm = userMapper.selectById(newDmId);
        if (newDm == null) throw new BaseException("用户不存在");

        Long count = memberMapper.selectCount(new QueryWrapper<PoolMember>()
                .eq("pool_id", poolId).eq("user_id", newDmId).eq("status", MemberStatus.JOINED));
        if (count == 0) throw new BaseException("新DM不是拼车成员");

        pool.setDmId(newDmId);
        poolMapper.updateById(pool);
    }

    @Override
    @Transactional
    public void assignDm(Long userId, Long poolId, Long dmId) {
        CarPool pool = poolMapper.selectById(poolId);
        if (pool == null) throw new BaseException("拼车不存在");
        if (pool.getType() != 1) throw new BaseException("仅店家局可指派DM");

        Long count = shopMemberMapper.selectCount(new QueryWrapper<ShopMember>()
                .eq("shop_id", pool.getShopId()).eq("user_id", userId).in("role", 1, 2));
        if (count == 0) throw new BaseException("无权限指派DM");

        count = shopMemberMapper.selectCount(new QueryWrapper<ShopMember>()
                .eq("shop_id", pool.getShopId()).eq("user_id", dmId));
        if (count == 0) throw new BaseException("该用户不是店铺成员");

        pool.setDmId(dmId);
        poolMapper.updateById(pool);
    }

    @Override
    @Transactional
    public ConfirmVO complete(Long userId, Long poolId) {
        CarPool pool = poolMapper.selectById(poolId);
        if (pool == null) throw new BaseException("拼车不存在");
        if (!pool.getOwnerId().equals(userId)) throw new BaseException("仅发布人可发起确认");
        if (pool.getStatus() != PoolStatus.FULL) throw new BaseException("拼车未满员");
        if (pool.getDmId() == null) throw new BaseException("未指定DM");

        memberMapper.update(null, new UpdateWrapper<PoolMember>()
                .set("completed_confirmed", ConfirmStatus.UNCONFIRMED)
                .set("completed_confirm_time", null)
                .eq("pool_id", poolId));

        List<PoolMember> members = memberMapper.selectList(new QueryWrapper<PoolMember>()
                .eq("pool_id", poolId).eq("status", MemberStatus.JOINED));
        long total = members.size();

        return new ConfirmVO(poolId, 0, total, false);
    }

    @Override
    @Transactional
    public ConfirmVO confirm(Long userId, Long poolId, boolean confirmed) {
        String lockKey = LOCK_KEY_PREFIX + poolId;
        RLock lock = redisson.getLock(lockKey);
        try {
            if (!lock.tryLock(3, 10, TimeUnit.SECONDS)) {
                throw new BaseException("系统繁忙，请稍后再试");
            }

            CarPool pool = poolMapper.selectById(poolId);
            if (pool == null) throw new BaseException("拼车不存在");

            PoolMember member = memberMapper.selectOne(new QueryWrapper<PoolMember>()
                    .eq("pool_id", poolId).eq("user_id", userId));
            if (member == null || member.getStatus() != MemberStatus.JOINED) {
                throw new BaseException("你不是该拼车的正式成员");
            }

            int confirmedCount;
            boolean isCompleteConfirm;

            if (pool.getStatus() == PoolStatus.FULL) {
                if (member.getCompletedConfirmed() != ConfirmStatus.UNCONFIRMED) {
                    throw new BaseException("你已经确认过");
                }
                int confirmValue = confirmed ? ConfirmStatus.CONFIRMED : ConfirmStatus.REJECTED;
                member.setCompletedConfirmed(confirmValue);
                member.setCompletedConfirmTime(LocalDateTime.now());
                memberMapper.updateById(member);

                isCompleteConfirm = true;
            } else if (pool.getStatus() == PoolStatus.COMPLETED) {
                if (member.getFinishedConfirmed() != ConfirmStatus.UNCONFIRMED) {
                    throw new BaseException("你已经确认过");
                }
                int confirmValue = confirmed ? ConfirmStatus.CONFIRMED : ConfirmStatus.REJECTED;
                member.setFinishedConfirmed(confirmValue);
                member.setFinishedConfirmTime(LocalDateTime.now());
                memberMapper.updateById(member);

                isCompleteConfirm = false;
            } else {
                throw new BaseException("当前状态无需确认");
            }

            List<PoolMember> allMembers = memberMapper.selectList(new QueryWrapper<PoolMember>()
                    .eq("pool_id", poolId).eq("status", MemberStatus.JOINED));

            if (isCompleteConfirm) {
                confirmedCount = (int) allMembers.stream().filter(m -> m.getCompletedConfirmed() == ConfirmStatus.CONFIRMED).count();
                boolean anyRejected = allMembers.stream().anyMatch(m -> m.getCompletedConfirmed() == ConfirmStatus.REJECTED);
                if (!anyRejected && confirmedCount == allMembers.size()) {
                    stateMachine.toCompleted(poolId);
                    return new ConfirmVO(poolId, confirmedCount, allMembers.size(), true);
                }
                return new ConfirmVO(poolId, confirmedCount, allMembers.size(), false);
            } else {
                confirmedCount = (int) allMembers.stream().filter(m -> m.getFinishedConfirmed() == ConfirmStatus.CONFIRMED).count();
                long rejected = allMembers.stream().filter(m -> m.getFinishedConfirmed() == ConfirmStatus.REJECTED).count();

                boolean timeElapsed = pool.getEndTime() != null && LocalDateTime.now().isAfter(pool.getEndTime());
                boolean allConfirmed = confirmedCount == allMembers.size();
                boolean canFinish = timeElapsed ? (confirmedCount > allMembers.size() / 2) : allConfirmed;
                if (canFinish) {
                    stateMachine.toFinished(poolId);
                    return new ConfirmVO(poolId, confirmedCount, allMembers.size(), true);
                }
                return new ConfirmVO(poolId, confirmedCount, allMembers.size(), false);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BaseException("系统繁忙，请稍后再试");
        } finally {
            if (lock.isHeldByCurrentThread()) lock.unlock();
        }
    }

    @Override
    @Transactional
    public ConfirmVO finish(Long userId, Long poolId) {
        CarPool pool = poolMapper.selectById(poolId);
        if (pool == null) throw new BaseException("拼车不存在");
        if (!pool.getOwnerId().equals(userId)) throw new BaseException("仅发布人可发起完成确认");
        if (pool.getStatus() != PoolStatus.COMPLETED) throw new BaseException("拼车未成功");

        memberMapper.update(null, new UpdateWrapper<PoolMember>()
                .set("finished_confirmed", ConfirmStatus.UNCONFIRMED)
                .set("finished_confirm_time", null)
                .eq("pool_id", poolId));

        List<PoolMember> members = memberMapper.selectList(new QueryWrapper<PoolMember>()
                .eq("pool_id", poolId).eq("status", MemberStatus.JOINED));
        long total = members.size();

        return new ConfirmVO(poolId, 0, total, false);
    }

    @Override
    public List<PoolMember> getMembers(Long poolId) {
        return memberMapper.selectList(new QueryWrapper<PoolMember>()
                .eq("pool_id", poolId).orderByAsc("join_time"));
    }

    @Override
    public void selectRole(Long userId, Long poolId, String roleName) {
        String hashKey = ROLE_KEY_PREFIX + poolId;
        Boolean success = stringRedis.opsForHash().putIfAbsent(hashKey, roleName, String.valueOf(userId));
        if (Boolean.FALSE.equals(success)) {
            throw new BaseException("该角色已被选择");
        }
        memberMapper.update(null, new UpdateWrapper<PoolMember>()
                .set("selected_role", roleName)
                .eq("pool_id", poolId).eq("user_id", userId));
    }

    @Override
    public List<RoleStatusVO> getRoles(Long poolId) {
        CarPool pool = poolMapper.selectById(poolId);
        if (pool == null || pool.getRoles() == null) return Collections.emptyList();
        List<Map<String, String>> roleList = JSONUtil.toBean(pool.getRoles(),
                new TypeReference<List<Map<String, String>>>() {}, false);

        Map<Object, Object> selected = stringRedis.opsForHash().entries(ROLE_KEY_PREFIX + poolId);
        return roleList.stream().map(r -> {
            String name = r.get("name");
            boolean isSelected = selected.containsKey(name);
            return new RoleStatusVO(name, r.get("desc"), isSelected,
                    isSelected ? Long.valueOf((String) selected.get(name)) : null);
        }).collect(Collectors.toList());
    }
}
