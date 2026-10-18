package com.crane.modules.system.dto;

import lombok.Data;

/**
 * 逆地理编码响应（服务端代理封装，屏蔽第三方原始报文，前端不接触地图服务密钥）。
 */
@Data
public class AmapRegeoResponse {

    /** 结构化完整地址 */
    private String formattedAddress;

    /** 省份 */
    private String province;

    /** 城市（直辖市场景可能缺失，服务端回退省份） */
    private String city;

    /** 区县 */
    private String district;

    /** 街道 / 乡镇 */
    private String township;

    /** 行政区划编码 */
    private String adcode;

    /** 输入经度（回显） */
    private Double lng;

    /** 输入纬度（回显） */
    private Double lat;
}
