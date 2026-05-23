package com.jupin.server.converter;

import cn.hutool.core.bean.BeanUtil;
import com.jupin.pojo.entity.*;
import com.jupin.pojo.vo.*;
import com.jupin.server.mapper.ShopMapper;
import com.jupin.server.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class VOConverter {

    private final UserMapper userMapper;
    private final ShopMapper shopMapper;

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
        return vo;
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
