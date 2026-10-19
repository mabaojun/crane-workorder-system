package com.crane.modules.auth.service;

import com.crane.common.exception.BizException;
import com.crane.common.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 微信小程序凭据服务：
 * 1. jscode2session：登录 code 换 openid / unionid；
 * 2. getuserphonenumber：手机号快速验证 code 换手机号（首次绑定免短信）。
 * 配置项 crane.wechat.appid / crane.wechat.secret，生产环境以 WECHAT_APPID / WECHAT_SECRET 注入。
 */
@Slf4j
@Service
public class WechatService {

    private static final String JSCODE2SESSION_URL = "https://api.weixin.qq.com/sns/jscode2session";
    private static final String ACCESS_TOKEN_URL = "https://api.weixin.qq.com/cgi-bin/token";
    private static final String GET_PHONE_URL = "https://api.weixin.qq.com/wxa/business/getuserphonenumber";
    private static final String PHONE_PATTERN = "^1\\d{10}$";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** access_token 进程内缓存；提前 5 分钟过期，避免临界点失效 */
    private volatile String accessToken;
    private volatile long accessTokenExpiresAt;

    @Value("${crane.wechat.appid:}")
    private String appid;

    @Value("${crane.wechat.secret:}")
    private String secret;

    /** 微信登录会话：openid 必返，unionid 仅在绑定开放平台时返回 */
    public record Session(String openId, String unionId) {
    }

    /** 以登录 code 换取 openid / unionid；未配置或换取失败抛出业务异常 */
    public Session code2Session(String code) {
        requireConfigured();
        String url = UriComponentsBuilder.fromUriString(JSCODE2SESSION_URL)
                .queryParam("appid", appid)
                .queryParam("secret", secret)
                .queryParam("js_code", code)
                .queryParam("grant_type", "authorization_code")
                .encode(StandardCharsets.UTF_8)
                .toUriString();
        // 微信接口返回 Content-Type: text/plain，不能直接反序列化为 Map，先取字符串再解析
        String resp;
        try {
            resp = restTemplate.getForObject(url, String.class);
        } catch (RestClientException e) {
            log.warn("微信 jscode2session 调用失败: {}", e.getMessage());
            throw new BizException(ErrorCode.SYSTEM_ERROR, "微信登录服务暂不可用，请稍后重试");
        }
        Map<?, ?> body = parseBody(resp, "jscode2session");
        Object errcode = body.get("errcode");
        if (errcode != null && !"0".equals(String.valueOf(errcode))) {
            String errmsg = messageOf(body);
            log.warn("微信 jscode2session 返回错误 errcode={} errmsg={}", errcode, errmsg);
            throw new BizException(ErrorCode.WECHAT_CODE_INVALID, "微信登录失败：" + errmsg);
        }
        String openId = str(body.get("openid"));
        if (openId == null || openId.isBlank()) {
            throw new BizException(ErrorCode.WECHAT_CODE_INVALID, "微信登录未返回 openid");
        }
        return new Session(openId, str(body.get("unionid")));
    }

    /**
     * 微信手机号快速验证：用小程序 getPhoneNumber 返回的一次性 code 换取手机号。
     *
     * @return 不带国家码的中国大陆 11 位手机号
     */
    public String getPhoneNumber(String phoneCode) {
        requireConfigured();
        Map<?, ?> body = requestPhoneNumber(phoneCode, getAccessToken(false));
        if (isAccessTokenInvalid(body)) {
            // access_token 失效时强制刷新并重试一次
            body = requestPhoneNumber(phoneCode, getAccessToken(true));
        }
        Object errcode = body.get("errcode");
        if (errcode != null && !"0".equals(String.valueOf(errcode))) {
            String errmsg = messageOf(body);
            log.warn("微信 getuserphonenumber 返回错误 errcode={} errmsg={}", errcode, errmsg);
            throw new BizException(ErrorCode.WECHAT_CODE_INVALID, "微信手机号获取失败：" + errmsg);
        }
        Object phoneInfoValue = body.get("phone_info");
        if (!(phoneInfoValue instanceof Map<?, ?> phoneInfo)) {
            throw new BizException(ErrorCode.WECHAT_CODE_INVALID, "微信未返回手机号");
        }
        String phone = str(phoneInfo.get("purePhoneNumber"));
        if (phone == null || phone.isBlank()) {
            phone = str(phoneInfo.get("phoneNumber"));
        }
        if (phone == null || !phone.matches(PHONE_PATTERN)) {
            throw new BizException(ErrorCode.WECHAT_CODE_INVALID, "微信返回的手机号格式不正确");
        }
        return phone;
    }

