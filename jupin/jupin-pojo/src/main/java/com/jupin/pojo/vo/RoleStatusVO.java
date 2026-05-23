package com.jupin.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
@Schema(description = "剧本角色状态")
public class RoleStatusVO {
    @Schema(description = "角色名", example = "金运")
    private String roleName;

    @Schema(description = "角色描述", example = "侦探")
    private String roleDesc;

    @Schema(description = "是否已被选择")
    private boolean selected;

    @Schema(description = "选择该角色的用户 ID")
    private Long selectedByUserId;
}
