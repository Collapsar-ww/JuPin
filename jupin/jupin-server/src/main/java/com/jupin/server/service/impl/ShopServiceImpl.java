package com.jupin.server.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jupin.common.exception.BaseException;
import com.jupin.pojo.dto.ShopCreateRequest;
import com.jupin.pojo.dto.ShopUpdateRequest;
import com.jupin.pojo.entity.Shop;
import com.jupin.pojo.entity.ShopMember;
import com.jupin.server.mapper.ShopMapper;
import com.jupin.server.mapper.ShopMemberMapper;
import com.jupin.server.service.ShopService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ShopServiceImpl implements ShopService {

    private final ShopMapper shopMapper;
    private final ShopMemberMapper shopMemberMapper;

    @Override
    @Transactional
    public Shop create(Long userId, ShopCreateRequest request) {
        Shop shop = Shop.builder()
                .name(request.getName())
                .address(request.getAddress())
                .phone(request.getPhone())
                .logo(request.getLogo())
                .cover(request.getCover())
                .description(request.getDescription())
                .openingHours(request.getOpeningHours())
                .city(request.getCity())
                .status(1)
                .build();
        shopMapper.insert(shop);

        ShopMember owner = ShopMember.builder()
                .shopId(shop.getId())
                .userId(userId)
                .role(1)
                .status(1)
                .build();
        shopMemberMapper.insert(owner);

        return shop;
    }

    @Override
    public Shop getMyShop(Long userId) {
        ShopMember member = shopMemberMapper.selectOne(
                new QueryWrapper<ShopMember>().eq("user_id", userId));
        if (member == null) throw new BaseException("你未加入任何店铺");
        Shop shop = shopMapper.selectById(member.getShopId());
        if (shop == null) throw new BaseException("店铺不存在");
        return shop;
    }

    @Override
    @Transactional
    public Shop update(Long userId, ShopUpdateRequest request) {
        ShopMember member = shopMemberMapper.selectOne(
                new QueryWrapper<ShopMember>().eq("user_id", userId).in("role", 1, 2));
        if (member == null) throw new BaseException("无权限修改店铺信息");
        Shop existing = shopMapper.selectById(member.getShopId());
        BeanUtil.copyProperties(request, existing, CopyOptions.create().ignoreNullValue());
        shopMapper.updateById(existing);
        return existing;
    }

    @Override
    public List<Shop> search(String city, Integer status, Integer page, Integer size) {
        QueryWrapper<Shop> q = new QueryWrapper<Shop>()
                .eq(StringUtils.hasText(city), "city", city)
                .eq(status != null, "status", status)
                .orderByDesc("create_time");
        Page<Shop> p = shopMapper.selectPage(new Page<>(page, size), q);
        return p.getRecords();
    }

    @Override
    @Transactional
    public void join(Long userId, Long shopId) {
        Shop shop = shopMapper.selectById(shopId);
        if (shop == null) throw new BaseException("店铺不存在");

        Long count = shopMemberMapper.selectCount(new QueryWrapper<ShopMember>()
                .eq("shop_id", shopId).eq("user_id", userId));
        if (count > 0) throw new BaseException("你已在店铺中");

        ShopMember member = ShopMember.builder()
                .shopId(shopId)
                .userId(userId)
                .role(3)
                .status(1)
                .build();
        shopMemberMapper.insert(member);
    }

    @Override
    public List<ShopMember> getMembers(Long shopId, Long userId) {
        checkShopMember(shopId, userId);
        return shopMemberMapper.selectList(
                new QueryWrapper<ShopMember>().eq("shop_id", shopId));
    }

    @Override
    @Transactional
    public void addMember(Long shopId, Long userId, Long targetUserId) {
        checkShopManager(shopId, userId);
        Long count = shopMemberMapper.selectCount(new QueryWrapper<ShopMember>()
                .eq("shop_id", shopId).eq("user_id", targetUserId));
        if (count > 0) throw new BaseException("该用户已在店铺中");

        ShopMember member = ShopMember.builder()
                .shopId(shopId)
                .userId(targetUserId)
                .role(3)
                .status(1)
                .build();
        shopMemberMapper.insert(member);
    }

    @Override
    @Transactional
    public void removeMember(Long shopId, Long userId, Long targetUserId) {
        checkShopManager(shopId, userId);
        ShopMember target = shopMemberMapper.selectOne(
                new QueryWrapper<ShopMember>().eq("shop_id", shopId).eq("user_id", targetUserId));
        if (target == null) throw new BaseException("该用户不在店铺中");
        if (target.getRole() == 1) throw new BaseException("不能移除店长");
        shopMemberMapper.deleteById(target.getId());
    }

    @Override
    @Transactional
    public void setMemberRole(Long shopId, Long userId, Long targetUserId, Integer role) {
        ShopMember self = shopMemberMapper.selectOne(
                new QueryWrapper<ShopMember>().eq("shop_id", shopId).eq("user_id", userId));
        if (self == null || self.getRole() != 1) throw new BaseException("仅店长可设置权限");

        ShopMember target = shopMemberMapper.selectOne(
                new QueryWrapper<ShopMember>().eq("shop_id", shopId).eq("user_id", targetUserId));
        if (target == null) throw new BaseException("该用户不在店铺中");
        if (target.getRole() == 1) throw new BaseException("不能修改店长权限");

        target.setRole(role);
        shopMemberMapper.updateById(target);
    }

    private void checkShopMember(Long shopId, Long userId) {
        Long count = shopMemberMapper.selectCount(
                new QueryWrapper<ShopMember>().eq("shop_id", shopId).eq("user_id", userId));
        if (count == 0) throw new BaseException("你不是该店铺成员");
    }

    private void checkShopManager(Long shopId, Long userId) {
        ShopMember member = shopMemberMapper.selectOne(
                new QueryWrapper<ShopMember>().eq("shop_id", shopId).eq("user_id", userId).in("role", 1, 2));
        if (member == null) throw new BaseException("无权限，仅店长或管理员可操作");
    }
}
