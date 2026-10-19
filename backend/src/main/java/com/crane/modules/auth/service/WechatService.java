package com.crane.modules.auth.service;

import com.crane.common.exception.BizException;
import com.crane.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 微信小程序登录凭据服务：用登录 code 换取 openid / unionid（jscode2session）。
 * 配置项 crane.wechat.appid / crane.wechat.secret，生产环境以 WECHAT_APPID / WECHAT_SECRET 注入。
 */
@Slf4j
@Service
public class WechatService {

    private static final String JSCODE2SESSION_URL = "https://api.weixin.qq.com/sns/jscode2session";

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${crane.wechat.appid:}")
    private String appid;

    @Value("${crane.wechat.secret:}")
    private String secret;

    /** 微信登录会话：openid 必返，unionid 仅在绑定开放平台时返回 */
    public record Session(String openId, String unionId) {
    }

    /** 以登录 code 换取 openid / unionid；未配置或换取失败抛出业务异常 */
    public Session code2Session(String code) {
        if (appid == null || appid.isBlank() || secret == null || secret.isBlank()) {
            throw new BizException(ErrorCode.WECHAT_CODE_INVALID,
                    "微信登录通道未配置，请设置 WECHAT_APPID / WECHAT_SECRET");
        }
        String url = UriComponentsBuilder.fromUriString(JSCODE2SESSION_URL)
                .queryParam("appid", appid)
                .queryParam("secret", secret)
                .queryParam("js_code", code)
                .queryParam("grant_type", "authorization_code")
                .encode(StandardCharsets.UTF_8)
                .toUriString();
        Map<?, ?> body;
        try {
            body = restTemplate.getForObject(url, Map.class);
        } catch (RestClientException e) {
            log.warn("微信 jscode2session 调用失败: {}", e.getMessage());
            throw new BizException(ErrorCode.SYSTEM_ERROR, "微信登录服务暂不可用，请稍后重试");
        }
        if (body == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "微信登录服务返回为空");
        }
        Object errcode = body.get("errcode");
        if (errcode != null && !"0".equals(String.valueOf(errcode))) {
            Object errmsgValue = body.get("errmsg");
            String errmsg = errmsgValue == null ? "未知错误" : String.valueOf(errmsgValue);
            log.warn("微信 jscode2session 返回错误 errcode={} errmsg={}", errcode, errmsg);
            throw new BizException(ErrorCode.WECHAT_CODE_INVALID, "微信登录失败：" + errmsg);
        }
        String openId = str(body.get("openid"));
        if (openId == null || openId.isBlank()) {
            throw new BizException(ErrorCode.WECHAT_CODE_INVALID, "微信登录未返回 openid");
        }
        return new Session(openId, str(body.get("unionid")));
    }

    private String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
