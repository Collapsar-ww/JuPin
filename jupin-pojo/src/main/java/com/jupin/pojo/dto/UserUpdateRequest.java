package com.jupin.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "修改个人信息请求")
public class UserUpdateRequest {
    @Schema(description = "昵称", example = "剧本杀手")
    private String nickname;

    @Schema(description = "头像 URL")
    private String avatar;

    @Schema(description = "性别：0-未知 1-男 2-女", example = "1")
    private Integer gender;

    @Schema(description = "所在城市", example = "上海")
    private String city;

    @Schema(description = "剧本偏好标签，逗号分隔", example = "硬核,情感,欢乐")
    private String preference;
}
