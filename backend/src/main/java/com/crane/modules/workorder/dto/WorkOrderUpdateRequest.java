package com.crane.modules.workorder.dto;

import jakarta.validation.constraints.DecimalMin;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * API-WO-004 修改工单请求体（PATCH，未传字段不变）。
 * 仅管理员可用；改派司机 / 车辆与变更约定时间触发对应业务校验。
 */
@Data
public class WorkOrderUpdateRequest {

    /** 任务名称 */
    private String taskName;

    /** 改派车辆 */
    private Long vehicleId;

    /** 改派司机 */
    private Long staffId;

    /** 变更约定服务时间 */
    private LocalDateTime planStartAt;

    /** 变更地点 */
    private String address;

    /** 与 latitude 成对 */
    private BigDecimal longitude;

    /** 与 longitude 成对 */
    private BigDecimal latitude;

    /** 复核后重量（吨） */
    private BigDecimal cargoWeightTon;

    /** 吊车吨位 */
    private BigDecimal craneTonnage;

    /** 预计使用时长（天），选填，仅备注提示 */
    @DecimalMin(value = "0.5", message = "estimatedDays 最小 0.5 天")
    private BigDecimal estimatedDays;

    /** 施工要求 */
    private String requirement;

    private String remark;

    /** 预计开始时间早于当前时间时的二次确认标记，默认 false */
    private Boolean confirmPast;
}
