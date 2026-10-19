package com.crane.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 多角色账号选择本次进入身份（API-AUTH-007）。
 */
@Data
public class SelectRoleRequest {

    /** 目标角色码：ADMIN / DRIVER / CUSTOMER，须在账号已有角色集合内 */
    @NotBlank(message = "角色不能为空")
    private String role;
}
