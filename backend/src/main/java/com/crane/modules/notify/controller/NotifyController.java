package com.crane.modules.notify.controller;

import com.crane.common.auth.JwtUtil;
import com.crane.common.auth.TokenUserChecker;
import com.crane.common.exception.BizException;
import com.crane.common.exception.ErrorCode;
import com.crane.common.result.ApiResult;
import com.crane.modules.notify.entity.NotificationRecord;
import com.crane.modules.notify.service.NotifyService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * 管理端事件通知接口。
 * SSE 流式端点 /notify/stream 免 Authorization 头（EventSource 无法自定义请求头），
 * 改由 query 参数传令牌并在控制器内自行校验（WebConfig 白名单排除）。
 */
@RestController
@RequestMapping("/api/v1/notify")
@RequiredArgsConstructor
public class NotifyController {

    private final NotifyService notifyService;
    private final JwtUtil jwtUtil;
    private final ObjectProvider<TokenUserChecker> userChecker;

    /** SSE 事件流（仅管理员）：query 传 token，连接后实时接收工单事件推送 */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam(required = false) String token) {
        if (token == null || token.isBlank()) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        Claims claims;
        try {
            claims = jwtUtil.parse(token.trim());
        } catch (Exception e) {
            throw new BizException(ErrorCode.TOKEN_INVALID);
        }
        Long userId = Long.valueOf(claims.getSubject());
        TokenUserChecker checker = userChecker.getIfAvailable();
        if (checker != null) {
            Long forceLogoutAt = checker.forceLogoutAt(userId);
            if (forceLogoutAt != null && claims.getIssuedAt() != null
                    && claims.getIssuedAt().getTime() < forceLogoutAt) {
                throw new BizException(ErrorCode.TOKEN_REVOKED);
            }
        }
        if (!"ADMIN".equals(claims.get("role", String.class))) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        return notifyService.subscribe();
    }

    /** 最近通知列表（仅管理员）：SSE 断连重连后的补偿拉取 */
    @GetMapping("/admin/recent")
    public ApiResult<Map<String, Object>> recent(@RequestParam(defaultValue = "20") int limit) {
        requireAdmin();
        List<NotificationRecord> list = notifyService.recent(limit);
        return ApiResult.ok(Map.of("list", list));
    }

    private void requireAdmin() {
        com.crane.common.auth.AuthContext ctx = com.crane.common.auth.AuthContext.get();
        if (ctx == null || !ctx.isAdmin()) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
    }
}
