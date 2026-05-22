package com.jupin.server.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.jupin.common.constant.DbFieldConstant;
import com.jupin.common.constant.ErrorConstant;
import com.jupin.common.constant.RedisKeyConstant;
import com.jupin.common.constant.RoleConstant;
import com.jupin.common.exception.BaseException;
import com.jupin.common.utils.JwtUtil;
import com.jupin.pojo.dto.LoginRequest;
import com.jupin.pojo.dto.RegisterRequest;
import com.jupin.pojo.dto.UserUpdateRequest;
import com.jupin.pojo.entity.User;
import com.jupin.server.mapper.UserMapper;
import com.jupin.server.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate stringRedis;

    @Override
    @Transactional
    public User register(RegisterRequest request) {
        if (userMapper.selectCount(new QueryWrapper<User>().eq(DbFieldConstant.PHONE, request.getPhone())) > 0) {
            throw new BaseException(ErrorConstant.PHONE_REGISTERED);
        }

        int roleVal = switch (request.getRole()) {
            case "player" -> RoleConstant.PLAYER;
            case "shop" -> RoleConstant.SHOP;
            default -> throw new BaseException(ErrorConstant.INVALID_ROLE);
        };

        User user = User.builder()
                .phone(request.getPhone())
                .password(passwordEncoder.encode(request.getPassword()))
                .nickname(request.getNickname())
                .gender(request.getGender())
                .role(roleVal)
                .city(request.getCity())
                .creditScore(100)
                .status(1)
                .build();
        userMapper.insert(user);
        return user;
    }

    @Override
    public User login(LoginRequest request) {
        User user = userMapper.selectOne(new QueryWrapper<User>().eq(DbFieldConstant.PHONE, request.getPhone()));
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BaseException(ErrorConstant.ACCOUNT_OR_PASSWORD_ERROR);
        }
        if (user.getStatus() == 0) {
            throw new BaseException(ErrorConstant.ACCOUNT_DISABLED);
        }
        user.setLastLoginTime(LocalDateTime.now());
        userMapper.updateById(user);
        return user;
    }

    @Override
    public void logout(String token) {
        long ttl = 7200000;
        stringRedis.opsForValue().set(RedisKeyConstant.JWT_BLACKLIST_PREFIX + token, "1", ttl, TimeUnit.MILLISECONDS);
    }

    @Override
    public String refreshToken(String refreshToken) {
        if (!jwtUtil.validateToken(refreshToken)) {
            throw new BaseException(ErrorConstant.REFRESH_TOKEN_INVALID);
        }
        Long userId = jwtUtil.getUserIdFromToken(refreshToken);
        String cached = stringRedis.opsForValue().get(RedisKeyConstant.REFRESH_TOKEN_PREFIX + userId);
        if (cached == null || !cached.equals(refreshToken)) {
            throw new BaseException(ErrorConstant.REFRESH_TOKEN_REVOKED);
        }

        User user = userMapper.selectById(userId);
        if (user == null) throw new BaseException(ErrorConstant.USER_NOT_FOUND);

        return jwtUtil.generateAccessToken(userId, user.getPhone(), user.getRole());
    }

    @Override
    public User getCurrentUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) throw new BaseException(ErrorConstant.USER_NOT_FOUND);
        return user;
    }

    @Override
    @Transactional
    public User updateUser(Long userId, UserUpdateRequest request) {
        User existing = userMapper.selectById(userId);
        if (existing == null) throw new BaseException(ErrorConstant.USER_NOT_FOUND);
        BeanUtil.copyProperties(request, existing, CopyOptions.create().ignoreNullValue());
        userMapper.updateById(existing);
        return existing;
    }
}
