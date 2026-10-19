package com.crane.modules.staff.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 车辆档案创建 / 更新入参（管理端）。
 * status 仅允许 IDLE/BUSY/BUSY_THIRD/MAINTENANCE/DISABLED，由服务层校验枚举字面量。
 */
@Data
public class VehicleUpsertRequest {

    @NotBlank(message = "车牌号不能为空")
    @Size(max = 16, message = "车牌号长度不能超过 16")
    private String plateNo;

    @Size(max = 64, message = "车辆型号长度不能超过 64")
    private String model;

    /** 车辆类型：CRANE 吊车 / LIFT 举升车，缺省 CRANE */
    private String type;

    /** 吊车吨位（吨），type=CRANE 必填，由服务层校验 */
    private BigDecimal tonnage;

    /** 举升高度（米），type=LIFT 必填，由服务层校验 */
    private BigDecimal liftHeight;

    /** 默认司机，可空；提供时校验员工存在 */
    private Long defaultStaffId;

    /** 状态：IDLE / BUSY / BUSY_THIRD / MAINTENANCE / DISABLED，缺省 IDLE */
    private String status;

    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
