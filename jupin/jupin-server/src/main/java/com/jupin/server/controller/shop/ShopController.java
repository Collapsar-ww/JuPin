package com.jupin.server.controller.shop;

import cn.hutool.core.bean.BeanUtil;
import com.jupin.common.context.BaseContext;
import com.jupin.common.exception.BaseException;
import com.jupin.common.result.Result;
import com.jupin.pojo.dto.SetMemberRoleRequest;
import com.jupin.pojo.dto.ShopCreateRequest;
import com.jupin.pojo.dto.ShopJoinRequest;
import com.jupin.pojo.dto.ShopUpdateRequest;
import com.jupin.pojo.entity.Shop;
import com.jupin.pojo.entity.ShopMember;
import com.jupin.pojo.vo.ShopCurrentVO;
import com.jupin.pojo.vo.ShopMemberVO;
import com.jupin.pojo.vo.ShopVO;
import com.jupin.server.converter.VOConverter;
import com.jupin.server.mapper.ShopMemberMapper;
import com.jupin.server.service.ShopService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;

@Tag(name = "店家端-店铺")
@RestController
@RequestMapping("/api/shop")
@RequiredArgsConstructor
public class ShopController {

    private final ShopService shopService;
    private final ShopMemberMapper shopMemberMapper;
    private final VOConverter converter;

    @Operation(summary = "创建店铺  🔒")
    @PostMapping("/create")
    public Result<ShopVO> create(@Valid @RequestBody ShopCreateRequest request) {
        Shop shop = shopService.create(BaseContext.getCurrentId(), request);
        return Result.success(BeanUtil.copyProperties(shop, ShopVO.class));
    }

    @Operation(summary = "我的店铺信息  🔒")
    @GetMapping("/my")
    public Result<ShopVO> my() {
        Shop shop = shopService.getMyShop(BaseContext.getCurrentId());
        return Result.success(BeanUtil.copyProperties(shop, ShopVO.class));
    }

    @Operation(summary = "当前店家账号绑定店铺  🔒")
    @GetMapping("/current")
    public Result<ShopCurrentVO> current() {
        ShopMember member = shopMemberMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ShopMember>()
                        .eq("user_id", BaseContext.getCurrentId()));
        if (member == null) {
            return Result.error("当前账号未绑定店铺");
        }
        Shop shop = shopService.getMyShop(BaseContext.getCurrentId());
        ShopCurrentVO vo = new ShopCurrentVO();
        vo.setId(shop.getId());
        vo.setName(shop.getName());
        vo.setCity(shop.getCity());
        vo.setAddress(shop.getAddress());
        vo.setRole(member.getRole());
        return Result.success(vo);
    }

    @Operation(summary = "修改店铺信息  🔒")
    @PutMapping("/update")
    public Result<ShopVO> update(@Valid @RequestBody ShopUpdateRequest request) {
        Shop shop = shopService.update(BaseContext.getCurrentId(), request);
        return Result.success(BeanUtil.copyProperties(shop, ShopVO.class));
    }

    @Operation(summary = "搜索店铺")
    @GetMapping("/search")
    public Result<List<ShopVO>> search(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        List<Shop> shops = shopService.search(city, status, page, size);
        List<ShopVO> vos = shops.stream().map(s -> BeanUtil.copyProperties(s, ShopVO.class)).collect(Collectors.toList());
        return Result.success(vos);
    }

    @Operation(summary = "申请加入店铺  🔒")
    @PostMapping("/join")
    public Result<Void> join(@Valid @RequestBody ShopJoinRequest request) {
        shopService.join(BaseContext.getCurrentId(), request.getShopId());
        return Result.success();
    }

    @Operation(summary = "成员列表  🔒")
    @GetMapping("/{shopId}/members")
    public Result<List<ShopMemberVO>> members(@PathVariable Long shopId) {
        List<ShopMember> members = shopService.getMembers(shopId, BaseContext.getCurrentId());
        List<ShopMemberVO> vos = members.stream().map(converter::toShopMemberVO).collect(Collectors.toList());
        return Result.success(vos);
    }

    @Operation(summary = "添加成员  🔒")
    @PostMapping("/{shopId}/members/add")
    public Result<Void> addMember(@PathVariable Long shopId, @RequestBody Long userId) {
        shopService.addMember(shopId, BaseContext.getCurrentId(), userId);
        return Result.success();
    }

    @Operation(summary = "移除成员  🔒")
    @PostMapping("/{shopId}/members/remove")
    public Result<Void> removeMember(@PathVariable Long shopId, @RequestBody Long userId) {
        shopService.removeMember(shopId, BaseContext.getCurrentId(), userId);
        return Result.success();
    }

    @Operation(summary = "设置成员权限  🔒")
    @PutMapping("/{shopId}/members/role")
    public Result<Void> setMemberRole(@PathVariable Long shopId, @RequestBody SetMemberRoleRequest request) {
        shopService.setMemberRole(shopId, BaseContext.getCurrentId(), request.getUserId(), request.getRole());
        return Result.success();
    }
}
