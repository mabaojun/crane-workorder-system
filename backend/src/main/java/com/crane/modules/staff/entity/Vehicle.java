package com.crane.modules.staff.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
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

    /** 车辆类型：CRANE 吊车 / LIFT 举升车，缺省 CRANE */
    private String type;

    /** 吊车吨位（吨），type=CRANE 时维护 */
    private BigDecimal tonnage;

    /** 举升高度（米），type=LIFT 时维护 */
    private BigDecimal liftHeight;

    /** 默认司机（固定归属），一人多车表现为多行共用同一值 */
    private Long defaultStaffId;

    /** 状态：IDLE / BUSY / BUSY_THIRD / MAINTENANCE / DISABLED */
    private String status;

    /** 当前绑定的进行中工单 */
    private Long currentOrderId;

    /** 第三方派单标记时刻 */
    private LocalDateTime thirdPartyMarkedAt;

    /** 车架号 VIN（17 位），第 10 位年款码可推算车龄与审车周期 */
    private String vin;

    /** 车险购买日期，保险到期 = 购买日 + 1 年 */
    private LocalDate insurancePurchaseDate;

    /** 最近审车日期，下次审车 = 审车日 + 周期 */
    private LocalDate inspectionDate;

    /** 审车周期：HALF_YEAR 半年（车龄 15 年以上）/ YEAR 一年 */
    private String inspectionCycle;

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
