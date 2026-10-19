package com.crane.modules.auth.dto;

import lombok.Data;

import java.util.List;

/**
 * 当前用户信息响应（API-AUTH-006）。
 */
@Data
public class MeResponse {

    private Long userId;

    /** 角色码：ADMIN / DRIVER / CUSTOMER（当前令牌所选身份） */
    private String role;

    /** 账号可选身份列表（一人多角色时供小程序切换身份） */
    private List<RoleOption> availableRoles;

    /** 用户昵称，取 sys_user.nick_name */
    private String name;

    private String phone;

    /** 关联员工档案 id，未绑定为 null */
    private Long staffId;

    /** 关联客户 id，未绑定为 null */
    private Long customerId;

    /** 客户联系人账号绑定的联系人 id（企业多联系人提报身份），非联系人账号为 null */
    private Long contactId;

    /** 所属客户名称（企业主体展示用），无客户绑定为 null */
    private String customerName;

    /** 所属客户类型：COMPANY / PERSON，无客户绑定为 null */
    private String customerType;

    /** 数据范围：ALL / SELF_STAFF / SELF_CUSTOMER */
    private String dataScope;

    /** 权限码列表 */
    private List<String> permissions;
}
