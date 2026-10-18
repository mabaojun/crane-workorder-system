package com.crane.modules.workorder.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工时统计表 biz_work_hour_stat（02 §3.3.11）。一单一条，由结单联动派生，允许重算覆盖。
 * 该表归属考勤域，因结单联动在本域触发，实体与 Mapper 暂放 workorder 域包内。
 */
@Data
@TableName("biz_work_hour_stat")
public class WorkHourStat {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联工单，一单一条 */
    private Long orderId;

    /** 司机 */
    private Long staffId;

    /** 车辆 */
    private Long vehicleId;

    /** 任务启动时间 */
    private LocalDateTime startedAt;

    /** 任务结束时间 */
    private LocalDateTime endedAt;

    /** 总工作时长（分钟） */
    private Integer totalMinutes;

    /** 加班总时长（分钟），仅含已生效加班 */
    private Integer overtimeMinutes;

    /** 签到次数 */
    private Integer checkInCount;

    /** 签退次数 */
    private Integer checkoutCount;

    /** 计算版本，重算覆盖时递增 */
    private Integer calcVersion;

    /** 统计生成时间 */
    private LocalDateTime computedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Long createdBy;

    private Long updatedBy;

    @TableLogic
    private Integer deleted;
}
