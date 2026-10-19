package com.crane.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 微信手机号快速验证绑定请求（API-AUTH-002b）：
 * phoneCode 为小程序 getPhoneNumber 返回的一次性 code，由后端换取手机号。
 */
@Data
public class WechatBindPhoneRequest {

    /** 微信手机号快速验证 code，单次有效 */
    @NotBlank(message = "phoneCode 不能为空")
    private String phoneCode;
}
