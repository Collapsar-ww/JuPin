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
@TableName("player_preference")
public class PlayerPreference {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String city;
    private String scriptType;
    private BigDecimal priceMin;
    private BigDecimal priceMax;
    private String timeSlot;
    private Integer minMembers;
    private Integer maxMembers;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
