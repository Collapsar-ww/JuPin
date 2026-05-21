package com.jupin.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
@Schema(description = "设置成员权限请求")
public class SetMemberRoleRequest {
    @NotNull
    @Schema(description = "用户ID", example = "3")
    private Long userId;

    @NotNull
    @Schema(description = "角色：2-管理员 3-普通成员", example = "2")
    private Integer role;
}
