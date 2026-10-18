package com.crane.modules.auth.dto;

import lombok.Data;

import java.util.List;

/**
 * 登录成功响应（API-AUTH-001/002/004 及短信直登共用）。
 */
@Data
public class LoginResponse {

    /** 令牌类型，固定 Bearer */
    private String tokenType = "Bearer";

    /** 业务访问令牌 */
    private String accessToken;

    /** 有效期（秒） */
    private long expiresIn;

    /** 角色码：ADMIN / DRIVER / CUSTOMER */
    private String role;

    /** 权限码列表，可为空 */
    private List<String> permissions;

    /** 是否已绑定手机号并激活 */
    private boolean bound;
}
