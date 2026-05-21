package com.jupin.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
@Schema(description = "提交确认请求")
public class ConfirmRequest {
    @NotNull
    @Schema(description = "是否确认：true-确认 false-拒绝", example = "true")
    private Boolean confirmed;
}
