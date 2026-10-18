package com.crane.modules.system.controller;

import com.crane.common.result.ApiResult;
import com.crane.modules.system.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
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

    /**
     * 公开配置：system 组全量 + 地图组仅下发前端 JSAPI 所需密钥（jsapi_key / security_jscode，
     * Web 服务 key 与 mock 开关不出网）+ 密码登录开关，供各端登录页与地图页预判展示。
     */
    @GetMapping("/public-config")
    public ApiResult<Map<String, String>> publicConfig() {
        Map<String, String> result = new HashMap<>(systemConfigService.getByGroup("system"));
        for (String key : List.of("map.amap.jsapi_key", "map.amap.security_jscode")) {
            String value = systemConfigService.get(key);
            if (value != null) {
                result.put(key, value);
            }
        }
        result.put("auth.password_login_enabled",
                systemConfigService.getOrDefault("auth.password_login_enabled", "true"));
        // 打卡定位/照片强制开关：移动端据此放宽签到 UI 拦截（后端为准）
        result.put("attendance.location_required",
                systemConfigService.getOrDefault("attendance.location_required", "false"));
        result.put("attendance.photo_required",
                systemConfigService.getOrDefault("attendance.photo_required", "false"));
        return ApiResult.ok(result);
    }
}
