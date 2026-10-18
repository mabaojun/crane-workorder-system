package com.crane.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 短信验证码直登请求。扩展接口：H5/小程序验证码直登，MVP 阶段主用登录方式。
 */
@Data
public class SmsLoginRequest {

    /** 11 位手机号 */
    @NotBlank(message = "phone 不能为空")
    @Pattern(regexp = "^1\\d{10}$", message = "phone 须为 11 位手机号")
    private String phone;

    /** 6 位短信验证码 */
    @NotBlank(message = "smsCode 不能为空")
    private String smsCode;
}
