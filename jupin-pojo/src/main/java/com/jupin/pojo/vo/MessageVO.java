package com.jupin.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "消息信息（响应）")
public class MessageVO {
    @Schema(description = "消息 ID")
    private Long id;

    @Schema(description = "类型：0-系统 1-匹配结果 2-成团 3-跳车 4-评价")
    private Integer type;

    @Schema(description = "消息标题")
    private String title;

    @Schema(description = "消息内容")
    private String content;

    @Schema(description = "关联业务 ID")
    private Long relatedId;

    @Schema(description = "是否已读：0-未读 1-已读")
    private Integer isRead;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
