package com.crane.modules.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户表（02 §3.2.1）。四角色统一账号；微信授权登录 + 绑定预留手机号；
 * 后台/大屏账号可用密码登录（API-AUTH-004）。
 */
@Data
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String phone;

    private String password;

    private String openId;

    private String unionId;

    private String nickName;

    private String avatarUrl;

    private Long roleId;

    private Long staffId;

    private Long customerId;

    /** PENDING_BIND 待绑定 / ACTIVE 已激活 / FROZEN 冻结 / DISABLED 停用 */
    private String status;

    private LocalDateTime boundAt;

    private LocalDateTime lastLoginAt;

    private LocalDateTime forceLogoutAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Long createdBy;

    private Long updatedBy;

    @TableLogic
    private Integer deleted;
}
