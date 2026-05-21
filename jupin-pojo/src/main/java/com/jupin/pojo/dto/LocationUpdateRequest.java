package com.jupin.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

@Data
@Schema(description = "更新拼车地点坐标请求")
public class LocationUpdateRequest {
    @NotNull
    @Schema(description = "经度", example = "121.458")
    private BigDecimal longitude;

    @NotNull
    @Schema(description = "纬度", example = "31.231")
    private BigDecimal latitude;
}
