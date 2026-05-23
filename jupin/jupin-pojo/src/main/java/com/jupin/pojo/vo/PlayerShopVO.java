package com.jupin.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "玩家端店铺信息（响应）")
public class PlayerShopVO {
    @Schema(description = "店铺ID")
    private Long id;

    @Schema(description = "店铺名称")
    private String name;

    @Schema(description = "所在城市")
    private String city;

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

    @Schema(description = "营业时间")
    private String openingHours;

    @Schema(description = "店铺评分，V0可返回null")
    private Double rating;

    @Schema(description = "评分文本")
    private String ratingText;
}
