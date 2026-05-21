package com.jupin.server.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jupin.common.exception.BaseException;
import com.jupin.pojo.dto.ScriptCreateRequest;
import com.jupin.pojo.entity.Script;
import com.jupin.pojo.entity.ShopScript;
import com.jupin.server.mapper.ScriptMapper;
import com.jupin.server.mapper.ShopScriptMapper;
import com.jupin.server.service.ScriptService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ScriptServiceImpl implements ScriptService {

    private final ScriptMapper scriptMapper;
    private final ShopScriptMapper shopScriptMapper;

    @Override
    public List<Script> list(String type, Integer page, Integer size) {
        QueryWrapper<Script> q = new QueryWrapper<Script>()
                .eq("status", 1)
                .eq(StringUtils.hasText(type), "type", type)
                .orderByDesc("create_time");
        Page<Script> p = scriptMapper.selectPage(new Page<>(page, size), q);
        return p.getRecords();
    }

    @Override
    @Transactional
    public Script create(ScriptCreateRequest request) {
        Script script = Script.builder()
                .name(request.getName())
                .type(request.getType())
                .difficulty(request.getDifficulty())
                .minPlayers(request.getMinPlayers())
                .maxPlayers(request.getMaxPlayers())
                .duration(request.getDuration())
                .roles(request.getRoles())
                .cover(request.getCover())
                .priceRef(request.getPriceRef())
                .description(request.getDescription())
                .status(1)
                .build();
        scriptMapper.insert(script);
        return script;
    }

    @Override
    @Transactional
    public Script update(Long scriptId, ScriptCreateRequest request) {
        Script existing = scriptMapper.selectById(scriptId);
        if (existing == null) throw new BaseException("剧本不存在");
        BeanUtil.copyProperties(request, existing);
        scriptMapper.updateById(existing);
        return existing;
    }

    @Override
    @Transactional
    public void delete(Long scriptId) {
        Script script = scriptMapper.selectById(scriptId);
        if (script == null) throw new BaseException("剧本不存在");
        script.setStatus(0);
        scriptMapper.updateById(script);
    }

    @Override
    public List<Script> listShopScripts(Long shopId) {
        List<ShopScript> shopScripts = shopScriptMapper.selectList(
                new QueryWrapper<ShopScript>().eq("shop_id", shopId));
        if (shopScripts.isEmpty()) return List.of();
        List<Long> ids = shopScripts.stream().map(ShopScript::getScriptId).toList();
        return scriptMapper.selectBatchIds(ids);
    }

    @Override
    @Transactional
    public void addShopScript(Long shopId, Long scriptId, BigDecimal price) {
        Long count = shopScriptMapper.selectCount(new QueryWrapper<ShopScript>()
                .eq("shop_id", shopId).eq("script_id", scriptId));
        if (count > 0) throw new BaseException("该剧本已在店铺中");

        ShopScript ss = ShopScript.builder()
                .shopId(shopId)
                .scriptId(scriptId)
                .price(price)
                .build();
        shopScriptMapper.insert(ss);
    }

    @Override
    @Transactional
    public void removeShopScript(Long shopId, Long scriptId) {
        shopScriptMapper.delete(new QueryWrapper<ShopScript>()
                .eq("shop_id", shopId).eq("script_id", scriptId));
    }
}
