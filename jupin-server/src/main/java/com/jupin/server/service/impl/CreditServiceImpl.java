package com.jupin.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.jupin.common.exception.BaseException;
import com.jupin.pojo.entity.CreditLog;
import com.jupin.pojo.entity.User;
import com.jupin.server.mapper.CreditLogMapper;
import com.jupin.server.mapper.UserMapper;
import com.jupin.server.service.CreditService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CreditServiceImpl implements CreditService {

    private final UserMapper userMapper;
    private final CreditLogMapper creditLogMapper;

    @Override
    public int getScore(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) throw new BaseException("用户不存在");
        return user.getCreditScore();
    }

    @Override
    @Transactional
    public void deduct(Long userId, int change, String reason) {
        User user = userMapper.selectById(userId);
        if (user == null) throw new BaseException("用户不存在");
        int newScore = Math.max(0, user.getCreditScore() - Math.abs(change));
        user.setCreditScore(newScore);
        userMapper.updateById(user);

        CreditLog creditLog = CreditLog.builder()
                .userId(userId)
                .change(-Math.abs(change))
                .balance(newScore)
                .reason(reason)
                .build();
        creditLogMapper.insert(creditLog);
    }

    @Override
    @Transactional
    public void add(Long userId, int change, String reason) {
        User user = userMapper.selectById(userId);
        if (user == null) throw new BaseException("用户不存在");
        int newScore = Math.min(100, user.getCreditScore() + change);
        user.setCreditScore(newScore);
        userMapper.updateById(user);

        CreditLog creditLog = CreditLog.builder()
                .userId(userId)
                .change(change)
                .balance(newScore)
                .reason(reason)
                .build();
        creditLogMapper.insert(creditLog);
    }

    @Override
    public List<CreditLog> getLog(Long userId, int page, int size) {
        return creditLogMapper.selectList(new QueryWrapper<CreditLog>()
                .eq("user_id", userId)
                .orderByDesc("create_time")
                .last("LIMIT " + ((long) (page - 1) * size) + "," + size));
    }
}
