package com.crane.modules.staff.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 车辆状态变更记录表（02 §3.3.19）。
 * 变更类型：DISPATCH/RELEASE/THIRD_PARTY_MARK/THIRD_PARTY_RESUME/MAINTENANCE/DISABLE/DAILY_CHECKOUT。
 */
@Data
@TableName("biz_vehicle_status_log")
public class VehicleStatusLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long vehicleId;

    /** 变更前状态，取值同车辆状态枚举 */
    private String beforeStatus;

    /** 变更后状态 */
    private String afterStatus;

    /** 变更类型 */
    private String changeType;

    /** 关联工单 */
    private Long orderId;

    /** 操作司机 */
    private Long staffId;

    /** 操作账号 */
    private Long operatorId;

    /** 变更发生时间 */
    private LocalDateTime occurAt;

    /** 本次外部占用时长（分钟），由 THIRD_PARTY_RESUME 时回填 */
    private Integer externalOccupyMinutes;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Long createdBy;

    private Long updatedBy;

    @TableLogic
    private Integer deleted;
}
