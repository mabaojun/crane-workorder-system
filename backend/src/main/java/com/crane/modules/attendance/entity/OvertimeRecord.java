package com.crane.modules.attendance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 加班记录表 biz_overtime_record（02 §3.3.10）。
 * 位置不合规的记录仍落库并标注（is_location_compliant = 0），由管理员人工确认，不做物理剔除；
 * 加班须管理员审核后生效，以 audit_status + is_effective 双字段表达。
 */
@Data
@TableName("biz_overtime_record")
public class OvertimeRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联工单 */
    private Long orderId;

    /** 司机 */
    private Long staffId;

    /** 关联每日签退记录 */
    private Long dailyCheckoutId;

    /** 加班所属日期 */
    private LocalDate workDate;

    /** 正常下班时间节点（默认 18:00，后台可配置） */
    private LocalDateTime normalOffAt;

    /** 实际签退时间 */
    private LocalDateTime actualCheckoutAt;

    /** 签退坐标经度 */
    private BigDecimal checkoutLongitude;

    /** 签退坐标纬度 */
    private BigDecimal checkoutLatitude;

    /** 签退点与任务执行地距离（米） */
    private Integer checkoutDistanceMeters;

    /** 签退位置是否在加班有效范围内，不合规则不计加班 */
    private Integer isLocationCompliant;

    /** 加班时长（分钟）= 实际签退时间 - 正常下班时间，仅晚间下班时段 */
    private Integer overtimeMinutes;

    /** 审核状态：PENDING 待审核、APPROVED 已确认、REJECTED 已驳回 */
    private String auditStatus;

    /** 审核人 */
    private Long auditedBy;

    /** 审核时间 */
    private LocalDateTime auditedAt;

    /** 审核通过后置 1，方可计入绩效 */
    private Integer isEffective;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Long createdBy;

    private Long updatedBy;

    @TableLogic
    private Integer deleted;
}
