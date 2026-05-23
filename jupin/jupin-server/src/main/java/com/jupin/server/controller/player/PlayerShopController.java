package com.jupin.server.controller.player;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jupin.common.result.Result;
import com.jupin.pojo.entity.CarPool;
import com.jupin.pojo.entity.Script;
import com.jupin.pojo.entity.Shop;
import com.jupin.pojo.vo.PlayerShopVO;
import com.jupin.pojo.vo.PoolVO;
import com.jupin.pojo.vo.ScriptVO;
import com.jupin.server.converter.VOConverter;
import com.jupin.server.mapper.ShopMapper;
import com.jupin.server.service.PoolService;
import com.jupin.server.service.ScriptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@Tag(name = "玩家端-店铺浏览")
@RestController
@RequestMapping("/api/player/shop")
@RequiredArgsConstructor
public class PlayerShopController {

    private final ShopMapper shopMapper;
    private final ScriptService scriptService;
    private final PoolService poolService;
    private final VOConverter converter;

    @Operation(summary = "玩家端店铺列表")
    @GetMapping("/list")
    public Result<List<PlayerShopVO>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String keyword) {
        QueryWrapper<Shop> q = new QueryWrapper<Shop>().eq("status", 1);
        if (StringUtils.hasText(city)) q.eq("city", city);
        if (StringUtils.hasText(keyword)) {
            q.and(w -> w.like("name", keyword).or().like("description", keyword));
        }
        q.orderByDesc("create_time");
        Page<Shop> p = shopMapper.selectPage(new Page<>(page, size), q);
        List<PlayerShopVO> vos = p.getRecords().stream().map(s -> {
            PlayerShopVO vo = BeanUtil.copyProperties(s, PlayerShopVO.class);
            vo.setRating(null);
            vo.setRatingText("暂无评分");
            return vo;
        }).collect(Collectors.toList());
        return Result.success(vos);
    }

    @Operation(summary = "玩家端店铺主页详情")
    @GetMapping("/{shopId}")
    public Result<PlayerShopVO> detail(@PathVariable Long shopId) {
        Shop shop = shopMapper.selectById(shopId);
        if (shop == null) {
            return Result.error("店铺不存在");
        }
        PlayerShopVO vo = BeanUtil.copyProperties(shop, PlayerShopVO.class);
        vo.setRating(null);
        vo.setRatingText("暂无评分");
        return Result.success(vo);
    }

    @Operation(summary = "玩家查看店铺剧本库")
    @GetMapping("/{shopId}/scripts")
    public Result<List<ScriptVO>> scripts(
            @PathVariable Long shopId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        List<Script> scripts = scriptService.listShopScripts(shopId);
        List<ScriptVO> vos = scripts.stream().map(s -> BeanUtil.copyProperties(s, ScriptVO.class)).collect(Collectors.toList());
        return Result.success(vos);
    }

    @Operation(summary = "玩家查看店铺下全部店家局")
    @GetMapping("/{shopId}/pools")
    public Result<List<PoolVO>> pools(
            @PathVariable Long shopId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        List<CarPool> pools = poolService.listShopPools(shopId, null, page, size);
        List<PoolVO> vos = pools.stream().map(converter::toPoolVO).collect(Collectors.toList());
        return Result.success(vos);
    }
}
