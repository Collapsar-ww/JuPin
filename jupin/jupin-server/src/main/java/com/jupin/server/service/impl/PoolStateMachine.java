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
        // 组局满员只允许从 OPEN 推进到 FULL。
        // 用 where status = OPEN 做乐观条件，防止已取消或已成团的组局被错误改成满员。
        int rows = poolMapper.update(null, new UpdateWrapper<CarPool>()
                .set("status", PoolStatus.FULL)
                .eq("id", poolId).eq("status", PoolStatus.OPEN));
        // 更新 0 行表示当前状态不满足流转条件，直接抛出状态异常。
        if (rows == 0) throw new BaseException(ErrorConstant.POOL_STATUS_ABNORMAL_CANNOT_SET_FULL);
    }

    public void toCompleted(Long poolId) {
        // 成团只允许从 FULL 推进到 COMPLETED。
        // 这对应“人数已满并完成成团确认”之后的业务状态。
        int rows = poolMapper.update(null, new UpdateWrapper<CarPool>()
                .set("status", PoolStatus.COMPLETED)
                .eq("id", poolId).eq("status", PoolStatus.FULL));
        // 如果不是满员状态，说明成团前置条件不满足。
        if (rows == 0) throw new BaseException(ErrorConstant.POOL_STATUS_ABNORMAL_CANNOT_COMPLETE);
    }

    public void toFinished(Long poolId) {
        // 结束只允许从 COMPLETED 推进到 FINISHED。
        // 也就是已经成团并线下游玩后，才能进入最终结束状态。
        int rows = poolMapper.update(null, new UpdateWrapper<CarPool>()
                .set("status", PoolStatus.FINISHED)
                .eq("id", poolId).eq("status", PoolStatus.COMPLETED));
        // 更新失败说明当前组局还没有成团，或已经被其他流程处理过。
        if (rows == 0) throw new BaseException(ErrorConstant.POOL_STATUS_ABNORMAL_CANNOT_FINISH);
    }

    public void toCancelled(Long poolId, Long ownerId) {
        // 取消组局要求当前用户是发起人，并且组局仍处于 OPEN 或 FULL。
        // 已成团、已结束的组局不能随意取消。
        int rows = poolMapper.update(null, new UpdateWrapper<CarPool>()
                .set("status", PoolStatus.CANCELLED)
                .eq("id", poolId).eq("owner_id", ownerId)
                .in("status", PoolStatus.OPEN, PoolStatus.FULL));
        // 更新 0 行可能是无权限，也可能是状态已经不允许取消。
        if (rows == 0) throw new BaseException(ErrorConstant.CANCEL_FAILED_NOT_OWNER_OR_NOT_OPEN);
    }

    public void rollbackToOpen(Long poolId) {
        // 满员组局有人退出后，需要从 FULL 回滚到 OPEN。
        // 只有当前确实是 FULL 时才回滚，避免影响其他状态。
        poolMapper.update(null, new UpdateWrapper<CarPool>()
                .set("status", PoolStatus.OPEN)
                .eq("id", poolId).eq("status", PoolStatus.FULL));
    }
}
