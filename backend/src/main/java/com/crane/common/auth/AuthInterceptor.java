package com.crane.common.auth;

import com.crane.common.exception.BizException;
import com.crane.common.exception.ErrorCode;
import com.crane.common.result.ApiResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * 鉴权拦截器，契约见 03 §4.1：
 * 令牌存在性 → 签名与过期 → 强制下线比对 → 注入上下文。
 * 拦截器抛出的异常不经过 @RestControllerAdvice，因此失败时直接写出统一信封 JSON。
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<TokenUserChecker> userChecker;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return reject(response, new BizException(ErrorCode.UNAUTHORIZED));
        }
        Claims claims;
        try {
            claims = jwtUtil.parse(header.substring(7).trim());
        } catch (Exception e) {
            return reject(response, new BizException(ErrorCode.TOKEN_INVALID));
        }
        Long userId = Long.valueOf(claims.getSubject());
        // 强制下线校验：令牌签发时间早于 force_logout_at 即失效（10004）
        TokenUserChecker checker = userChecker.getIfAvailable();
        if (checker != null) {
            Long forceLogoutAt = checker.forceLogoutAt(userId);
            if (forceLogoutAt != null && claims.getIssuedAt() != null
                    && claims.getIssuedAt().getTime() < forceLogoutAt) {
                return reject(response, new BizException(ErrorCode.TOKEN_REVOKED));
            }
        }
        AuthContext ctx = new AuthContext();
        ctx.setUserId(userId);
        ctx.setRoleCode(claims.get("role", String.class));
        // 回填员工/客户绑定关系，供各域做数据范围过滤（司机仅本人、客户仅本人关联）
        Long[] binding = checker != null ? checker.resolveBinding(userId) : null;
        if (binding != null && binding.length >= 2) {
            ctx.setStaffId(binding[0]);
            ctx.setCustomerId(binding[1]);
        }
        AuthContext.set(ctx);
        request.setAttribute("userId", userId);
        request.setAttribute("role", ctx.getRoleCode());
        return true;
    }

    private boolean reject(HttpServletResponse response, BizException e) throws Exception {
        ApiResult<Void> result = ApiResult.fail(e.getCode(), e.getMessage());
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(result));
        return false;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        AuthContext.clear();
    }
}
