package com.crane.modules.staff.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 车辆档案表（02 §3.3.4）。
 * 状态：IDLE/BUSY/BUSY_THIRD/MAINTENANCE/DISABLED（01 §6.3 锁定，禁止新增取值）。
 */
@Data
@TableName("biz_vehicle")
public class Vehicle {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String plateNo;

    private String model;

    /** 吊车吨位（吨） */
    private BigDecimal tonnage;

    /** 默认司机（固定归属），一人多车表现为多行共用同一值 */
    private Long defaultStaffId;

    /** 状态：IDLE / BUSY / BUSY_THIRD / MAINTENANCE / DISABLED */
    private String status;

    /** 当前绑定的进行中工单 */
    private Long currentOrderId;

    /** 第三方派单标记时刻 */
    private LocalDateTime thirdPartyMarkedAt;

    /** 是否在外未回场（由每日签退派生，档案域只读） */
    private Integer isAway;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Long createdBy;

    private Long updatedBy;

    @TableLogic
    private Integer deleted;
}
