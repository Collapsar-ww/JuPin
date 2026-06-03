package com.jupin.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
@Schema(description = "Mock 支付回调请求")
public class MockPayCallbackRequest {
    @NotBlank
    @Schema(description = "订单号")
    private String orderNo;

    @Schema(description = "支付请求幂等号")
    private String payRequestNo;

    @NotBlank
    @Schema(description = "回调请求幂等号")
    private String callbackRequestNo;

    @NotBlank
    @Schema(description = "渠道交易流水号")
    private String channelTxnId;

    @Schema(description = "支付结果：SUCCESS / FAIL", example = "SUCCESS")
    private String payStatus;
}
