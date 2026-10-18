package com.crane.modules.system.controller;

import com.crane.common.exception.BizException;
import com.crane.common.exception.ErrorCode;
import com.crane.common.result.ApiResult;
import com.crane.modules.system.dto.AmapPoiResponse;
import com.crane.modules.system.dto.AmapRegeoResponse;
import com.crane.modules.system.service.AmapProxyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 高德地图代理接口：服务端持有 Web 服务密钥，前端不接触第三方报文。
 * 拦截器默认要求登录（无额外权限码）；mock 模式下返回模拟数据便于联调。
 */
@RestController
@RequestMapping("/api/v1/system/amap")
@RequiredArgsConstructor
public class AmapProxyController {

    private final AmapProxyService amapProxyService;

    /** 逆地理编码：坐标转结构化地址 */
    @GetMapping("/regeo")
    public ApiResult<AmapRegeoResponse> regeo(@RequestParam(required = false) String lng,
                                              @RequestParam(required = false) String lat) {
        Double lngVal = parseCoordinate(lng);
        Double latVal = parseCoordinate(lat);
        if (lngVal == null || latVal == null) {
            throw new BizException(ErrorCode.PARAM_MISSING);
        }
        checkRange(lngVal, latVal);
        return ApiResult.ok(amapProxyService.regeo(lngVal, latVal));
    }

    /** 关键词 POI 搜索：keywords 必填，坐标可选作为参考位置，最多返回 10 条 */
    @GetMapping("/place/text")
    public ApiResult<List<AmapPoiResponse>> placeText(@RequestParam(required = false) String keywords,
                                                      @RequestParam(required = false) String city,
                                                      @RequestParam(required = false) String lng,
                                                      @RequestParam(required = false) String lat) {
        if (keywords == null || keywords.isBlank()) {
            throw new BizException(ErrorCode.PARAM_MISSING);
        }
        Double lngVal = parseCoordinate(lng);
        Double latVal = parseCoordinate(lat);
        // 坐标须成对出现，只传其一视为非法
        if (lngVal == null && latVal == null) {
            return ApiResult.ok(amapProxyService.placeText(keywords.trim(), city, null, null));
        }
        if (lngVal == null || latVal == null) {
            throw new BizException(ErrorCode.COORDINATE_INVALID);
        }
        checkRange(lngVal, latVal);
        return ApiResult.ok(amapProxyService.placeText(keywords.trim(), city, lngVal, latVal));
    }

    /** 解析坐标参数，未传返回 null，格式非法抛 20007 */
    private Double parseCoordinate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            throw new BizException(ErrorCode.COORDINATE_INVALID);
        }
    }

    /** 经度范围 [-180,180]、纬度范围 [-90,90]，越界抛 COORDINATE_INVALID */
    private void checkRange(double lngVal, double latVal) {
        if (lngVal < -180 || lngVal > 180 || latVal < -90 || latVal > 90) {
            throw new BizException(ErrorCode.COORDINATE_INVALID);
        }
    }
}
