package com.crane.modules.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理员重置账号密码请求。
 */
@Data
public class PasswordResetRequest {

    /** 新密码，BCrypt 加密后存储 */
    @NotBlank(message = "password 不能为空")
    @Size(min = 6, max = 64, message = "password 长度须为 6 到 64 位")
    private String password;
}
