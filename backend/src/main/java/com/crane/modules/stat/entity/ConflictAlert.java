package com.crane.modules.stat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 冲突提醒记录表 biz_conflict_alert（02 §3.3.14）。
 * 触发场景：AHEAD_OF_WINDOW 提前提醒时段、DRIVER_POSTPONE 司机申请延期、ADMIN_EDIT 管理员修改工单、ADMIN_DISPATCH 管理员重新派单；
 * 处置状态：PENDING 待处理、HANDLED 已处置、ESCALATED 已升级、CLOSED 已关闭。
 * 统计域只读消费，写入由调度与变更域负责。
 */
@Data
@TableName("biz_conflict_alert")
public class ConflictAlert {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 提醒编号，系统生成 */
    private String alertNo;

    /** 触发场景 */
    private String triggerScene;

    /** 涉及司机 */
    private Long staffId;

    /** 司机工号快照，供列表直接展示 */
    private String staffNoSnapshot;

    /** 冲突前一单（进行中工单） */
    private Long prevOrderId;

    /** 冲突后一单（进入提前提醒时段的工单） */
    private Long nextOrderId;

    /** 前一单工单号快照 */
    private String prevOrderNo;

    /** 后一单工单号快照 */
    private String nextOrderNo;

    /** 冲突说明，含两单任务名与约定服务时间 */
    private String conflictSummary;

    /** 提醒对象数组，JSON 字符串存储 */
    private String notifyTargets;

    /** 各对象通道与送达状态，JSON 字符串存储 */
    private String notifyChannels;

    /** 提醒生成时间 */
    private LocalDateTime generatedAt;

    /** 处置时限截止时间，升级巡检依据 */
    private LocalDateTime responseDeadlineAt;

    /** 处置状态：PENDING / HANDLED / ESCALATED / CLOSED */
    private String status;

    /** 处置动作：REASSIGN / APPROVE_POSTPONE / SUSPEND / NOTIFY / CONTINUE */
    private String handleAction;

    /** 处置人 */
    private Long handledBy;

    /** 处置时间 */
    private LocalDateTime handledAt;

    /** 处置说明 */
    private String handleRemark;

    /** 升级时间 */
    private LocalDateTime escalatedAt;

    /** 升级提醒对象，JSON 字符串存储 */
    private String escalateTargets;

    /** 是否在数据驾驶舱报警中心置顶：0 否、1 是 */
    private Integer isPinned;

    /** 关闭原因 */
    private String closedReason;

    /** 由延期类变更申请触发时指向该申请 */
    private Long orderChangeRequestId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Long createdBy;

    private Long updatedBy;

    @TableLogic
    private Integer deleted;
}
