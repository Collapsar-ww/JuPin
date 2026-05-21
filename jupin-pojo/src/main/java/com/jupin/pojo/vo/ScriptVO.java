package com.jupin.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Schema(description = "剧本信息（响应）")
public class ScriptVO {
    @Schema(description = "剧本ID")
    private Long id;

    @Schema(description = "剧本名称", example = "年轮")
    private String name;

    @Schema(description = "剧本类型", example = "硬核")
    private String type;

    @Schema(description = "难度：1-简单 2-中等 3-困难")
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

    @Schema(description = "店内定价（店铺剧本库时返回）")
    private BigDecimal shopPrice;

    @Schema(description = "状态：0-下架 1-上架")
    private Integer status;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
