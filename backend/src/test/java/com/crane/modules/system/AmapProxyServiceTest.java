package com.crane.modules.system;

import com.crane.modules.system.dto.AmapPoiResponse;
import com.crane.modules.system.dto.AmapRegeoResponse;
import com.crane.modules.system.service.AmapProxyService;
import com.crane.modules.system.service.SystemConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 高德代理服务单元测试（mock 模式，不发起真实 HTTP）：
 * 覆盖 mock 开关判定与模拟逆地理 / 模拟 POI 数据结构。
 */
class AmapProxyServiceTest {

    private SystemConfigService systemConfigService;
    private AmapProxyService amapProxyService;

    @BeforeEach
    void setUp() {
        systemConfigService = mock(SystemConfigService.class);
        amapProxyService = new AmapProxyService(systemConfigService);
    }

    /** map.mock=true 时逆地理返回济南模拟点位并回显坐标 */
    @Test
    void regeoMock返回模拟点位数据() {
        when(systemConfigService.getOrDefault("map.mock", "true")).thenReturn("true");

        AmapRegeoResponse resp = amapProxyService.regeo(117.1201, 36.7112);

        assertEquals("山东省济南市历下区经十路 12345 号（模拟点位）", resp.getFormattedAddress());
        assertEquals("山东省", resp.getProvince());
        assertEquals("济南市", resp.getCity());
        assertEquals("历下区", resp.getDistrict());
        assertEquals("舜华路街道", resp.getTownship());
        assertEquals("370102", resp.getAdcode());
        assertEquals(117.1201, resp.getLng(), 0.0000001);
        assertEquals(36.7112, resp.getLat(), 0.0000001);
    }

    /** mock 开关关闭但 web_key 为空时仍走模拟数据 */
    @Test
    void webKey为空时仍走模拟数据() {
        when(systemConfigService.getOrDefault("map.mock", "true")).thenReturn("false");
        when(systemConfigService.get("map.amap.web_key")).thenReturn("  ");

        AmapRegeoResponse resp = amapProxyService.regeo(117.0, 36.7);

        assertEquals("370102", resp.getAdcode());
        assertEquals("济南市", resp.getCity());
    }

    /** mock 模式关键词搜索返回 3 条围绕参考坐标的模拟 POI */
    @Test
    void placeTextMock返回三条围绕坐标的模拟POI() {
        when(systemConfigService.getOrDefault("map.mock", "true")).thenReturn("true");

        List<AmapPoiResponse> pois = amapProxyService.placeText("加油站", "济南", 117.12, 36.71);

        assertEquals(3, pois.size());
        for (AmapPoiResponse poi : pois) {
            assertTrue(poi.getName().startsWith("模拟 POI-"));
            assertTrue(poi.getAddress().contains("模拟点位"));
            assertEquals("山东省", poi.getProvince());
            assertEquals("济南市", poi.getCity());
            assertEquals("历下区", poi.getDistrict());
            // 坐标围绕参考点 ±0.01 度内
            assertTrue(Math.abs(poi.getLng() - 117.12) <= 0.011);
            assertTrue(Math.abs(poi.getLat() - 36.71) <= 0.011);
        }
    }

    /** 未传坐标时模拟 POI 以济南兜底坐标展开 */
    @Test
    void placeTextMock未传坐标时使用兜底坐标() {
        when(systemConfigService.getOrDefault("map.mock", "true")).thenReturn("true");

        List<AmapPoiResponse> pois = amapProxyService.placeText("野外作业", null, null, null);

        assertEquals(3, pois.size());
        assertNotNull(pois.get(0).getLng());
        assertNotNull(pois.get(0).getLat());
        assertTrue(Math.abs(pois.get(0).getLng() - 117.12) <= 0.011);
        assertTrue(Math.abs(pois.get(0).getLat() - 36.71) <= 0.011);
    }
}
