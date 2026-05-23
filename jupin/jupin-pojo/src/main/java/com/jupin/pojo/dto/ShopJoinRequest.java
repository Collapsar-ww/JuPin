package com.jupin.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
@Schema(description = "申请加入店铺请求")
public class ShopJoinRequest {
    @NotNull
    @Schema(description = "店铺ID", example = "1")
    private Long shopId;
}
