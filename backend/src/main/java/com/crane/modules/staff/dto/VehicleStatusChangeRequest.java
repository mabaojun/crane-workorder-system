package com.crane.modules.staff.dto;

import lombok.Data;

/**
 * 车辆状态手动变更请求（管理员 PATCH /staff/vehicles/{id}/status、司机 POST /staff/vehicles/{id}/driver-status 共用）。
 * 目标状态合法性与权限边界由服务层校验。
 */
@Data
public class VehicleStatusChangeRequest {

    /** 目标状态：IDLE / BUSY_THIRD / MAINTENANCE / DISABLED（BUSY 由工单自动驱动） */
    private String status;
}
