package com.jupin.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "店铺成员信息（响应）")
public class ShopMemberVO {
    @Schema(description = "成员记录ID")
    private Long id;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "用户昵称")
    private String nickname;

    @Schema(description = "用户头像")
    private String avatar;

    @Schema(description = "角色：1-店长 2-管理员 3-普通成员")
    private Integer role;

    @Schema(description = "加入时间")
    private LocalDateTime createTime;
}
