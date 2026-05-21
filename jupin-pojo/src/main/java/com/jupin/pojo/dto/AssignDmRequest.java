package com.jupin.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
@Schema(description = "指派DM请求")
public class AssignDmRequest {
    @NotNull
    @Schema(description = "拼车ID", example = "1")
    private Long poolId;

    @NotNull
    @Schema(description = "DM用户ID", example = "2")
    private Long dmId;
}
