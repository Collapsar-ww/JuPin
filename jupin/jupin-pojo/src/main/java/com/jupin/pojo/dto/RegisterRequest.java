package com.jupin.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

@Data
@Schema(description = "注册请求")
public class RegisterRequest {
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    @Schema(description = "手机号（11位中国大陆手机号）", example = "13800000001")
    private String phone;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度6-32位")
    @Schema(description = "密码（6-32位）", example = "abc123456")
    private String password;

    @NotBlank(message = "昵称不能为空")
    @Size(max = 50, message = "昵称最长50字符")
    @Schema(description = "昵称", example = "剧本杀手")
    private String nickname;

    @Schema(description = "性别：0-未知 1-男 2-女", example = "1")
    private Integer gender;

    @Schema(description = "角色：player-玩家 shop-店家", example = "player")
    @NotBlank(message = "角色不能为空")
    private String role;

    @Schema(description = "所在城市", example = "上海")
    private String city;
}
