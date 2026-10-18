package com.crane.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 绑定手机号请求（API-AUTH-002）。MVP 简化：仅支持已登录 Bearer 凭据调用。
 */
@Data
public class BindPhoneRequest {

    /** 11 位手机号，须与员工档案或客户预留手机号匹配 */
    @NotBlank(message = "phone 不能为空")
    @Pattern(regexp = "^1\\d{10}$", message = "phone 须为 11 位手机号")
    private String phone;

    /** 6 位短信验证码（场景 BIND_PHONE） */
    @NotBlank(message = "smsCode 不能为空")
    private String smsCode;
}
