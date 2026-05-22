package com.jupin.server.controller.player;

import cn.hutool.core.bean.BeanUtil;
import com.jupin.common.context.BaseContext;
import com.jupin.common.result.Result;
import com.jupin.pojo.dto.OrderCreateRequest;
import com.jupin.pojo.entity.Order;
import com.jupin.pojo.vo.OrderVO;
import com.jupin.server.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;

@Tag(name = "玩家端-订单")
@RestController
@RequestMapping("/api/player/order")
@RequiredArgsConstructor
public class PlayerOrderController {

    private final OrderService orderService;

    @Operation(summary = "创建订单  🔒")
    @PostMapping("/create")
    public Result<OrderVO> create(@Valid @RequestBody OrderCreateRequest request) {
        Order order = orderService.create(BaseContext.getCurrentId(), request.getPoolId(), request.getType());
        return Result.success(BeanUtil.copyProperties(order, OrderVO.class));
    }

    @Operation(summary = "模拟支付  🔒")
    @PostMapping("/pay/{orderNo}")
    public Result<Void> pay(@PathVariable String orderNo) {
        orderService.pay(BaseContext.getCurrentId(), orderNo);
        return Result.success();
    }

    @Operation(summary = "我的订单  🔒")
    @GetMapping("/my")
    public Result<List<OrderVO>> myOrders(
            @RequestParam(required = false) Integer type,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        List<Order> orders = orderService.myOrders(BaseContext.getCurrentId(), type, status, page, size);
        List<OrderVO> vos = orders.stream().map(o -> BeanUtil.copyProperties(o, OrderVO.class)).collect(Collectors.toList());
        return Result.success(vos);
    }
}
