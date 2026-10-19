package com.crane.modules.workorder.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * API-WO-001 创建工单请求体。字段与必填性与契约一致，业务校验在 Service 层完成。
 */
@Data
public class WorkOrderCreateRequest {

    /** 选填：不填时按「客户名·吊车吨位·司机姓名」自动生成 */
    @Size(max = 100, message = "taskName 最长 100")
    private String taskName;

    @NotNull(message = "customerId 必填")
    private Long customerId;

    /** 客户联系人，选填 */
    private Long contactId;

    @NotNull(message = "vehicleId 必填")
    private Long vehicleId;

    /** 用车种类：缺省按所选车辆种类，选择时须与车辆种类一致 */
    private String vehicleType;

    @NotNull(message = "staffId 必填")
    private Long staffId;

    @NotNull(message = "planStartAt 必填")
    private LocalDateTime planStartAt;

    @NotBlank(message = "address 必填")
    @Size(max = 255, message = "address 最长 255")
    private String address;

    @NotNull(message = "longitude 必填")
    private BigDecimal longitude;

    @NotNull(message = "latitude 必填")
    private BigDecimal latitude;

    /** 选填：货物名称 */
    @Size(max = 128, message = "cargoName 最长 128")
    private String cargoName;

    /** 选填：货物数量 */
    @Min(value = 1, message = "cargoQty 须大于等于 1")
    private Integer cargoQty;

    /** 选填：货物重量（吨） */
    @DecimalMin(value = "0.01", message = "cargoWeightTon 须大于 0")
    private BigDecimal cargoWeightTon;

    /** 吊车吨位，仅用车种类为 CRANE 时必填 */
    private BigDecimal craneTonnage;

    /** 预计使用时长（天），选填，快捷 0.5/1/1.5/2 天或自定义，仅备注提示 */
    @DecimalMin(value = "0.5", message = "estimatedDays 最小 0.5 天")
    private BigDecimal estimatedDays;

    /** 结算主体：ENTERPRISE 企业客户名称（默认，按客户结算）/PERSONAL 个人结算；缺省由服务层按客户类型兜底 */
    private String settlementType;

    /** 枚举 DEMAND、DIRECT */
    @NotBlank(message = "source 必填")
    private String source;

    /** source=DEMAND 时必填 */
    private Long demandOrderId;

    @Size(max = 500, message = "remark 最长 500")
    private String remark;

    @Size(max = 500, message = "requirement 最长 500")
    private String requirement;
}
