package com.jupin.server.service.impl;

import cn.hutool.core.lang.TypeReference;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jupin.common.constant.ConfirmStatus;
import com.jupin.common.constant.DbFieldConstant;
import com.jupin.common.constant.ErrorConstant;
import com.jupin.common.constant.MemberStatus;
import com.jupin.common.constant.OrderStatus;
import com.jupin.common.constant.PoolStatus;
import com.jupin.common.constant.RedisKeyConstant;
import com.jupin.common.exception.BaseException;
import com.jupin.pojo.dto.PoolCreateRequest;
import com.jupin.pojo.entity.*;
import com.jupin.pojo.vo.ConfirmVO;
import com.jupin.pojo.vo.RoleStatusVO;
import com.jupin.server.mapper.*;
import com.jupin.server.service.CreditService;
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
    private final OrderMapper orderMapper;
    private final PoolStateMachine stateMachine;
    private final RedissonClient redisson;
    private final StringRedisTemplate stringRedis;
    private final CreditService creditService;

    @Override
    @Transactional
    public CarPool create(Long userId, PoolCreateRequest request) {
        User owner = userMapper.selectById(userId);
        if (owner.getCreditScore() < 60) {
            throw new BaseException("信用分过低，无法发布拼车");
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
                if (scriptCount == 0) throw new BaseException(ErrorConstant.SHOP_SCRIPT_NOT_IN_LIBRARY);
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
        if (pool == null) throw new BaseException(ErrorConstant.POOL_NOT_FOUND);
        return pool;
    }

    @Override
    public List<CarPool> list(String city, String scriptType, Integer type, Integer status,
                               BigDecimal priceMin, BigDecimal priceMax,
                               String startTimeAfter, String startTimeBefore,
                               Boolean recommend, Integer page, Integer size) {
        QueryWrapper<CarPool> q = new QueryWrapper<CarPool>();
        q.in(DbFieldConstant.STATUS, PoolStatus.OPEN, PoolStatus.FULL);
        if (StringUtils.hasText(city)) q.eq(DbFieldConstant.CITY, city);
        if (StringUtils.hasText(scriptType)) q.eq(DbFieldConstant.SCRIPT_TYPE, scriptType);
        if (type != null) q.eq(DbFieldConstant.TYPE, type);
        if (status != null) q.eq(DbFieldConstant.STATUS, status);
        if (priceMin != null) q.ge("price", priceMin);
        if (priceMax != null) q.le("price", priceMax);
        if (StringUtils.hasText(startTimeAfter)) q.ge("start_time", startTimeAfter);
        if (StringUtils.hasText(startTimeBefore)) q.le("start_time", startTimeBefore);
        q.orderByDesc(DbFieldConstant.CREATE_TIME);

        Page<CarPool> p = poolMapper.selectPage(new Page<>(page, size), q);
        return p.getRecords();
    }

    @Override
    public List<CarPool> listShopPools(Long shopId, Integer status, Integer page, Integer size) {
        QueryWrapper<CarPool> q = new QueryWrapper<CarPool>()
                .eq(DbFieldConstant.SHOP_ID, shopId)
                .eq(status != null, DbFieldConstant.STATUS, status)
                .orderByDesc(DbFieldConstant.CREATE_TIME);
        Page<CarPool> p = poolMapper.selectPage(new Page<>(page, size), q);
        return p.getRecords();
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
                existing.setStatus(newStatus);
                existing.setJoinTime(LocalDateTime.now());
                existing.setLeaveTime(null);
                memberMapper.updateById(existing);
            } else {
                PoolMember member = PoolMember.builder()
                        .poolId(poolId)
                        .userId(userId)
                        .role(0)
                        .status(pool.getJoinType() == 1 ? MemberStatus.PENDING_PAYMENT : MemberStatus.PENDING_REVIEW)
                        .joinTime(LocalDateTime.now())
                        .build();
                memberMapper.insert(member);
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
    public void leave(Long userId, Long poolId) {
        try {
            doLeave(userId, poolId);
        } catch (BaseException e) {
            throw e;
        } catch (Exception e) {
            log.error("leave error pool={} user={}: ", poolId, userId, e);
            throw new BaseException("跳车失败: " + e.getMessage());
        }
    }

    private void doLeave(Long userId, Long poolId) {
        CarPool pool = poolMapper.selectById(poolId);
        if (pool == null) throw new BaseException(ErrorConstant.POOL_NOT_FOUND);

        PoolMember member = memberMapper.selectOne(new QueryWrapper<PoolMember>()
                .eq(DbFieldConstant.POOL_ID, poolId).eq(DbFieldConstant.USER_ID, userId));
        if (member == null) throw new BaseException(ErrorConstant.NOT_IN_POOL);

        if (pool.getStatus() == PoolStatus.OPEN || pool.getStatus() == PoolStatus.FULL) {
            boolean joined = member.getStatus() == MemberStatus.JOINED;
            member.setStatus(MemberStatus.LEFT);
            member.setLeaveTime(LocalDateTime.now());
            memberMapper.updateById(member);

            if (joined) {
                pool.setCurrentMembers(Math.max(0, pool.getCurrentMembers() - 1));
                poolMapper.updateById(pool);

                if (pool.getStatus() == PoolStatus.FULL) {
                    stateMachine.rollbackToOpen(poolId);
                }
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
        try {
            return doConfirm(userId, poolId, confirmed);
        } catch (BaseException e) {
            throw e;
        } catch (Exception e) {
            log.error("confirm error pool={} user={}: ", poolId, userId, e);
            throw new BaseException("确认失败: " + e.getMessage());
        }
    }

    private ConfirmVO doConfirm(Long userId, Long poolId, boolean confirmed) {
        String lockKey = RedisKeyConstant.POOL_LOCK_PREFIX + poolId;
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
        String hashKey = RedisKeyConstant.POOL_ROLE_PREFIX + poolId;
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

        Map<Object, Object> selected = stringRedis.opsForHash().entries(RedisKeyConstant.POOL_ROLE_PREFIX + poolId);
        return roleList.stream().map(r -> {
            String name = r.get("name");
            boolean isSelected = selected.containsKey(name);
            return new RoleStatusVO(name, r.get("desc"), isSelected,
                    isSelected ? Long.valueOf((String) selected.get(name)) : null);
        }).collect(Collectors.toList());
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
}
