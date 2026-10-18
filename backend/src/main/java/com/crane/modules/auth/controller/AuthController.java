package com.crane.modules.auth.controller;

import com.crane.common.result.ApiResult;
import com.crane.modules.auth.dto.BindPhoneRequest;
import com.crane.modules.auth.dto.LoginResponse;
import com.crane.modules.auth.dto.MeResponse;
import com.crane.modules.auth.dto.PasswordLoginRequest;
import com.crane.modules.auth.dto.SmsCodeRequest;
import com.crane.modules.auth.dto.SmsCodeResponse;
import com.crane.modules.auth.dto.SmsLoginRequest;
import com.crane.modules.auth.dto.WechatLoginRequest;
import com.crane.modules.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证域接口（03 §3.1）。/api/v1/auth/** 整体在拦截器白名单内，
 * 需登录的接口（bind-phone / logout / me）在服务层自行解析 Bearer 令牌。
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * API-AUTH-004 账号密码登录（后台与大屏）。
     * X-Client 标识客户端（admin / screen / mp 等），密码登录开关仅豁免 admin 与 screen。
     */
    @PostMapping("/password-login")
    public ApiResult<LoginResponse> passwordLogin(@Valid @RequestBody PasswordLoginRequest req,
                                                  @RequestHeader(value = "X-Client", required = false) String clientType) {
        return ApiResult.ok(authService.passwordLogin(req, clientType));
    }

    /** API-AUTH-003 发送登录 / 绑定验证码 */
    @PostMapping("/sms-code")
    public ApiResult<SmsCodeResponse> sendSmsCode(@Valid @RequestBody SmsCodeRequest req) {
        return ApiResult.ok(authService.sendSmsCode(req));
    }

    /** 短信验证码直登（H5/小程序，MVP 主用） */
    @PostMapping("/sms-login")
    public ApiResult<LoginResponse> smsLogin(@Valid @RequestBody SmsLoginRequest req) {
        return ApiResult.ok(authService.smsLogin(req));
    }

    /** API-AUTH-001 微信授权登录（MVP 通道未配置，返回 10006） */
    @PostMapping("/wechat-login")
    public ApiResult<LoginResponse> wechatLogin(@Valid @RequestBody WechatLoginRequest req) {
        return ApiResult.ok(authService.wechatLogin(req));
    }

    /** API-AUTH-002 绑定手机号并激活账号（MVP 仅支持已登录 Bearer 凭据） */
    @PostMapping("/bind-phone")
    public ApiResult<LoginResponse> bindPhone(@Valid @RequestBody BindPhoneRequest req,
                                              HttpServletRequest request) {
        Long userId = authService.resolveUserId(request, true);
        return ApiResult.ok(authService.bindPhone(userId, req));
    }

    /** API-AUTH-005 登出并失效令牌；重复登出幂等成功 */
    @PostMapping("/logout")
    public ApiResult<Void> logout(HttpServletRequest request) {
        // 不校验强制下线时间戳，保证重复登出仍返回成功
        Long userId = authService.resolveUserId(request, false);
        authService.logout(userId);
        return ApiResult.ok();
    }

    /** API-AUTH-006 当前用户与权限码 */
    @GetMapping("/me")
    public ApiResult<MeResponse> me(HttpServletRequest request) {
        Long userId = authService.resolveUserId(request, true);
        return ApiResult.ok(authService.me(userId));
    }
}
