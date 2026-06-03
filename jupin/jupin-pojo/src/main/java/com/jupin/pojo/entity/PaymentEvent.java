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
@TableName("payment_event")
public class PaymentEvent {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String eventKey;
    private String orderNo;
    private String eventType;
    private String requestNo;
    private String channelTxnId;
    private Integer status;
    private String rawPayload;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
