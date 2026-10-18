package com.crane.modules.attendance.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 签到请求体（API-ATT-001）。
 */
@Data
public class CheckInRequest {

    /** 工单 id，必填 */
    private Long orderId;

    /** 实时定位经度，必填 */
    private BigDecimal longitude;

    /** 实时定位纬度，必填 */
    private BigDecimal latitude;

    /** 现场照片私有资源路径，至少 1 张 */
    private List<String> photoUrls;

    /** 实际采集时间，弱网补传时保留原值，可空则服务端以打卡时间兜底语义留空 */
    private LocalDateTime capturedAt;

    /** 弱网补传标记，默认 false；决定 upload_mode 取值（ONLINE / OFFLINE_RETRY） */
    private Boolean offlineSync;
}
