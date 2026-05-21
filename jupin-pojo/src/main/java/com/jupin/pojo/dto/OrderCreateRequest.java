package com.jupin.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
@Schema(description = "创建订单请求")
public class OrderCreateRequest {
    @NotNull
    @Schema(description = "关联拼车 ID", example = "1")
    private Long poolId;

    @NotNull
    @Schema(description = "订单类型：0-押金 1-车费", example = "0")
    private Integer type;

    @Schema(description = "收款方ID（创建时自动填入，玩家局=DM, 店家局=店铺）")
    private Long payeeId;

    @Schema(description = "收款方类型：0-DM(个人) 1-店铺")
    private Integer payeeType;
}
