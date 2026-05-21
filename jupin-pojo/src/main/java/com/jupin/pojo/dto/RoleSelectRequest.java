package com.jupin.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
@Schema(description = "选择剧本角色请求")
public class RoleSelectRequest {
    @NotBlank(message = "角色名不能为空")
    @Schema(description = "要选择的角色名", example = "金运")
    private String roleName;
}
