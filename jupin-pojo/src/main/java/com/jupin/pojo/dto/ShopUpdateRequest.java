package com.jupin.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "修改店铺信息请求")
public class ShopUpdateRequest {
    @Schema(description = "店铺名称", example = "静安剧本杀馆")
    private String name;

    @Schema(description = "店铺地址")
    private String address;

    @Schema(description = "联系电话")
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
