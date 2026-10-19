package com.crane.modules.workorder.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 建单命令（供需求单域转工单调用，也作为 API 建单请求的内部承载）。
 * 与 API-WO-001 请求体字段对齐：taskName/source/demandOrderId/customerId/contactId/
 * staffId/vehicleId/address/longitude/latitude/cargoName/cargoQuantity/cargoWeightTon/craneTonnage/planStartAt/remark/requirement。
 */
@Data
public class WorkOrderCreateCommand {

    /** 任务名称 */
    private String taskName;

    /** 工单来源：DEMAND、DIRECT */
    private String source;

    /** 来源需求单编号，source=DEMAND 时必填 */
    private Long demandOrderId;

    /** 客户 */
    private Long customerId;

    /** 客户联系人 */
    private Long contactId;

    /** 执行司机 */
    private Long staffId;

    /** 施工车辆 */
    private Long vehicleId;

    /** 施工地点详细地址 */
    private String address;

    /** 经度 */
    private BigDecimal longitude;

    /** 纬度 */
    private BigDecimal latitude;

    /** 货物名称 */
    private String cargoName;

    /** 货物数量 */
    private BigDecimal cargoQuantity;

    /** 实际吊装货物重量（吨） */
    private BigDecimal cargoWeightTon;

    /** 吊车吨位 */
    private BigDecimal craneTonnage;

    /** 预计使用时长（天），选填，仅备注提示 */
    private BigDecimal estimatedDays;

    /** 结算主体：ENTERPRISE 企业客户主体（缺省，按客户结算）/PERSONAL 个人结算；缺省值由服务层按客户类型兜底 */
    private String settlementType;

    /** 预计开始时间 */
    private LocalDateTime planStartAt;

    /** 任务备注 */
    private String remark;

    /** 施工要求 */
    private String requirement;
}
