package com.crane.modules.staff.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

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

    /** 车辆种类：CRANE 吊车 / LIFT 举升车 / SEMI_TRAILER 板车（半挂）/ HEAVY_TRAILER 大件托盘车，缺省 CRANE */
    private String type;

    /** 吊车吨位（吨），type=CRANE 时选填 */
    private BigDecimal tonnage;

    /** 举升高度（米），type=LIFT 时选填 */
    private BigDecimal liftHeight;

    /** 默认司机，可空；提供时校验员工存在 */
    private Long defaultStaffId;

    /** 车架号 VIN（选填，≤32 位；第 10 位年款码用于推算车龄与审车周期） */
    @Size(max = 32, message = "车架号长度不能超过 32")
    private String vin;

    /** 车险购买日期（选填），保险到期 = 购买日 + 1 年，临期 1 个月报警 */
    private LocalDate insurancePurchaseDate;

    /** 最近审车日期（选填），下次审车 = 审车日 + 周期，临期 1 个月报警 */
    private LocalDate inspectionDate;

    /** 审车周期：HALF_YEAR 半年 / YEAR 一年；留空按 VIN 车龄自动带出（15 年以上半年，否则一年） */
    private String inspectionCycle;

    /** 状态：IDLE / BUSY / BUSY_THIRD / MAINTENANCE / DISABLED，缺省 IDLE */
    private String status;

    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
