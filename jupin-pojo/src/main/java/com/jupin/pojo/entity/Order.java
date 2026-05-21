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
@TableName("`order`")
public class Order {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;
    private Long userId;
    private Long poolId;
    private Integer type;           // 0-押金 1-车费
    private BigDecimal amount;
    private Integer status;         // 0-待支付 1-已支付 2-已退款 3-已扣留 4-逾期
    private Long payeeId;           // 收款方ID
    private Integer payeeType;      // 0-DM(个人) 1-店铺
    private Integer releaseStatus;  // 0-未释放 1-已释放
    private LocalDateTime releaseTime;
    private String refundReason;
    private String channelTxnId;
    private LocalDateTime payTime;
    private LocalDateTime refundTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
