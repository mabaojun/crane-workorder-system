package com.crane.modules.demand.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 需求单转工单入参（管理端）。
 * 执行司机、车辆、计划开始时间、吨位、货重与备注均必填，复核后落工单。
 */
@Data
public class DemandConvertRequest {

    /** 执行司机（员工档案 id） */
    @NotNull(message = "执行司机不能为空")
    private Long staffId;

    /** 施工车辆 */
    @NotNull(message = "施工车辆不能为空")
    private Long vehicleId;

    /** 计划开始时间 */
    @NotNull(message = "计划开始时间不能为空")
    private LocalDateTime planStartAt;

    /** 吊车吨位（吨），复核确认值 */
    @NotNull(message = "吊车吨位不能为空")
    private BigDecimal craneTonnage;

    /** 实际吊装货物重量（吨），复核确认值 */
    @NotNull(message = "货物重量不能为空")
    private BigDecimal cargoWeightTon;

    /** 预计使用时长（天），选填，缺省带入需求单客户自估值，仅备注提示 */
    private BigDecimal estimatedDays;

    /** 任务备注 */
    @NotBlank(message = "任务备注不能为空")
    private String remark;
}
