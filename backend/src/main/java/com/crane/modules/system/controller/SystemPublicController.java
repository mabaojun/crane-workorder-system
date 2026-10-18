package com.crane.modules.system.controller;

import com.crane.common.result.ApiResult;
import com.crane.modules.system.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 健康检查与公开配置。两者均免鉴权（WebConfig 白名单）。
 * public-config 返回系统名称等品牌信息：系统名称在后台「系统配置」中修改后全端生效，
 * 支持整套系统复制给其他公司时换名。
 */
@RestController
@RequestMapping("/api/v1/system")
@RequiredArgsConstructor
public class SystemPublicController {

    private final SystemConfigService systemConfigService;

    @GetMapping("/health")
    public ApiResult<Map<String, Object>> health() {
        return ApiResult.ok(Map.of(
                "status", "UP",
                "service", "crane-backend",
                "version", "0.1.0"
        ));
    }

    @GetMapping("/public-config")
    public ApiResult<Map<String, String>> publicConfig() {
        return ApiResult.ok(systemConfigService.getByGroup("system"));
    }
}
