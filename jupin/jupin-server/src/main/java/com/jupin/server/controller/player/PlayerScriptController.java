package com.jupin.server.controller.player;

import cn.hutool.core.bean.BeanUtil;
import com.jupin.common.result.Result;
import com.jupin.pojo.entity.Script;
import com.jupin.pojo.vo.ScriptVO;
import com.jupin.server.service.ScriptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@Tag(name = "玩家端-剧本")
@RestController
@RequestMapping("/api/player/script")
@RequiredArgsConstructor
public class PlayerScriptController {

    private final ScriptService scriptService;

    @Operation(summary = "系统剧本库")
    @GetMapping("/list")
    public Result<List<ScriptVO>> list(
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "50") Integer size) {
        List<Script> scripts = scriptService.list(type, page, size);
        List<ScriptVO> vos = scripts.stream().map(s -> BeanUtil.copyProperties(s, ScriptVO.class)).collect(Collectors.toList());
        return Result.success(vos);
    }
}
