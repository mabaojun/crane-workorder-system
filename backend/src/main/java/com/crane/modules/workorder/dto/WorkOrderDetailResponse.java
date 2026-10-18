package com.crane.modules.workorder.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 工单详情 / 列表聚合出参：工单字段 + 跨域聚合（客户名称、司机姓名与电话、车牌号）。
 */
@Data
public class WorkOrderDetailResponse {

    private Long id;

    private String orderNo;

    /** 任务名称 */
    private String taskName;

    /** 来源：DEMAND、DIRECT */
    private String source;

    private Long demandId;

    private Long customerId;

    private String customerName;

    private Long contactId;

    private Long staffId;

    private String staffName;

    private String staffPhone;

    private Long vehicleId;

    private String vehiclePlateNo;

    private BigDecimal vehicleTonnage;

    private String address;

    private BigDecimal longitude;

    private BigDecimal latitude;

    private String cargoName;

    private BigDecimal cargoQuantity;

    private BigDecimal cargoWeight;

    private BigDecimal craneTonnage;

    private LocalDateTime expectedStartAt;

    private LocalDateTime acceptedAt;

    private LocalDateTime startedAt;

    private LocalDateTime suspendedAt;

    private LocalDateTime completedAt;

    private LocalDateTime cancelledAt;

    private String cancelledReason;

    /** 工单状态（01 §6.3 六态） */
    private String status;

    /** 客户可见状态 */
    private String customerVisibleStatus;

    /** 客户可见状态中文标签 */
    private String customerVisibleStatusLabel;

    private LocalDateTime acceptDeadlineAt;

    private LocalDateTime timeoutRemindedAt;

    private Integer isPinnedAlarm;

    private String remark;

    private String requirement;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
