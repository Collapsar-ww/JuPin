package com.jupin.server.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.jupin.pojo.dto.PreferenceRequest;
import com.jupin.pojo.entity.PlayerPreference;
import com.jupin.pojo.vo.PreferenceVO;
import com.jupin.server.mapper.PlayerPreferenceMapper;
import com.jupin.server.service.PlayerPreferenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlayerPreferenceServiceImpl implements PlayerPreferenceService {

    private final PlayerPreferenceMapper preferenceMapper;

    @Override
    public PreferenceVO get(Long userId) {
        PlayerPreference pref = preferenceMapper.selectOne(
                new QueryWrapper<PlayerPreference>().eq("user_id", userId));
        if (pref == null) {
            return new PreferenceVO();
        }
        return BeanUtil.copyProperties(pref, PreferenceVO.class);
    }

    @Override
    @Transactional
    public void save(Long userId, PreferenceRequest request) {
        PlayerPreference existing = preferenceMapper.selectOne(
                new QueryWrapper<PlayerPreference>().eq("user_id", userId));
        if (existing != null) {
            BeanUtil.copyProperties(request, existing);
            preferenceMapper.updateById(existing);
        } else {
            PlayerPreference pref = PlayerPreference.builder()
                    .userId(userId)
                    .city(request.getCity())
                    .scriptType(request.getScriptType())
                    .priceMin(request.getPriceMin())
                    .priceMax(request.getPriceMax())
                    .timeSlot(request.getTimeSlot())
                    .minMembers(request.getMinMembers())
                    .maxMembers(request.getMaxMembers())
                    .build();
            preferenceMapper.insert(pref);
        }
    }
}
