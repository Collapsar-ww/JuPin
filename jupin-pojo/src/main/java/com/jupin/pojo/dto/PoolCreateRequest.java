package com.jupin.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

@Data
@Schema(description = "发布拼车请求")
public class PoolCreateRequest {
    @Schema(description = "拼车类型：0-玩家局 1-店家局", example = "0")
    private Integer type;

    @Schema(description = "关联剧本ID（玩家局选系统剧本必填）")
    private Long scriptId;

    @Schema(description = "关联店铺ID（店家局必填）")
    private Long shopId;

    @NotBlank(message = "剧本名称不能为空")
    @Schema(description = "剧本名称", example = "年轮")
    private String scriptName;

    @Schema(description = "剧本类型", example = "硬核")
    private String scriptType;

    @Schema(description = "角色列表 JSON：[{\"name\":\"金运\",\"desc\":\"侦探\"}]")
    private String roles;

    @NotBlank(message = "城市不能为空")
    @Schema(description = "所在城市", example = "上海")
    private String city;

    @Schema(description = "详细地址", example = "静安区南京西路XX号")
    private String address;

    @NotBlank(message = "开始时间不能为空")
    @Schema(description = "开始时间（格式：yyyy-MM-dd HH:mm:ss）", example = "2026-06-01 14:00:00")
    private String startTime;

    @Schema(description = "结束时间（格式：yyyy-MM-dd HH:mm:ss）", example = "2026-06-01 18:00:00")
    private String endTime;

    @NotNull(message = "人数不能为空")
    @Schema(description = "总需人数", example = "6")
    private Integer maxMembers;

    @Schema(description = "人均费用", example = "88.00")
    private BigDecimal price;

    @Schema(description = "押金金额", example = "10.00")
    private BigDecimal deposit;

    @Schema(description = "加入方式：0-审核制 1-自动通过", example = "1")
    private Integer joinType;

    @Schema(description = "DM用户ID（店家局指派DM时传）")
    private Long dmId;
}
