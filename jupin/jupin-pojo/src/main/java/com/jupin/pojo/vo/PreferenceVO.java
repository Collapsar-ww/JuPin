package com.jupin.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "玩家偏好（响应）")
public class PreferenceVO {
    @Schema(description = "常驻城市")
    private String city;

    @Schema(description = "偏好剧本类型（V0单选）")
    private String scriptType;

    @Schema(description = "最低价格")
    private BigDecimal priceMin;

    @Schema(description = "最高价格")
    private BigDecimal priceMax;

    @Schema(description = "常玩时间段")
    private String timeSlot;

    @Schema(description = "最少人数")
    private Integer minMembers;

    @Schema(description = "最多人数")
    private Integer maxMembers;
}
