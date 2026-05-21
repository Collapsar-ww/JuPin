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
@TableName("shop")
public class Shop {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    private String address;
    private String phone;
    private String logo;
    private String cover;
    private String description;
    private String openingHours;
    private String city;
    private Integer status;         // 0-关闭 1-营业

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
