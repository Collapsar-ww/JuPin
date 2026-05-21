package com.jupin.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
@Schema(description = "登录请求")
public class LoginRequest {
    @NotBlank(message = "手机号不能为空")
    @Schema(description = "手机号", example = "13800000001")
    private String phone;

    @NotBlank(message = "密码不能为空")
    @Schema(description = "密码", example = "abc123456")
    private String password;
}
