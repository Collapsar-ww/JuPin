package com.jupin.server.interceptor;

import com.jupin.common.context.BaseContext;
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
            "/api/auth/register", "/api/auth/login", "/api/auth/refresh",
            "/api/player/pool/list", "/api/player/pool/{id}", "/api/player/script/list",
            "/api/shop/search", "/api/shop/script/list",
            "/api/admin/script/list",
            "/swagger-ui", "/v3/api-docs", "/ws"
    );

    private static final String BLACKLIST_PREFIX = "jwt:blacklist:";

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
        if (Boolean.TRUE.equals(stringRedis.hasKey(BLACKLIST_PREFIX + token))) {
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
            response.getWriter().write("{\"code\":403,\"message\":\"无权限访问\"}");
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
        if (path.startsWith("/api/auth/")) return true; // auth always ok after auth
        if (path.startsWith("/api/player/") && role == 0) return true;
        if (path.startsWith("/api/shop/") && role == 1) return true;
        if (path.startsWith("/api/admin/") && role == 2) return true;
        return false;
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
