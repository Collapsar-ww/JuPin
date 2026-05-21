package com.jupin.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
@Schema(description = "登录响应（AccessToken + RefreshToken + 用户信息）")
public class LoginVO {
    @Schema(description = "Access Token", example = "eyJhbGciOiJIUzI1NiIs...")
    private String accessToken;

    @Schema(description = "Refresh Token", example = "eyJhbGciOiJIUzI1NiIs...")
    private String refreshToken;

    @Schema(description = "用户基本信息")
    private UserVO user;
}
