package com.jupin.server.controller.player;

import com.jupin.common.context.BaseContext;
import com.jupin.common.result.Result;
import com.jupin.pojo.dto.ConfirmRequest;
import com.jupin.pojo.dto.PoolCreateRequest;
import com.jupin.pojo.dto.RoleSelectRequest;
import com.jupin.pojo.entity.CarPool;
import com.jupin.pojo.entity.PoolMember;
import com.jupin.pojo.vo.ConfirmVO;
import com.jupin.pojo.vo.MemberVO;
import com.jupin.pojo.vo.PoolVO;
import com.jupin.pojo.vo.RoleStatusVO;
import com.jupin.server.converter.VOConverter;
import com.jupin.server.service.PoolService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Tag(name = "玩家端-拼车")
@RestController
@RequestMapping("/api/player/pool")
@RequiredArgsConstructor
public class PlayerPoolController {

    private final PoolService poolService;
    private final VOConverter converter;

    @Operation(summary = "发布玩家局  🔒")
    @PostMapping("/create")
    public Result<PoolVO> create(@Valid @RequestBody PoolCreateRequest request) {
        CarPool pool = poolService.create(BaseContext.getCurrentId(), request);
        return Result.success(converter.toPoolVO(pool));
    }

    @Operation(summary = "修改价格  🔒")
    @PutMapping("/{poolId}/price")
    public Result<Void> updatePrice(@PathVariable Long poolId, @RequestBody BigDecimal price) {
        poolService.updatePrice(BaseContext.getCurrentId(), poolId, price);
        return Result.success();
    }

    @Operation(summary = "转让DM  🔒")
    @PutMapping("/{poolId}/transfer-dm")
    public Result<Void> transferDm(@PathVariable Long poolId, @RequestBody Long newDmId) {
        poolService.transferDm(BaseContext.getCurrentId(), poolId, newDmId);
        return Result.success();
    }

    @Operation(summary = "拼车列表")
    @GetMapping("/list")
    public Result<List<PoolVO>> list(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String scriptType,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) BigDecimal priceMin,
            @RequestParam(required = false) BigDecimal priceMax,
            @RequestParam(required = false) String startTimeAfter,
            @RequestParam(required = false) String startTimeBefore,
            @RequestParam(required = false) Boolean recommend,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        List<CarPool> pools = poolService.list(city, scriptType, 0, status,
                priceMin, priceMax, startTimeAfter, startTimeBefore, recommend, page, size);
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

    @Operation(summary = "申请加入  🔒")
    @PostMapping("/{poolId}/join")
    public Result<Void> join(@PathVariable Long poolId) {
        poolService.join(BaseContext.getCurrentId(), poolId);
        return Result.success();
    }

    @Operation(summary = "退出/跳车  🔒")
    @PostMapping("/{poolId}/leave")
    public Result<Void> leave(@PathVariable Long poolId) {
        poolService.leave(BaseContext.getCurrentId(), poolId);
        return Result.success();
    }

    @Operation(summary = "通过申请  🔒")
    @PostMapping("/{poolId}/approve/{userId}")
    public Result<Void> approve(@PathVariable Long poolId, @PathVariable Long userId) {
        poolService.approve(BaseContext.getCurrentId(), poolId, userId);
        return Result.success();
    }

    @Operation(summary = "拒绝申请  🔒")
    @PostMapping("/{poolId}/reject/{userId}")
    public Result<Void> reject(@PathVariable Long poolId, @PathVariable Long userId) {
        poolService.reject(BaseContext.getCurrentId(), poolId, userId);
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

    @Operation(summary = "选择剧本角色  🔒")
    @PostMapping("/{poolId}/role/select")
    public Result<Void> selectRole(@PathVariable Long poolId, @Valid @RequestBody RoleSelectRequest request) {
        poolService.selectRole(BaseContext.getCurrentId(), poolId, request.getRoleName());
        return Result.success();
    }

    @Operation(summary = "角色列表及选择状态")
    @GetMapping("/{poolId}/roles")
    public Result<List<RoleStatusVO>> roles(@PathVariable Long poolId) {
        return Result.success(poolService.getRoles(poolId));
    }
}
