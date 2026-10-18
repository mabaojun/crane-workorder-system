package com.crane.modules.attendance.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务结束（最终完工签退）请求体（API-ATT-003），结构同签到。
 */
@Data
public class TaskEndRequest {

    /** 工单 id，必填 */
    private Long orderId;

    /** 签退定位经度，必填 */
    private BigDecimal longitude;

    /** 签退定位纬度，必填 */
    private BigDecimal latitude;

    /** 现场照片私有资源路径，至少 1 张 */
    private List<String> photoUrls;

    /** 实际采集时间，弱网补传时保留原值，可空 */
    private LocalDateTime capturedAt;

    /** 弱网补传标记，默认 false；决定 upload_mode 取值 */
    private Boolean offlineSync;
}
