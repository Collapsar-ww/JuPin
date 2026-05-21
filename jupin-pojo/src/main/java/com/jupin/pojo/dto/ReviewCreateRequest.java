package com.jupin.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

@Data
@Schema(description = "提交评价请求")
public class ReviewCreateRequest {
    @NotNull
    @Schema(description = "关联拼车 ID", example = "1")
    private Long poolId;

    @NotNull
    @Schema(description = "评价目标ID（type=0存shop_id, type=1存dm_user_id）", example = "1")
    private Long targetId;

    @Schema(description = "评价类型：0-评价店家 1-评价DM", example = "1")
    private Integer type;

    @NotNull @Min(1) @Max(5)
    @Schema(description = "评分 1-5", example = "5")
    private Integer score;

    @Schema(description = "评价内容", example = "玩得很好，逻辑清晰！")
    private String content;

    @Schema(description = "评价标签，逗号分隔", example = "准时,逻辑帝")
    private String tags;
}
