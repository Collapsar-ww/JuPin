package com.jupin.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

@Data
@Schema(description = "修改拼车价格请求")
public class PriceUpdateRequest {
    @NotNull
    @Schema(description = "新价格", example = "99.00")
    private BigDecimal price;
}
