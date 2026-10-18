package com.crane.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 账号密码登录请求（API-AUTH-004）。username 即手机号，仅后台与大屏通道使用。
 */
@Data
public class PasswordLoginRequest {

    /** 登录账号，即 sys_user.phone */
    @NotBlank(message = "username 不能为空")
    private String username;

    @NotBlank(message = "password 不能为空")
    private String password;
}
