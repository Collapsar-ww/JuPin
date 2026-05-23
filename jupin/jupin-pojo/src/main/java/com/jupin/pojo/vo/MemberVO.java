package com.jupin.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "拼车成员信息（响应）")
public class MemberVO {
    @Schema(description = "成员记录 ID")
    private Long id;

    @Schema(description = "用户 ID")
    private Long userId;

    @Schema(description = "用户昵称")
    private String nickname;

    @Schema(description = "用户头像")
    private String avatar;

    @Schema(description = "性别：0-未知 1-男 2-女")
    private Integer gender;

    @Schema(description = "信用分")
    private Integer creditScore;

    @Schema(description = "身份：0-玩家 1-发布人")
    private Integer role;

    @Schema(description = "已选剧本角色名")
    private String selectedRole;

    @Schema(description = "状态：0-待审核 1-待支付 2-已加入 3-已退出 4-已拒绝")
    private Integer status;

    @Schema(description = "COMPLETED确认状态：0-未确认 1-已确认 2-已拒绝")
    private Integer completedConfirmed;

    @Schema(description = "FINISHED确认状态：0-未确认 1-已确认 2-已拒绝")
    private Integer finishedConfirmed;

    @Schema(description = "加入时间")
    private LocalDateTime joinTime;
}
