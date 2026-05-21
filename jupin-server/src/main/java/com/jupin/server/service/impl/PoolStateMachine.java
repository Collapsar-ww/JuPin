package com.jupin.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.jupin.common.constant.PoolStatus;
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
        if (rows == 0) throw new BaseException("拼车状态异常，无法设为满员");
    }

    public void toCompleted(Long poolId) {
        int rows = poolMapper.update(null, new UpdateWrapper<CarPool>()
                .set("status", PoolStatus.COMPLETED)
                .eq("id", poolId).eq("status", PoolStatus.FULL));
        if (rows == 0) throw new BaseException("拼车状态异常，无法设为已完成");
    }

    public void toFinished(Long poolId) {
        int rows = poolMapper.update(null, new UpdateWrapper<CarPool>()
                .set("status", PoolStatus.FINISHED)
                .eq("id", poolId).eq("status", PoolStatus.COMPLETED));
        if (rows == 0) throw new BaseException("拼车状态异常，无法设为完成");
    }

    public void toCancelled(Long poolId, Long ownerId) {
        int rows = poolMapper.update(null, new UpdateWrapper<CarPool>()
                .set("status", PoolStatus.CANCELLED)
                .eq("id", poolId).eq("owner_id", ownerId)
                .in("status", PoolStatus.OPEN, PoolStatus.FULL));
        if (rows == 0) throw new BaseException("取消失败，请确认你是发布人且拼车未完成");
    }

    public void rollbackToOpen(Long poolId) {
        poolMapper.update(null, new UpdateWrapper<CarPool>()
                .set("status", PoolStatus.OPEN)
                .eq("id", poolId).eq("status", PoolStatus.FULL));
    }
}
