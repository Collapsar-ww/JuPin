package com.jupin.server.controller.player;

import com.jupin.common.context.BaseContext;
import com.jupin.common.result.Result;
import com.jupin.pojo.entity.CreditLog;
import com.jupin.server.service.CreditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "玩家端-信用")
@RestController
@RequestMapping("/api/player/credit")
@RequiredArgsConstructor
public class PlayerCreditController {

    private final CreditService creditService;

    @Operation(summary = "我的信用分  🔒")
    @GetMapping("/score")
    public Result<Map<String, Integer>> score() {
        return Result.success(Map.of("creditScore", creditService.getScore(BaseContext.getCurrentId())));
    }

    @Operation(summary = "信用分记录  🔒")
    @GetMapping("/log")
    public Result<List<CreditLog>> log(@RequestParam(defaultValue = "1") Integer page,
                                       @RequestParam(defaultValue = "20") Integer size) {
        return Result.success(creditService.getLog(BaseContext.getCurrentId(), page, size));
    }
}
