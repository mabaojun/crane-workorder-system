package com.crane.modules.system.dto;

import lombok.Data;

/**
 * 关键词搜索 POI 响应（服务端代理封装，屏蔽第三方原始报文）。
 */
@Data
public class AmapPoiResponse {

    /** POI 名称 */
    private String name;

    /** 地址 */
    private String address;

    /** 经度 */
    private Double lng;

    /** 纬度 */
    private Double lat;

    /** 省份 */
    private String province;

    /** 城市 */
    private String city;

    /** 区县 */
    private String district;
}
