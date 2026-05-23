package com.jupin.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("pool_member")
public class PoolMember {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long poolId;
    private Long userId;
    private Integer role;           // 0-玩家 1-发布人
    private String selectedRole;
    private Integer status;         // 0-待审核 1-待支付 2-已加入 3-已退出(跳车) 4-已拒绝
    private Integer completedConfirmed;    // 0-未确认 1-已确认 2-已拒绝
    private LocalDateTime completedConfirmTime;
    private Integer finishedConfirmed;     // 0-未确认 1-已确认 2-已拒绝
    private LocalDateTime finishedConfirmTime;
    private LocalDateTime joinTime;
    private LocalDateTime leaveTime;
}
