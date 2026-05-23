package com.jupin.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Schema(description = "拼车信息（响应）")
public class PoolVO {
    @Schema(description = "拼车 ID")
    private Long id;

    @Schema(description = "拼车类型：0-玩家局 1-店家局")
    private Integer type;

    @Schema(description = "发布人用户 ID")
    private Long ownerId;

    @Schema(description = "发布人昵称")
    private String ownerNickname;

    @Schema(description = "发布人头像")
    private String ownerAvatar;

    @Schema(description = "关联店铺 ID")
    private Long shopId;

    @Schema(description = "店铺名称（店家局时返回）")
    private String shopName;

    @Schema(description = "关联剧本 ID")
    private Long scriptId;

    @Schema(description = "剧本名称", example = "年轮")
    private String scriptName;

    @Schema(description = "剧本类型", example = "硬核")
    private String scriptType;

    @Schema(description = "所在城市", example = "上海")
    private String city;

    @Schema(description = "详细地址")
    private String address;

    @Schema(description = "开始时间")
    private LocalDateTime startTime;

    @Schema(description = "结束时间")
    private LocalDateTime endTime;

    @Schema(description = "总需人数", example = "6")
    private Integer maxMembers;

    @Schema(description = "当前人数", example = "3")
    private Integer currentMembers;

    @Schema(description = "人均费用")
    private BigDecimal price;

    @Schema(description = "押金金额")
    private BigDecimal deposit;

    @Schema(description = "DM用户ID")
    private Long dmId;

    @Schema(description = "DM昵称")
    private String dmNickname;

    @Schema(description = "加入方式：0-审核制 1-自动通过")
    private Integer joinType;

    @Schema(description = "状态：0-开放 1-满员 2-COMPLETED 3-FINISHED 4-已取消")
    private Integer status;

    @Schema(description = "成员列表")
    private List<MemberVO> members;

    @Schema(description = "角色状态列表")
    private List<RoleStatusVO> roles;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
