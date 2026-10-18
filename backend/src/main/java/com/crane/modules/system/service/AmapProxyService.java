package com.crane.modules.system.service;

import com.crane.common.exception.BizException;
import com.crane.common.exception.ErrorCode;
import com.crane.modules.system.dto.AmapPoiResponse;
import com.crane.modules.system.dto.AmapRegeoResponse;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * 高德地图服务端代理：逆地理编码与关键词搜索。
 * 开启 map.mock 或未配置 map.amap.web_key 时返回模拟数据供联调；
 * 真实模式透传高德 REST（连接 / 读取超时均 5 秒），失败统一抛 AMAP_ERROR(50004)。
 */
@Slf4j
@Service
public class AmapProxyService {

    /** mock 开关配置键：true 时强制返回模拟数据 */
    private static final String KEY_MOCK = "map.mock";

    /** 高德 Web 服务 key 配置键（逆地理 / POI 搜索共用） */
    private static final String KEY_WEB = "map.amap.web_key";

    /** 高德 REST 逆地理编码地址 */
    private static final String REGEO_URL = "https://restapi.amap.com/v3/geocode/regeo";

    /** 高德 REST v5 关键词搜索地址 */
    private static final String PLACE_TEXT_URL = "https://restapi.amap.com/v5/place/text";

    /** 连接与读取超时（毫秒） */
    private static final int TIMEOUT_MILLIS = 5000;

    /** POI 搜索最大返回条数 */
    private static final int POI_LIMIT = 10;

    /** 模拟 POI 兜底坐标（济南市中心附近） */
    private static final double FALLBACK_LNG = 117.12;
    private static final double FALLBACK_LAT = 36.71;

    private final SystemConfigService systemConfigService;
    private final RestClient restClient;

