package com.crane.modules.workorder.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 工单表 biz_work_order（02 §3.3.6）。聚合根，承载派单、执行、结单全过程判定字段。
 * 状态字面量与 01 §6.3 六态一致：PENDING_ACCEPT、PENDING_ARRIVE、IN_PROGRESS、SUSPENDED、COMPLETED、CANCELLED。
 */
@Data
@TableName("biz_work_order")
public class WorkOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 工单号，规则 GD + yyyyMMdd + 4 位序号，服务层生成 */
    private String orderNo;

    /** 任务名称 */
    private String name;

    /** 工单来源：DEMAND 需求单转入、DIRECT 管理员直建 */
    private String source;

    /** 来源需求单编号 */
    private Long demandId;

    /** 关联客户 */
    private Long customerId;

    /** 关联客户联系人 */
    private Long contactId;

    /** 执行司机 */
    private Long staffId;

    /** 施工车辆 */
    private Long vehicleId;

    /** 施工地点详细地址 */
    private String address;

    /** 高德点位经度 */
    private BigDecimal longitude;

    /** 高德点位纬度 */
    private BigDecimal latitude;

    /** 货物名称 */
    private String cargoName;

    /** 货物数量 */
    private BigDecimal cargoQuantity;

    /** 实际吊装货物重量（吨） */
    private BigDecimal cargoWeight;

    /** 吊车吨位 */
    private BigDecimal craneTonnage;

    /** 预计使用时长（天），仅备注提示帮助司机与管理员建立时长预期，不参与时间换算 */
    private BigDecimal estimatedDays;

    /** 预计开始时间 */
    private LocalDateTime expectedStartAt;

    /** 司机接单时间 */
    private LocalDateTime acceptedAt;

    /** 任务启动时间（首次进入 IN_PROGRESS 写入） */
    private LocalDateTime startedAt;

    /** 最近一次进入施工受阻的时间 */
    private LocalDateTime suspendedAt;

    /** 结单时间 */
    private LocalDateTime completedAt;

    /** 取消时间 */
    private LocalDateTime cancelledAt;

    /** 取消原因，对客户可见 */
    private String cancelledReason;

    /** 工单状态（01 §6.3 六态） */
    private String status;

    /** 客户可见状态：DISPATCHED、ACCEPTED、STARTED、PAUSED、FINISHED、CANCELLED */
    private String customerVisibleStatus;

    /** 接单时限（下发时间 + 配置分钟数） */
    private LocalDateTime acceptDeadlineAt;

    /** 超时未签到提醒已发出时间 */
    private LocalDateTime timeoutRemindedAt;

    /** 是否在报警中心置顶高亮：0 否、1 是 */
    private Integer isPinnedAlarm;

    /** 任务备注 */
    private String remark;

    /** 施工要求 */
    private String requirement;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Long createdBy;

    private Long updatedBy;

    @TableLogic
    private Integer deleted;
}
