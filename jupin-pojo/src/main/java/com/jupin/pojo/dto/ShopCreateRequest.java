package com.jupin.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
@Schema(description = "创建店铺请求")
public class ShopCreateRequest {
    @NotBlank(message = "店铺名称不能为空")
    @Schema(description = "店铺名称", example = "静安剧本杀馆")
    private String name;

    @Schema(description = "店铺地址", example = "上海市静安区南京西路XXX号")
    private String address;

    @Schema(description = "联系电话", example = "021-12345678")
    private String phone;

    @Schema(description = "Logo URL")
    private String logo;

    @Schema(description = "封面图 URL")
    private String cover;

    @Schema(description = "店铺简介")
    private String description;

    @Schema(description = "营业时间", example = "10:00-22:00")
    private String openingHours;

    @Schema(description = "所在城市", example = "上海")
    private String city;
}
