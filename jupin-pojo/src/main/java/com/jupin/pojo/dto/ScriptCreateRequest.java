package com.jupin.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.math.BigDecimal;

@Data
@Schema(description = "创建/修改剧本请求")
public class ScriptCreateRequest {
    @NotBlank(message = "剧本名称不能为空")
    @Schema(description = "剧本名称", example = "年轮")
    private String name;

    @Schema(description = "剧本类型", example = "硬核")
    private String type;

    @Schema(description = "难度：1-简单 2-中等 3-困难", example = "2")
    private Integer difficulty;

    @Schema(description = "最少人数", example = "4")
    private Integer minPlayers;

    @Schema(description = "最多人数", example = "6")
    private Integer maxPlayers;

    @Schema(description = "参考时长(分钟)", example = "240")
    private Integer duration;

    @Schema(description = "角色列表JSON")
    private String roles;

    @Schema(description = "封面图URL")
    private String cover;

    @Schema(description = "价格参考")
    private BigDecimal priceRef;

    @Schema(description = "剧本简介")
    private String description;
}
