package com.jupin.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "群聊消息（响应）")
public class ChatMessageVO {
    @Schema(description = "消息ID")
    private Long id;

    @Schema(description = "拼车ID")
    private Long poolId;

    @Schema(description = "发送者用户ID")
    private Long senderId;

    @Schema(description = "发送者昵称")
    private String senderName;

    @Schema(description = "发送者角色：player/shop")
    private String senderRole;

    @Schema(description = "消息内容")
    private String content;

    @Schema(description = "发送时间")
    private LocalDateTime createTime;
}
