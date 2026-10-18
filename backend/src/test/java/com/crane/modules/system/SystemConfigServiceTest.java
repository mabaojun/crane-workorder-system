package com.crane.modules.system;

import com.crane.modules.system.entity.SysConfig;
import com.crane.modules.system.mapper.SysConfigMapper;
import com.crane.modules.system.service.SystemConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 系统配置服务单元测试：首次读取落库、二次读取走缓存、更新写穿透缓存。
 */
@ExtendWith(MockitoExtension.class)
class SystemConfigServiceTest {

    private static final String KEY = "site.name";

    @Mock
    private SysConfigMapper sysConfigMapper;

    @InjectMocks
    private SystemConfigService systemConfigService;

    /** 首次读取查库，二次读取命中进程内缓存，不再查库 */
    @Test
    void getHitsCacheAfterFirstRead() {
        when(sysConfigMapper.selectOne(any())).thenReturn(config("吊装管理系统"));

        assertEquals("吊装管理系统", systemConfigService.get(KEY));
        assertEquals("吊装管理系统", systemConfigService.get(KEY));
        verify(sysConfigMapper, times(1)).selectOne(any());
    }

    /** 更新后新值写入缓存，后续读取直接取缓存值 */
    @Test
    void updateWritesThroughToCache() {
        when(sysConfigMapper.selectOne(any())).thenReturn(config("old-value"));

        systemConfigService.update(KEY, "new-value");

        ArgumentCaptor<SysConfig> captor = ArgumentCaptor.forClass(SysConfig.class);
        verify(sysConfigMapper).updateById(captor.capture());
        assertEquals("new-value", captor.getValue().getConfigValue());
        assertEquals("new-value", systemConfigService.get(KEY));
        // selectOne 仅 update 内部触发一次，get 走缓存不再查库
        verify(sysConfigMapper, times(1)).selectOne(any());
    }

    private SysConfig config(String value) {
        SysConfig cfg = new SysConfig();
        cfg.setId(1L);
        cfg.setConfigKey(KEY);
        cfg.setConfigValue(value);
        return cfg;
    }
}
