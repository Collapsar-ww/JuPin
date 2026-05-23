package com.jupin.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "店家当前绑定店铺（响应）")
public class ShopCurrentVO {
    @Schema(description = "店铺ID")
    private Long id;

    @Schema(description = "店铺名称")
    private String name;

    @Schema(description = "所在城市")
    private String city;

    @Schema(description = "店铺地址")
    private String address;

    @Schema(description = "当前用户在店铺中的角色：1-店长 2-管理员 3-普通成员")
    private Integer role;
}
