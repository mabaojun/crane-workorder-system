package com.crane.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 微信授权登录请求（API-AUTH-001）。MVP 微信通道未开通，接口结构先行预留。
 */
@Data
public class WechatLoginRequest {

    /** 微信登录 code，单次有效 */
    @NotBlank(message = "code 不能为空")
    private String code;

    /** 多端标识：admin / h5 / mp / screen */
    @NotBlank(message = "client 不能为空")
    private String client;
}
