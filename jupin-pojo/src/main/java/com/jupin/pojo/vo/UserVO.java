package com.jupin.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "用户信息（响应）")
public class UserVO {
    @Schema(description = "用户 ID")
    private Long id;

    @Schema(description = "手机号", example = "13800000001")
    private String phone;

    @Schema(description = "昵称", example = "剧本杀手")
    private String nickname;

    @Schema(description = "头像 URL")
    private String avatar;

    @Schema(description = "性别：0-未知 1-男 2-女", example = "1")
    private Integer gender;

    @Schema(description = "角色：0-玩家 1-店家 2-管理员", example = "0")
    private Integer role;

    @Schema(description = "所在城市", example = "上海")
    private String city;

    @Schema(description = "剧本偏好标签，逗号分隔", example = "硬核,情感,欢乐")
    private String preference;

    @Schema(description = "信用分", example = "95")
    private Integer creditScore;
}
