package com.jupin.server.service;

import com.jupin.pojo.dto.PoolCreateRequest;
import com.jupin.pojo.entity.CarPool;
import com.jupin.pojo.entity.PoolMember;
import com.jupin.pojo.vo.ConfirmVO;
import com.jupin.pojo.vo.RoleStatusVO;

import java.math.BigDecimal;
import java.util.List;

public interface PoolService {
    CarPool create(Long userId, PoolCreateRequest request);
    CarPool getDetail(Long poolId);
    List<CarPool> list(String city, String scriptType, Integer type, Integer status,
                        BigDecimal priceMin, BigDecimal priceMax,
                        String startTimeAfter, String startTimeBefore,
                        Boolean recommend, Integer page, Integer size);
    void cancel(Long userId, Long poolId);

    void join(Long userId, Long poolId);
    void leave(Long userId, Long poolId);
    void approve(Long userId, Long poolId, Long targetUserId);
    void reject(Long userId, Long poolId, Long targetUserId);
    List<PoolMember> getMembers(Long poolId);

    void updatePrice(Long userId, Long poolId, BigDecimal price);
    void transferDm(Long userId, Long poolId, Long newDmId);
    void assignDm(Long userId, Long poolId, Long dmId);

    ConfirmVO complete(Long userId, Long poolId);
    ConfirmVO confirm(Long userId, Long poolId, boolean confirmed);
    ConfirmVO finish(Long userId, Long poolId);

    void selectRole(Long userId, Long poolId, String roleName);
    List<RoleStatusVO> getRoles(Long poolId);

    List<CarPool> listShopPools(Long shopId, Integer status, Integer page, Integer size);
}