    public AmapProxyService(SystemConfigService systemConfigService) {
        this.systemConfigService = systemConfigService;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(TIMEOUT_MILLIS);
        factory.setReadTimeout(TIMEOUT_MILLIS);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    /**
     * 逆地理编码：坐标转结构化地址，响应中回显输入坐标。
     */
    public AmapRegeoResponse regeo(double lng, double lat) {
        if (useMock()) {
            return mockRegeo(lng, lat);
        }
        try {
            URI uri = UriComponentsBuilder.fromUriString(REGEO_URL)
                    .queryParam("location", lng + "," + lat)
                    .queryParam("key", webKey())
                    .queryParam("extensions", "base")
                    .build()
                    .encode()
                    .toUri();
            JsonNode root = restClient.get().uri(uri).retrieve().body(JsonNode.class);
            if (root == null || !"1".equals(root.path("status").asText())) {
                log.warn("逆地理编码返回异常: {}", root == null ? "空响应" : root.path("info").asText());
                throw new BizException(ErrorCode.AMAP_ERROR);
            }
            JsonNode regeocode = root.path("regeocode");
            JsonNode component = regeocode.path("addressComponent");
            String province = text(component.path("province"), "");
            AmapRegeoResponse resp = new AmapRegeoResponse();
            resp.setFormattedAddress(text(regeocode.path("formatted_address"), ""));
            resp.setProvince(province);
            // 直辖市等场景 city 为空数组，回退省份
            resp.setCity(text(component.path("city"), province));
            resp.setDistrict(text(component.path("district"), ""));
            resp.setTownship(text(component.path("township"), ""));
            resp.setAdcode(text(component.path("adcode"), ""));
            resp.setLng(lng);
            resp.setLat(lat);
            return resp;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("逆地理编码调用失败: {}", e.getMessage());
            throw new BizException(ErrorCode.AMAP_ERROR);
        }
    }

    /**
     * 关键词 POI 搜索：传入坐标作为参考位置（可选），最多返回 10 条。
     */
    public List<AmapPoiResponse> placeText(String keywords, String city, Double lng, Double lat) {
        if (useMock()) {
            return mockPois(lng, lat);
        }
        try {
            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(PLACE_TEXT_URL)
                    .queryParam("keywords", keywords)
                    .queryParam("key", webKey());
            if (city != null && !city.isBlank()) {
                builder.queryParam("city", city.trim());
            }
            if (lng != null && lat != null) {
                builder.queryParam("location", lng + "," + lat);
            }
            JsonNode root = restClient.get().uri(builder.build().encode().toUri())
                    .retrieve().body(JsonNode.class);
            if (root == null || !"1".equals(root.path("status").asText())) {
                log.warn("关键词搜索返回异常: {}", root == null ? "空响应" : root.path("info").asText());
                throw new BizException(ErrorCode.AMAP_ERROR);
            }
            List<AmapPoiResponse> list = new ArrayList<>();
            for (JsonNode poi : root.path("pois")) {
                if (list.size() >= POI_LIMIT) {
                    break;
                }
                AmapPoiResponse item = new AmapPoiResponse();
                item.setName(text(poi.path("name"), ""));
                item.setAddress(text(poi.path("address"), ""));
                applyLocation(item, poi.path("location").asText(""));
                item.setProvince(text(poi.path("pname"), ""));
                item.setCity(text(poi.path("cityname"), ""));
                item.setDistrict(text(poi.path("adname"), ""));
                list.add(item);
            }
            return list;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("关键词搜索调用失败: {}", e.getMessage());
            throw new BizException(ErrorCode.AMAP_ERROR);
        }
    }

    /** mock 判定：map.mock=true 或 web_key 未配置均走模拟数据 */
    private boolean useMock() {
        if ("true".equalsIgnoreCase(systemConfigService.getOrDefault(KEY_MOCK, "true").trim())) {
            return true;
        }
        String webKey = systemConfigService.get(KEY_WEB);
        return webKey == null || webKey.isBlank();
    }

    /** 读取 Web 服务 key；useMock 已拦截空 key，此处兜底防裸调 */
    private String webKey() {
        String key = systemConfigService.get(KEY_WEB);
        if (key == null || key.isBlank()) {
            throw new BizException(ErrorCode.AMAP_ERROR);
        }
        return key;
    }

    /** 模拟逆地理结果：固定济南历下区模拟点位 */
    private AmapRegeoResponse mockRegeo(double lng, double lat) {
        AmapRegeoResponse resp = new AmapRegeoResponse();
        resp.setFormattedAddress("山东省济南市历下区经十路 12345 号（模拟点位）");
        resp.setProvince("山东省");
        resp.setCity("济南市");
        resp.setDistrict("历下区");
        resp.setTownship("舜华路街道");
        resp.setAdcode("370102");
        resp.setLng(lng);
        resp.setLat(lat);
        return resp;
    }

    /** 模拟 POI：围绕参考坐标 ±0.01 度分布；未传坐标时以济南兜底 */
    private List<AmapPoiResponse> mockPois(Double lng, Double lat) {
        double baseLng = lng == null ? FALLBACK_LNG : lng;
        double baseLat = lat == null ? FALLBACK_LAT : lat;
        List<AmapPoiResponse> list = new ArrayList<>();
        list.add(mockPoi("模拟 POI-某某路口", "山东省济南市历下区模拟路 1 号（模拟点位）", baseLng + 0.01, baseLat + 0.01));
        list.add(mockPoi("模拟 POI-某某停车场", "山东省济南市历下区模拟路 2 号（模拟点位）", baseLng - 0.01, baseLat + 0.01));
        list.add(mockPoi("模拟 POI-野外作业点", "山东省济南市历下区模拟路 3 号（模拟点位）", baseLng + 0.01, baseLat - 0.01));
        return list;
    }

    private AmapPoiResponse mockPoi(String name, String address, double lng, double lat) {
        AmapPoiResponse poi = new AmapPoiResponse();
        poi.setName(name);
        poi.setAddress(address);
        poi.setLng(round6(lng));
        poi.setLat(round6(lat));
        poi.setProvince("山东省");
        poi.setCity("济南市");
        poi.setDistrict("历下区");
        return poi;
    }

    /** 解析 "lng,lat" 坐标串，非法时不填充避免脏数据外发 */
    private void applyLocation(AmapPoiResponse poi, String location) {
        if (location == null || location.isBlank() || !location.contains(",")) {
            return;
        }
        String[] parts = location.split(",");
        try {
            poi.setLng(Double.parseDouble(parts[0].trim()));
            poi.setLat(Double.parseDouble(parts[1].trim()));
        } catch (NumberFormatException ignored) {
            // 坐标串异常时留空
        }
    }

    /** JsonNode 取文本：缺失 / null / 空数组返回兜底值（数组取首元素） */
    private String text(JsonNode node, String fallback) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return fallback;
        }
        if (node.isArray()) {
            return node.isEmpty() ? fallback : node.get(0).asText(fallback);
        }
        String value = node.asText();
        return value == null || value.isBlank() ? fallback : value;
    }

    /** 坐标保留 6 位小数，消除浮点尾差 */
    private double round6(double value) {
        return Math.round(value * 1_000_000) / 1_000_000.0;
    }
}
