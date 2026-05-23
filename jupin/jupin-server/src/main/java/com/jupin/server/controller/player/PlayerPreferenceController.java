package com.jupin.server.controller.player;

import com.jupin.common.context.BaseContext;
import com.jupin.common.result.Result;
import com.jupin.pojo.dto.PreferenceRequest;
import com.jupin.pojo.vo.PreferenceVO;
import com.jupin.server.service.PlayerPreferenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@Tag(name = "玩家端-偏好")
@RestController
@RequestMapping("/api/player/preference")
@RequiredArgsConstructor
public class PlayerPreferenceController {

    private final PlayerPreferenceService preferenceService;

    @Operation(summary = "我的玩家偏好  🔒")
    @GetMapping
    public Result<PreferenceVO> get() {
        return Result.success(preferenceService.get(BaseContext.getCurrentId()));
    }

    @Operation(summary = "保存玩家偏好  🔒")
    @PutMapping
    public Result<Void> save(@Valid @RequestBody PreferenceRequest request) {
        preferenceService.save(BaseContext.getCurrentId(), request);
        return Result.success();
    }
}
