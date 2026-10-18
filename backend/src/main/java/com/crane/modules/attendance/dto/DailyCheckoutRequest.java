package com.crane.modules.attendance.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 当日签退请求体（API-ATT-002）。照片为可选字段，签退仅结束当日施工，不改变工单完成状态。
 */
@Data
public class DailyCheckoutRequest {

    /** 工单 id，必填 */
    private Long orderId;

    /** 签退定位经度，必填 */
    private BigDecimal longitude;

    /** 签退定位纬度，必填 */
    private BigDecimal latitude;

    /** 离场照片私有资源路径，可选 */
    private List<String> photoUrls;

    /** 实际采集时间，弱网补传时保留原值，可空 */
    private LocalDateTime capturedAt;

    /** 车辆是否离场 */
    private Boolean isLeftSite;

    /** 车辆是否回场；选回场则车辆状态置空闲 */
    private Boolean isReturned;

    /** 弱网补传标记，默认 false；决定 upload_mode 取值 */
    private Boolean offlineSync;
}
