package com.jupin.server.service;

import com.jupin.pojo.dto.ScriptCreateRequest;
import com.jupin.pojo.entity.Script;

import java.math.BigDecimal;
import java.util.List;

public interface ScriptService {
    List<Script> list(String type, Integer page, Integer size);
    Script create(ScriptCreateRequest request);
    Script update(Long scriptId, ScriptCreateRequest request);
    void delete(Long scriptId);

    List<Script> listShopScripts(Long shopId);
    void addShopScript(Long shopId, Long scriptId, BigDecimal price);
    void removeShopScript(Long shopId, Long scriptId);
}
