package com.jupin.server.controller.auth;

import cn.hutool.core.bean.BeanUtil;
import com.jupin.common.result.Result;
import com.jupin.common.utils.JwtUtil;
import com.jupin.pojo.dto.LoginRequest;
import com.jupin.pojo.dto.RegisterRequest;
import com.jupin.pojo.entity.User;
import com.jupin.pojo.vo.LoginVO;
import com.jupin.pojo.vo.UserVO;
import com.jupin.server.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Tag(name = "认证")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate stringRedis;

    private static final String REFRESH_PREFIX = "refresh:";

    @Operation(summary = "用户注册")
    @PostMapping("/register")
    public Result<LoginVO> register(@Valid @RequestBody RegisterRequest request) {
        User user = userService.register(request);

        String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getPhone(), user.getRole());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId());
        cacheRefreshToken(user.getId(), refreshToken);

        return Result.success(new LoginVO(accessToken, refreshToken, BeanUtil.copyProperties(user, UserVO.class)));
    }

    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginRequest request) {
        User user = userService.login(request);

        String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getPhone(), user.getRole());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId());
        cacheRefreshToken(user.getId(), refreshToken);

        return Result.success(new LoginVO(accessToken, refreshToken, BeanUtil.copyProperties(user, UserVO.class)));
    }

    @Operation(summary = "退出登录  🔒")
    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        userService.logout(token);
        return Result.success();
    }

    @Operation(summary = "刷新 Access Token")
    @PostMapping("/refresh")
    public Result<Map<String, String>> refresh(@RequestHeader("Authorization") String authHeader) {
        String refreshToken = authHeader.replace("Bearer ", "");
        String newAccessToken = userService.refreshToken(refreshToken);
        return Result.success(Map.of("accessToken", newAccessToken));
    }

    private void cacheRefreshToken(Long userId, String refreshToken) {
        stringRedis.opsForValue().set(REFRESH_PREFIX + userId, refreshToken, 7, TimeUnit.DAYS);
    }
}
