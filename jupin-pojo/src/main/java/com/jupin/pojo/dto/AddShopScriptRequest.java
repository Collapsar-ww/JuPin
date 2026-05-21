package com.jupin.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

@Data
@Schema(description = "添加剧本到店铺请求")
public class AddShopScriptRequest {
    @NotNull
    @Schema(description = "剧本ID", example = "1")
    private Long scriptId;

    @Schema(description = "店内定价", example = "88.00")
    private BigDecimal price;
}
