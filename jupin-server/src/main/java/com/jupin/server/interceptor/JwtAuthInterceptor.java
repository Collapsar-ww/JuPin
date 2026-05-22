package com.jupin.server.interceptor;

import com.jupin.common.context.BaseContext;
import com.jupin.common.constant.ApiPathConstant;
import com.jupin.common.constant.ErrorConstant;
import com.jupin.common.constant.JwtConstant;
import com.jupin.common.constant.RedisKeyConstant;
import com.jupin.common.constant.RoleConstant;
import com.jupin.common.utils.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final StringRedisTemplate stringRedis;

    /** 无需登录的公开路径 */
    private static final List<String> PUBLIC_PATHS = Arrays.asList(
            ApiPathConstant.API_AUTH + "/register", ApiPathConstant.API_AUTH + "/login", ApiPathConstant.API_AUTH + "/refresh",
            ApiPathConstant.API_PLAYER + "/pool/list", ApiPathConstant.API_PLAYER + "/pool/{id}", ApiPathConstant.API_PLAYER + "/script/list",
            ApiPathConstant.API_SHOP + "/search", ApiPathConstant.API_SHOP + "/script/list",
            ApiPathConstant.API_ADMIN + "/script/list",
            ApiPathConstant.SWAGGER_UI, ApiPathConstant.API_DOCS, ApiPathConstant.WS
    );

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();

        // 跳过公开路径
        if (isPublicPath(path)) return true;

        String token = extractToken(request);
        if (!StringUtils.hasText(token) || !jwtUtil.validateToken(token)) {
            response.setStatus(401);
            response.getWriter().write("{\"code\":401,\"message\":\"未登录或Token已过期\"}");
            return false;
        }

        // 检查黑名单
        if (Boolean.TRUE.equals(stringRedis.hasKey(RedisKeyConstant.JWT_BLACKLIST_PREFIX + token))) {
            response.setStatus(401);
            response.getWriter().write("{\"code\":401,\"message\":\"Token已被吊销\"}");
            return false;
        }

        Long userId = jwtUtil.getUserIdFromToken(token);
        Integer role = jwtUtil.getRoleFromToken(token);
        BaseContext.setCurrentId(userId);
        BaseContext.setCurrentRole(role);

        // 角色路径匹配校验
        if (!checkRolePath(path, role)) {
            response.setStatus(403);
            response.getWriter().write("{\"code\":403,\"message\":\"" + ErrorConstant.ACCESS_DENIED + "\"}");
            BaseContext.remove();
            return false;
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        BaseContext.remove();
    }

    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream().anyMatch(p -> {
            // 简单通配：/api/pool/123 → /api/pool/{id}
            String regex = p.replace("{id}", "\\d+").replace("/**", "/.*");
            return path.matches(regex);
        });
    }

    private boolean checkRolePath(String path, Integer role) {
        if (path.startsWith(ApiPathConstant.API_AUTH_PATTERN)) return true; // auth always ok after auth
        if (path.startsWith(ApiPathConstant.API_PLAYER_PATTERN) && role == RoleConstant.PLAYER) return true;
        if (path.startsWith(ApiPathConstant.API_SHOP_PATTERN) && role == RoleConstant.SHOP) return true;
        if (path.startsWith(ApiPathConstant.API_ADMIN_PATTERN) && role == RoleConstant.ADMIN) return true;
        return false;
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(JwtConstant.AUTHORIZATION);
        if (StringUtils.hasText(header) && header.startsWith(JwtConstant.BEARER_PREFIX)) {
            return header.substring(JwtConstant.BEARER_PREFIX.length());
        }
        return null;
    }
}
