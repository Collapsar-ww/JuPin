package com.jupin.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
@Schema(description = "确认进度（响应）")
public class ConfirmVO {
    @Schema(description = "拼车ID")
    private Long poolId;

    @Schema(description = "已确认人数")
    private long confirmedCount;

    @Schema(description = "总需确认人数")
    private long totalCount;

    @Schema(description = "是否已达成条件")
    private boolean completed;
}