    /** 获取 access_token：优先使用内存缓存，forceRefresh 时强制重新获取 */
    private synchronized String getAccessToken(boolean forceRefresh) {
        long now = System.currentTimeMillis();
        if (!forceRefresh && accessToken != null && now < accessTokenExpiresAt) {
            return accessToken;
        }
        String url = UriComponentsBuilder.fromUriString(ACCESS_TOKEN_URL)
                .queryParam("grant_type", "client_credential")
                .queryParam("appid", appid)
                .queryParam("secret", secret)
                .encode(StandardCharsets.UTF_8)
                .toUriString();
        String resp;
        try {
            resp = restTemplate.getForObject(url, String.class);
        } catch (RestClientException e) {
            log.warn("微信 access_token 获取失败: {}", e.getMessage());
            throw new BizException(ErrorCode.SYSTEM_ERROR, "微信服务暂不可用，请稍后重试");
        }
        Map<?, ?> body = parseBody(resp, "access_token");
        Object errcode = body.get("errcode");
        if (errcode != null && !"0".equals(String.valueOf(errcode))) {
            String errmsg = messageOf(body);
            log.warn("微信 access_token 返回错误 errcode={} errmsg={}", errcode, errmsg);
            throw new BizException(ErrorCode.SYSTEM_ERROR, "微信服务暂不可用：" + errmsg);
        }
        String token = str(body.get("access_token"));
        if (token == null || token.isBlank()) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "微信未返回 access_token");
        }
        long expiresIn = 7200;
        try {
            String expiresInValue = str(body.get("expires_in"));
            if (expiresInValue != null && !expiresInValue.isBlank()) {
                expiresIn = Long.parseLong(expiresInValue);
            }
        } catch (NumberFormatException ignored) {
            // 非法 expires_in 按默认 2 小时处理
        }
        accessToken = token;
        accessTokenExpiresAt = now + Math.max(60, expiresIn - 300) * 1000L;
        return token;
    }

    /** 调用 getuserphonenumber 换取手机号 */
    private Map<?, ?> requestPhoneNumber(String phoneCode, String token) {
        String url = UriComponentsBuilder.fromUriString(GET_PHONE_URL)
                .queryParam("access_token", token)
                .encode(StandardCharsets.UTF_8)
                .toUriString();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String resp;
        try {
            resp = restTemplate.postForObject(url, new HttpEntity<>(Map.of("code", phoneCode), headers), String.class);
        } catch (RestClientException e) {
            log.warn("微信 getuserphonenumber 调用失败: {}", e.getMessage());
            throw new BizException(ErrorCode.SYSTEM_ERROR, "微信服务暂不可用，请稍后重试");
        }
        return parseBody(resp, "getuserphonenumber");
    }

    /** access_token 失效错误码：40001/40014/42001 */
    private boolean isAccessTokenInvalid(Map<?, ?> body) {
        String code = str(body.get("errcode"));
        return "40001".equals(code) || "40014".equals(code) || "42001".equals(code);
    }

    private Map<?, ?> parseBody(String resp, String api) {
        if (resp == null || resp.isBlank()) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "微信服务返回为空");
        }
        try {
            return objectMapper.readValue(resp, Map.class);
        } catch (JsonProcessingException e) {
            log.warn("微信 {} 响应解析失败: {}", api, resp);
            throw new BizException(ErrorCode.SYSTEM_ERROR, "微信服务返回异常");
        }
    }

    private void requireConfigured() {
        if (appid == null || appid.isBlank() || secret == null || secret.isBlank()) {
            throw new BizException(ErrorCode.WECHAT_CODE_INVALID,
                    "微信登录通道未配置，请设置 WECHAT_APPID / WECHAT_SECRET");
        }
    }

    private String messageOf(Map<?, ?> body) {
        Object errmsg = body.get("errmsg");
        return errmsg == null ? "未知错误" : String.valueOf(errmsg);
    }

    private String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
