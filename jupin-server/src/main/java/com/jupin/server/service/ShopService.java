package com.jupin.server.service;

import com.jupin.pojo.dto.ShopCreateRequest;
import com.jupin.pojo.dto.ShopUpdateRequest;
import com.jupin.pojo.entity.Shop;
import com.jupin.pojo.entity.ShopMember;

import java.util.List;

public interface ShopService {
    Shop create(Long userId, ShopCreateRequest request);
    Shop getMyShop(Long userId);
    Shop update(Long userId, ShopUpdateRequest request);
    List<Shop> search(String city, Integer status, Integer page, Integer size);
    void join(Long userId, Long shopId);
    List<ShopMember> getMembers(Long shopId, Long userId);
    void addMember(Long shopId, Long userId, Long targetUserId);
    void removeMember(Long shopId, Long userId, Long targetUserId);
    void setMemberRole(Long shopId, Long userId, Long targetUserId, Integer role);
}
