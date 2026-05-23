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
@TableName("shop_member")
public class ShopMember {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long shopId;
    private Long userId;
    private Integer role;           // 1-店长 2-管理员 3-普通成员
    private Integer status;         // 1-已加入

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
