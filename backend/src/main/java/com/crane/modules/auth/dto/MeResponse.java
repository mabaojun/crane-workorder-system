package com.crane.modules.auth.dto;

import lombok.Data;

import java.util.List;

/**
 * 当前用户信息响应（API-AUTH-006）。
 */
@Data
public class MeResponse {

    private Long userId;

    /** 角色码：ADMIN / DRIVER / CUSTOMER */
    private String role;

    /** 用户昵称，取 sys_user.nick_name */
    private String name;

    private String phone;

    /** 关联员工档案 id，未绑定为 null */
    private Long staffId;

    /** 关联客户 id，未绑定为 null */
    private Long customerId;

    /** 数据范围：ALL / SELF_STAFF / SELF_CUSTOMER */
    private String dataScope;

    /** 权限码列表 */
    private List<String> permissions;
}
