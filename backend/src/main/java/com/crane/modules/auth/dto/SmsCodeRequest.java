package com.crane.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 发送短信验证码请求（API-AUTH-003）。
 */
@Data
public class SmsCodeRequest {

    /** 11 位手机号 */
    @NotBlank(message = "phone 不能为空")
    @Pattern(regexp = "^1\\d{10}$", message = "phone 须为 11 位手机号")
    private String phone;

    /** 场景：LOGIN 登录 / BIND_PHONE 绑定手机号 */
    @NotBlank(message = "scene 不能为空")
    private String scene;
}
