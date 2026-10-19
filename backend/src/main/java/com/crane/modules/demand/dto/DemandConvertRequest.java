package com.crane.modules.demand.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 需求单转工单入参（管理端）。
 * 执行司机、车辆、计划开始时间与吨位必填；货物重量选填（客户提报可未填），复核后落工单。
 */
@Data
public class DemandConvertRequest {

    /** 执行司机（员工档案 id） */
    @NotNull(message = "执行司机不能为空")
    private Long staffId;

    /** 施工车辆 */
    @NotNull(message = "施工车辆不能为空")
    private Long vehicleId;

    /** 客户联系人（企业客户可改选具体联系人，缺省沿用需求单提报联系人） */
    private Long contactId;

    /** 计划开始时间 */
    @NotNull(message = "计划开始时间不能为空")
    private LocalDateTime planStartAt;

    /** 用车种类：缺省沿用需求单提报值，并与所选车辆种类校验一致 */
    private String vehicleType;

    /** 吊车吨位（吨），仅 CRANE 类型必填 */
    private BigDecimal craneTonnage;

    /** 实际吊装货物重量（吨），选填：客户提报未填时允许为空 */
    private BigDecimal cargoWeightTon;

    /** 预计使用时长（天），选填，缺省带入需求单客户自估值，仅备注提示 */
    private BigDecimal estimatedDays;

    /** 结算主体：ENTERPRISE 企业客户主体（管理员工单创建入口手动选择企业客户名称）/PERSONAL 个人结算；缺省带入需求单值 */
    private String settlementType;

    /** 任务备注 */
    @NotBlank(message = "任务备注不能为空")
    private String remark;
}
