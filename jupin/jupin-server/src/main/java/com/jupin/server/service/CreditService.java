package com.jupin.server.service;

import com.jupin.pojo.entity.CreditLog;

import java.util.List;

public interface CreditService {
    int getScore(Long userId);
    void deduct(Long userId, int change, String reason);
    void add(Long userId, int change, String reason);
    List<CreditLog> getLog(Long userId, int page, int size);

    record CreditRankVO(Long userId, String nickname, Integer score) {}
}
