package com.crane.modules.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crane.modules.system.entity.SysConfig;
import com.crane.modules.system.mapper.SysConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 系统配置服务：键值读取（带进程内缓存，写穿透），供系统名称等低频读参数使用。
 * 业务参数（T 值、阈值）由各域直接经本服务读取，避免散落常量。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemConfigService {

    private final SysConfigMapper sysConfigMapper;

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public String get(String key) {
        return cache.computeIfAbsent(key, k -> {
            SysConfig cfg = sysConfigMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
                    .eq(SysConfig::getConfigKey, k));
            return cfg == null ? null : cfg.getConfigValue();
        });
    }

    public String getOrDefault(String key, String defaultValue) {
        String v = get(key);
        return v == null ? defaultValue : v;
    }

    public int getInt(String key, int defaultValue) {
        String v = get(key);
        try {
            return v == null ? defaultValue : Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            log.warn("配置键 {} 非整数值: {}", key, v);
            return defaultValue;
        }
    }

    public Map<String, String> getByGroup(String groupName) {
        Map<String, String> result = new HashMap<>();
        List<SysConfig> list = sysConfigMapper.selectList(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getGroupName, groupName));
        for (SysConfig cfg : list) {
            result.put(cfg.getConfigKey(), cfg.getConfigValue());
            cache.put(cfg.getConfigKey(), cfg.getConfigValue());
        }
        return result;
    }

    public void update(String key, String value) {
        SysConfig cfg = sysConfigMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getConfigKey, key));
        if (cfg == null) {
            return;
        }
        cfg.setConfigValue(value);
        sysConfigMapper.updateById(cfg);
        cache.put(key, value);
    }
}
