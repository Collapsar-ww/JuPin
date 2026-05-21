package com.jupin.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("script")
public class Script {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    private String type;            // 硬核/情感/欢乐/恐怖/机制
    private Integer difficulty;     // 1-简单 2-中等 3-困难
    private Integer minPlayers;
    private Integer maxPlayers;
    private Integer duration;       // 参考时长(分钟)
    private String roles;           // 角色列表JSON
    private String cover;
    private BigDecimal priceRef;    // 价格参考
    private String description;
    private Integer status;         // 0-下架 1-上架

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
