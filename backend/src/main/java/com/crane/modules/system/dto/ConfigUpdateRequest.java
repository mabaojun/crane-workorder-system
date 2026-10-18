package com.crane.modules.system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 配置项更新请求（API-SYS-002）。
 */
@Data
public class ConfigUpdateRequest {

    @NotBlank(message = "configValue 不能为空")
    private String configValue;
}
