package com.jupin.server.controller.shop;

import cn.hutool.core.bean.BeanUtil;
import com.jupin.common.result.Result;
import com.jupin.pojo.dto.AddShopScriptRequest;
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

@Tag(name = "店家端-剧本")
@RestController
@RequestMapping("/api/shop/script")
@RequiredArgsConstructor
public class ShopScriptController {

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

    @Operation(summary = "店铺剧本库  🔒")
    @GetMapping("/{shopId}/scripts")
    public Result<List<ScriptVO>> shopScripts(@PathVariable Long shopId) {
        List<Script> scripts = scriptService.listShopScripts(shopId);
        List<ScriptVO> vos = scripts.stream().map(s -> BeanUtil.copyProperties(s, ScriptVO.class)).collect(Collectors.toList());
        return Result.success(vos);
    }

    @Operation(summary = "添加剧本到店  🔒")
    @PostMapping("/{shopId}/scripts/add")
    public Result<Void> addScript(@PathVariable Long shopId, @Valid @RequestBody AddShopScriptRequest request) {
        scriptService.addShopScript(shopId, request.getScriptId(), request.getPrice());
        return Result.success();
    }

    @Operation(summary = "移除店铺剧本  🔒")
    @DeleteMapping("/{shopId}/scripts/{scriptId}")
    public Result<Void> removeScript(@PathVariable Long shopId, @PathVariable Long scriptId) {
        scriptService.removeShopScript(shopId, scriptId);
        return Result.success();
    }
}
