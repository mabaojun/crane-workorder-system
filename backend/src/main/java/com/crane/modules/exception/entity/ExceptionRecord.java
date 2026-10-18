package com.crane.modules.exception.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 异常记录表 biz_exception_record（02 §3.3.12）。
 * 异常类型：RAIN 下雨、PARTY_STOP 甲方停工、VEHICLE_FAULT 车辆故障、CUSTOM 自定义；
 * 处理状态：PENDING 待批复、APPROVED 已批复、CLOSED 已关闭。
 * 状态快照字段使「上报 -> 批复 -> 状态回写」链路可独立复现；未批复期间司机不可签到 / 签退。
 */
@Data
@TableName("biz_exception_record")
public class ExceptionRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联工单 */
    private Long orderId;

    /** 上报司机 */
    private Long staffId;

    /** 异常类型：RAIN、PARTY_STOP、VEHICLE_FAULT、CUSTOM */
    private String type;

    /** 自定义原因的标签文本，固定选项时为空 */
    private String typeLabel;

    /** 异常描述 / 自定义原因明细 */
    private String description;

    /** 配图，私有资源路径数组，存 JSON 数组字符串 */
    private String imageUrls;

    /** 处理状态：PENDING、APPROVED、CLOSED */
    private String status;

    /** 批复指令：CONTINUE 继续施工、SUSPEND 暂停、TERMINATE 终止 */
    private String instruction;

    /** 上报时的工单状态快照 */
    private String orderStatusBefore;

    /** 批复后的工单状态快照 */
    private String orderStatusAfter;

    /** 是否对客户公开，含司机自定义原因 */
    private Integer isPublicToCustomer;

    /** 上报时间 */
    private LocalDateTime reportedAt;

    /** 批复时间 */
    private LocalDateTime repliedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Long createdBy;

    private Long updatedBy;

    @TableLogic
    private Integer deleted;
}
