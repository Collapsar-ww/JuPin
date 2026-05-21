package com.jupin.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "评价信息（响应）")
public class ReviewVO {
    @Schema(description = "评价 ID")
    private Long id;

    @Schema(description = "关联拼车 ID")
    private Long poolId;

    @Schema(description = "评价人 ID")
    private Long fromUserId;

    @Schema(description = "评价人昵称")
    private String fromNickname;

    @Schema(description = "被评价人 ID")
    private Long toUserId;

    @Schema(description = "被评价人昵称")
    private String toNickname;

    @Schema(description = "评分 1-5")
    private Integer score;

    @Schema(description = "评价内容")
    private String content;

    @Schema(description = "评价标签，逗号分隔")
    private String tags;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
