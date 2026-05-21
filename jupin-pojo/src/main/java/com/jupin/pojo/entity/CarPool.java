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
@TableName("car_pool")
public class CarPool {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Integer type;           // 0-玩家局 1-店家局
    private Long ownerId;           // 发布人
    private Long shopId;            // 店家局时关联店铺
    private Long scriptId;          // 关联剧本ID
    private String scriptName;
    private String scriptType;
    private String roles;
    private String city;
    private String address;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer maxMembers;
    private Integer currentMembers;
    private BigDecimal price;
    private BigDecimal deposit;
    private Long dmId;              // DM用户ID
    private Integer joinType;       // 0-审核制 1-自动通过
    private Integer status;         // 0-开放 1-满员 2-COMPLETED 3-FINISHED 4-已取消

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
