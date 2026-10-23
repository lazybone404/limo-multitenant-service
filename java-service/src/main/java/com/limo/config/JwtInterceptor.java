package com.limo.config;

import com.limo.common.BizException;
import com.limo.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 登录鉴权拦截器：从请求头 Authorization: Bearer <token> 解析 userId，放入 UserContext。
 * 无 token/解析失败 → 抛 401。实现"多用户数据隔离 + 无状态鉴权"。
 */
@Component
@RequiredArgsConstructor
public class JwtInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse resp, Object handler) {
        // 放行那些不需要登录的接口（本场景 /api/auth/** 已在 WebConfig 排除）
        String header = req.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new BizException(401, "未登录");
        }
        try {
            Claims claims = jwtUtil.parse(header.substring(7));
            Long userId = Long.valueOf(claims.getSubject());
            UserContext.set(userId);
            return true;
        } catch (Exception e) {
            throw new BizException(401, "登录已过期");
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest req, HttpServletResponse resp, Object handler, Exception ex) {
        UserContext.clear();   // 请求结束清空 ThreadLocal，防泄漏
    }
}
