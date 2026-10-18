package com.crane.modules.staff.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 车辆绑定记录表（02 §3.3.18）。
 * 记录车辆与工单、司机的三方绑定，同一车辆同一时段仅一个生效绑定（is_active=1）。
 */
@Data
@TableName("biz_vehicle_binding")
public class VehicleBinding {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long vehicleId;

    private Long orderId;

    private Long staffId;

    /** 绑定时间，外部占用与使用率统计的计时起点 */
    private LocalDateTime boundAt;

    /** 释放时间 */
    private LocalDateTime releasedAt;

    /** 释放原因：COMPLETED / CANCELLED / REASSIGN */
    private String releaseReason;

    /** 绑定是否生效：1 是 / 0 否 */
    private Integer isActive;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Long createdBy;

    private Long updatedBy;

    @TableLogic
    private Integer deleted;
}
