package com.crane.modules.attendance.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 地理计算服务：haversine 球面直线距离，与打卡范围校验配套使用。
 */
@Service
public class GeoService {

    /** 地球平均半径（米） */
    private static final double EARTH_RADIUS_METERS = 6371000.0;

    /**
     * 计算两点间 haversine 直线距离（米）。
     * 任一入参为空返回 -1，表示无法计算（由调用方决定是否按 0 处理或不拦截）。
     */
    public double haversine(BigDecimal lng1, BigDecimal lat1, BigDecimal lng2, BigDecimal lat2) {
        if (lng1 == null || lat1 == null || lng2 == null || lat2 == null) {
            return -1;
        }
        double lonA = Math.toRadians(lng1.doubleValue());
        double latA = Math.toRadians(lat1.doubleValue());
        double lonB = Math.toRadians(lng2.doubleValue());
        double latB = Math.toRadians(lat2.doubleValue());
        double deltaLat = latB - latA;
        double deltaLon = lonB - lonA;
        double h = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(latA) * Math.cos(latB) * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.min(1.0, Math.sqrt(h)));
    }
}
