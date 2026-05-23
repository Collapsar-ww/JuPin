package com.jupin.server.controller.shop;

import cn.hutool.core.bean.BeanUtil;
import com.jupin.common.result.Result;
import com.jupin.pojo.entity.Order;
import com.jupin.pojo.vo.OrderVO;
import com.jupin.server.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@Tag(name = "店家端-订单")
@RestController
@RequestMapping("/api/shop/order")
@RequiredArgsConstructor
public class ShopOrderController {

    private final OrderService orderService;

    @Operation(summary = "店铺订单  🔒")
    @GetMapping("/list")
    public Result<List<OrderVO>> list(
            @RequestParam Long shopId,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        List<Order> orders = orderService.shopOrders(shopId, status, page, size);
        List<OrderVO> vos = orders.stream().map(o -> BeanUtil.copyProperties(o, OrderVO.class)).collect(Collectors.toList());
        return Result.success(vos);
    }
}
