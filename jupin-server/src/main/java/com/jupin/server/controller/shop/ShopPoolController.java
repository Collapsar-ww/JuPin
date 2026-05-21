package com.jupin.server.controller.shop;

import com.jupin.common.context.BaseContext;
import com.jupin.common.result.Result;
import com.jupin.pojo.dto.AssignDmRequest;
import com.jupin.pojo.dto.ConfirmRequest;
import com.jupin.pojo.dto.PoolCreateRequest;
import com.jupin.pojo.entity.CarPool;
import com.jupin.pojo.entity.PoolMember;
import com.jupin.pojo.vo.ConfirmVO;
import com.jupin.pojo.vo.MemberVO;
import com.jupin.pojo.vo.PoolVO;
import com.jupin.server.converter.VOConverter;
import com.jupin.server.service.PoolService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;

@Tag(name = "店家端-拼车")
@RestController
@RequestMapping("/api/shop/pool")
@RequiredArgsConstructor
public class ShopPoolController {

    private final PoolService poolService;
    private final VOConverter converter;

    @Operation(summary = "发布店家局  🔒")
    @PostMapping("/create")
    public Result<PoolVO> create(@Valid @RequestBody PoolCreateRequest request) {
        request.setType(1);
        CarPool pool = poolService.create(BaseContext.getCurrentId(), request);
        return Result.success(converter.toPoolVO(pool));
    }

    @Operation(summary = "店铺拼车列表  🔒")
    @GetMapping("/list")
    public Result<List<PoolVO>> list(
            @RequestParam(required = false) Long shopId,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        if (shopId == null) {
            return Result.success(List.of());
        }
        List<CarPool> pools = poolService.listShopPools(shopId, status, page, size);
        List<PoolVO> vos = pools.stream().map(converter::toPoolVO).collect(Collectors.toList());
        return Result.success(vos);
    }

    @Operation(summary = "拼车详情")
    @GetMapping("/{poolId}")
    public Result<PoolVO> detail(@PathVariable Long poolId) {
        CarPool pool = poolService.getDetail(poolId);
        List<PoolMember> members = poolService.getMembers(poolId);
        return Result.success(converter.toPoolVOWithMembers(pool, members));
    }

    @Operation(summary = "指派DM  🔒")
    @PostMapping("/{poolId}/assign-dm")
    public Result<Void> assignDm(@PathVariable Long poolId, @Valid @RequestBody AssignDmRequest request) {
        poolService.assignDm(BaseContext.getCurrentId(), poolId, request.getDmId());
        return Result.success();
    }

    @Operation(summary = "发起完成确认  🔒")
    @PostMapping("/{poolId}/complete")
    public Result<ConfirmVO> complete(@PathVariable Long poolId) {
        return Result.success(poolService.complete(BaseContext.getCurrentId(), poolId));
    }

    @Operation(summary = "提交完成确认  🔒")
    @PostMapping("/{poolId}/confirm")
    public Result<ConfirmVO> confirm(@PathVariable Long poolId, @Valid @RequestBody ConfirmRequest request) {
        return Result.success(poolService.confirm(BaseContext.getCurrentId(), poolId, request.getConfirmed()));
    }

    @Operation(summary = "发起剧本杀完成  🔒")
    @PostMapping("/{poolId}/finish")
    public Result<ConfirmVO> finish(@PathVariable Long poolId) {
        return Result.success(poolService.finish(BaseContext.getCurrentId(), poolId));
    }

    @Operation(summary = "成员列表")
    @GetMapping("/{poolId}/members")
    public Result<List<MemberVO>> members(@PathVariable Long poolId) {
        List<PoolMember> members = poolService.getMembers(poolId);
        List<MemberVO> vos = members.stream().map(converter::toMemberVO).collect(Collectors.toList());
        return Result.success(vos);
    }
}
