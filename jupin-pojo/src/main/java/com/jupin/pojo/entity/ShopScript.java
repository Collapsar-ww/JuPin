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
@TableName("shop_script")
public class ShopScript {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long shopId;
    private Long scriptId;
    private BigDecimal price;       // 店内定价

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
