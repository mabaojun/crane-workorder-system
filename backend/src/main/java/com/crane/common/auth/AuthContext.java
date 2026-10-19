package com.crane.common.auth;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 当前登录上下文（请求级）。由 AuthInterceptor 在鉴权通过后写入 ThreadLocal。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthContext {

    private Long userId;
    private String roleCode;
    private String dataScope;
    private Long staffId;
    private Long customerId;
    /** 客户联系人账号绑定的联系人 id（企业多联系人独立提报时标识提报人），非联系人账号为 null */
    private Long contactId;
    private List<String> permissions;

    public boolean isAdmin() {
        return "ADMIN".equals(roleCode);
    }

    public boolean isDriver() {
        return "DRIVER".equals(roleCode);
    }

    public boolean isCustomer() {
        return "CUSTOMER".equals(roleCode);
    }

    private static final ThreadLocal<AuthContext> HOLDER = new ThreadLocal<>();

    public static void set(AuthContext ctx) {
        HOLDER.set(ctx);
    }

    public static AuthContext get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
