package com.jupin.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.jupin.common.constant.PoolStatus;
import com.jupin.common.constant.ErrorConstant;
import com.jupin.common.exception.BaseException;
import com.jupin.pojo.entity.CarPool;
import com.jupin.server.mapper.PoolMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PoolStateMachine {

    private final PoolMapper poolMapper;

    public void toFull(Long poolId) {
        int rows = poolMapper.update(null, new UpdateWrapper<CarPool>()
                .set("status", PoolStatus.FULL)
                .eq("id", poolId).eq("status", PoolStatus.OPEN));
        if (rows == 0) throw new BaseException(ErrorConstant.POOL_STATUS_ABNORMAL_CANNOT_SET_FULL);
    }

    public void toCompleted(Long poolId) {
        int rows = poolMapper.update(null, new UpdateWrapper<CarPool>()
                .set("status", PoolStatus.COMPLETED)
                .eq("id", poolId).eq("status", PoolStatus.FULL));
        if (rows == 0) throw new BaseException(ErrorConstant.POOL_STATUS_ABNORMAL_CANNOT_COMPLETE);
    }

    public void toFinished(Long poolId) {
        int rows = poolMapper.update(null, new UpdateWrapper<CarPool>()
                .set("status", PoolStatus.FINISHED)
                .eq("id", poolId).eq("status", PoolStatus.COMPLETED));
        if (rows == 0) throw new BaseException(ErrorConstant.POOL_STATUS_ABNORMAL_CANNOT_FINISH);
    }

    public void toCancelled(Long poolId, Long ownerId) {
        int rows = poolMapper.update(null, new UpdateWrapper<CarPool>()
                .set("status", PoolStatus.CANCELLED)
                .eq("id", poolId).eq("owner_id", ownerId)
                .in("status", PoolStatus.OPEN, PoolStatus.FULL));
        if (rows == 0) throw new BaseException(ErrorConstant.CANCEL_FAILED_NOT_OWNER_OR_NOT_OPEN);
    }

    public void rollbackToOpen(Long poolId) {
        poolMapper.update(null, new UpdateWrapper<CarPool>()
                .set("status", PoolStatus.OPEN)
                .eq("id", poolId).eq("status", PoolStatus.FULL));
    }
}
