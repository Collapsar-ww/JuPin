package com.jupin.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Schema(description = "订单信息（响应）")
public class OrderVO {
    @Schema(description = "订单 ID")
    private Long id;

    @Schema(description = "订单号（雪花算法生成）")
    private String orderNo;

    @Schema(description = "用户 ID")
    private Long userId;

    @Schema(description = "关联拼车 ID")
    private Long poolId;

    @Schema(description = "类型：0-押金 1-车费")
    private Integer type;

    @Schema(description = "金额")
    private BigDecimal amount;

    @Schema(description = "状态：0-待支付 1-已支付 2-已退款 3-已扣留 4-逾期")
    private Integer status;

    @Schema(description = "收款方ID")
    private Long payeeId;

    @Schema(description = "收款方类型：0-DM(个人) 1-店铺")
    private Integer payeeType;

    @Schema(description = "释放状态：0-未释放 1-已释放")
    private Integer releaseStatus;

    @Schema(description = "释放时间")
    private LocalDateTime releaseTime;

    @Schema(description = "支付时间")
    private LocalDateTime payTime;

    @Schema(description = "退款时间")
    private LocalDateTime refundTime;

    @Schema(description = "退款原因")
    private String refundReason;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
