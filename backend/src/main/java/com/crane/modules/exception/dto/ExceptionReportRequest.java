package com.crane.modules.exception.dto;

import lombok.Data;

import java.util.List;

/**
 * 异常上报请求体（API-EXC-001）。
 */
@Data
public class ExceptionReportRequest {

    /** 工单 id，必填 */
    private Long orderId;

    /** 异常类型：RAIN、PARTY_STOP、VEHICLE_FAULT、CUSTOM，必填 */
    private String type;

    /** 自定义原因标签文本，type = CUSTOM 时必填 */
    private String typeLabel;

    /** 异常描述 / 自定义原因明细，必填 */
    private String description;

    /** 配图私有资源路径，可选 */
    private List<String> imageUrls;
}
