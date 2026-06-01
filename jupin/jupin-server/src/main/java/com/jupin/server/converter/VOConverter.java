package com.jupin.server.converter;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.jupin.common.constant.DbFieldConstant;
import cn.hutool.core.bean.BeanUtil;
import com.jupin.pojo.entity.*;
import com.jupin.pojo.vo.*;
import com.jupin.server.mapper.OrderMapper;
import com.jupin.server.mapper.ShopMapper;
import com.jupin.server.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class VOConverter {

    private final UserMapper userMapper;
    private final ShopMapper shopMapper;
    private final OrderMapper orderMapper;

    public PoolVO toPoolVO(CarPool pool) {
        PoolVO vo = BeanUtil.copyProperties(pool, PoolVO.class);
        User owner = userMapper.selectById(pool.getOwnerId());
        if (owner != null) {
            vo.setOwnerNickname(owner.getNickname());
            vo.setOwnerAvatar(owner.getAvatar());
        }
        if (pool.getDmId() != null) {
            User dm = userMapper.selectById(pool.getDmId());
            if (dm != null) vo.setDmNickname(dm.getNickname());
        }
        if (pool.getShopId() != null) {
            Shop shop = shopMapper.selectById(pool.getShopId());
            if (shop != null) vo.setShopName(shop.getName());
        }
        return vo;
    }

    public PoolVO toPoolVOWithMembers(CarPool pool, List<PoolMember> members) {
        PoolVO vo = toPoolVO(pool);
        if (members != null) {
            vo.setMembers(members.stream().map(this::toMemberVO).collect(Collectors.toList()));
            vo.setCompletedConfirmStarted(members.stream()
                    .anyMatch(member -> member.getCompletedConfirmTime() != null)
                    && members.stream().noneMatch(member -> Objects.equals(member.getCompletedConfirmed(), 2)));
            vo.setFinishedConfirmStarted(members.stream()
                    .anyMatch(member -> member.getFinishedConfirmTime() != null)
                    && members.stream().noneMatch(member -> Objects.equals(member.getFinishedConfirmed(), 2)));
        }
        return vo;
    }

    public MemberVO toMemberVO(PoolMember member) {
        MemberVO vo = BeanUtil.copyProperties(member, MemberVO.class);
        User user = userMapper.selectById(member.getUserId());
        if (user != null) {
            vo.setNickname(user.getNickname());
            vo.setAvatar(user.getAvatar());
            vo.setGender(user.getGender());
            vo.setCreditScore(user.getCreditScore());
        }
        vo.setDepositOrderStatus(resolveOrderStatus(member, 0));
        vo.setRemainingOrderStatus(resolveOrderStatus(member, 1));
        return vo;
    }

    private Integer resolveOrderStatus(PoolMember member, int type) {
        Order order = orderMapper.selectOne(new QueryWrapper<Order>()
                .eq(DbFieldConstant.POOL_ID, member.getPoolId())
                .eq(DbFieldConstant.USER_ID, member.getUserId())
                .eq(DbFieldConstant.TYPE, type)
                .orderByDesc(DbFieldConstant.CREATE_TIME)
                .last("LIMIT 1"));
        return order == null ? null : order.getStatus();
    }

    public ReviewVO toReviewVO(Review review) {
        ReviewVO vo = BeanUtil.copyProperties(review, ReviewVO.class);
        vo.setToUserId(review.getTargetId());
        User from = userMapper.selectById(review.getFromUserId());
        if (from != null) vo.setFromNickname(from.getNickname());
        if (review.getType() == 1) {
            User to = userMapper.selectById(review.getTargetId());
            if (to != null) vo.setToNickname(to.getNickname());
        }
        return vo;
    }

    public ShopMemberVO toShopMemberVO(ShopMember member) {
        ShopMemberVO vo = BeanUtil.copyProperties(member, ShopMemberVO.class);
        User user = userMapper.selectById(member.getUserId());
        if (user != null) {
            vo.setNickname(user.getNickname());
            vo.setAvatar(user.getAvatar());
        }
        return vo;
    }
}
