package com.jupin.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@Schema(description = "我的拼车成员信息")
public class MemberPoolVO {
    @Schema(description = "拼车ID")
    private Long poolId;
    @Schema(description = "成员状态")
    private Integer memberStatus;
    @Schema(description = "拼车状态")
    private Integer poolStatus;
    @Schema(description = "剧本名")
    private String scriptName;
    @Schema(description = "开始时间")
    private LocalDateTime startTime;
    @Schema(description = "拼车类型")
    private Integer type;
    @Schema(description = "押金金额")
    private BigDecimal deposit;
    @Schema(description = "COMPLETED确认状态：0-未确认 1-已确认 2-已拒绝")
    private Integer completedConfirmed;
    @Schema(description = "FINISHED确认状态：0-未确认 1-已确认 2-已拒绝")
    private Integer finishedConfirmed;
}
