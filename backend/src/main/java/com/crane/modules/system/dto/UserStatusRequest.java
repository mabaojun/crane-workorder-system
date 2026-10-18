package com.crane.modules.system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 管理员变更账号状态请求。
 */
@Data
public class UserStatusRequest {

    /** 目标状态：ACTIVE 激活 / FROZEN 冻结 / DISABLED 停用 */
    @NotBlank(message = "status 不能为空")
    private String status;
}
