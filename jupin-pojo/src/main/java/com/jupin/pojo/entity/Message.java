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
@TableName("message")
public class Message {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String msgKey;          // 消息幂等键
    private Long userId;
    private Integer type;           // 0-系统 1-匹配 2-成团 3-跳车 4-评价
    private String title;
    private String content;
    private Long relatedId;
    private Integer isRead;
    private LocalDateTime readTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
