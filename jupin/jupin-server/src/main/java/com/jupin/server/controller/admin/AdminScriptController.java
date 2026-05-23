package com.jupin.server.controller.admin;

import cn.hutool.core.bean.BeanUtil;
import com.jupin.common.result.Result;
import com.jupin.pojo.dto.ScriptCreateRequest;
import com.jupin.pojo.entity.Script;
import com.jupin.pojo.vo.ScriptVO;
import com.jupin.server.service.ScriptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;

@Tag(name = "管理后台-剧本")
@RestController
@RequestMapping("/api/admin/script")
@RequiredArgsConstructor
public class AdminScriptController {

    private final ScriptService scriptService;

    @Operation(summary = "添加剧本  🔒")
    @PostMapping("/create")
    public Result<ScriptVO> create(@Valid @RequestBody ScriptCreateRequest request) {
        Script script = scriptService.create(request);
        return Result.success(BeanUtil.copyProperties(script, ScriptVO.class));
    }

    @Operation(summary = "修改剧本  🔒")
    @PutMapping("/{scriptId}")
    public Result<ScriptVO> update(@PathVariable Long scriptId, @Valid @RequestBody ScriptCreateRequest request) {
        Script script = scriptService.update(scriptId, request);
        return Result.success(BeanUtil.copyProperties(script, ScriptVO.class));
    }

    @Operation(summary = "下架剧本  🔒")
    @DeleteMapping("/{scriptId}")
    public Result<Void> delete(@PathVariable Long scriptId) {
        scriptService.delete(scriptId);
        return Result.success();
    }

    @Operation(summary = "剧本列表  🔒")
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
