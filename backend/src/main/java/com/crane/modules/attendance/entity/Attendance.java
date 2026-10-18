package com.crane.modules.attendance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 打卡记录表 biz_attendance（02 §3.3.8）。
 * 承载签到、当日签退、任务结束三类打卡；距离与范围结果由服务端计算后落库，便于审计复盘。
 * 打卡类型字面与 01 §6.3 一致：CHECK_IN、DAILY_CHECKOUT、TASK_END。
 */
@Data
@TableName("biz_attendance")
public class Attendance {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联工单 */
    private Long orderId;

    /** 打卡司机 */
    private Long staffId;

    /** 打卡类型：CHECK_IN 签到、DAILY_CHECKOUT 当日签退、TASK_END 任务结束 */
    private String type;

    /** 打卡时间，服务端记录 */
    private LocalDateTime checkedAt;

    /** 客户端实际采集时间，弱网补传时保留原始采集时间不被篡改 */
    private LocalDateTime capturedAt;

    /** 打卡经度 */
    private BigDecimal longitude;

    /** 打卡纬度 */
    private BigDecimal latitude;

    /** 打卡点与工单预设点位直线距离（米） */
    private Integer distanceMeters;

    /** 是否在有效范围内，签到强制、签退弱校验 */
    private Integer isInRange;

    /** 位置是否合规，签退在范围外由后台标注为 0 */
    private Integer isCompliant;

    /** 现场照片私有资源路径数组，存 JSON 数组字符串 */
    private String photoUrls;

    /** 是否有效打卡，签到须同时满足定位与照片两条件 */
    private Integer isValid;

    /** 上传方式：ONLINE 实时上传、OFFLINE_RETRY 离线补传 */
    private String uploadMode;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Long createdBy;

    private Long updatedBy;

    @TableLogic
    private Integer deleted;
}
