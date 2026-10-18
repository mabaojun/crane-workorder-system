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
 * 每日签退记录表 biz_daily_checkout（02 §3.3.9）。
 * 承载跨天任务逐日签退与车辆离场 / 回场确认；唯一键 uk_dc_order_date(order_id, work_date)
 * 由服务层判重保证，同日重复提交返回 40004。
 */
@Data
@TableName("biz_daily_checkout")
public class DailyCheckout {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联工单 */
    private Long orderId;

    /** 司机 */
    private Long staffId;

    /** 关联当日签退打卡记录 */
    private Long attendanceId;

    /** 施工日期 */
    private LocalDate workDate;

    /** 当日施工开始时间 */
    private LocalDateTime dayStartAt;

    /** 当日施工结束时间（今日任务完成时刻） */
    private LocalDateTime dayEndAt;

    /** 签退经度 */
    private BigDecimal longitude;

    /** 签退纬度 */
    private BigDecimal latitude;

    /** 签退点与任务执行地直线距离（米） */
    private Integer distanceMeters;

    /** 位置校验结果，签退为弱校验，允许范围外提交 */
    private Integer isInRange;

    /** 当日离场照片，存 JSON 数组字符串 */
    private String photoUrls;

    /** 车辆是否离场 */
    private Integer isLeftSite;

    /** 车辆是否回场；选回场则车辆状态置空闲 */
    private Integer isReturned;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Long createdBy;

    private Long updatedBy;

    @TableLogic
    private Integer deleted;
}
