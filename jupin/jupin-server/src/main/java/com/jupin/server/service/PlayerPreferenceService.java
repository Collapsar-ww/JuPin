package com.jupin.server.service;

import com.jupin.pojo.dto.PreferenceRequest;
import com.jupin.pojo.vo.PreferenceVO;

public interface PlayerPreferenceService {
    PreferenceVO get(Long userId);
    void save(Long userId, PreferenceRequest request);
}
