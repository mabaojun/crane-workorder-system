package com.crane.modules.demand.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 需求单表（02 §3.3.5）。
 * 状态：PENDING/REVIEWING/CONVERTED/REJECTED/CLOSED（01 §6.3 锁定）；
 * 来源：CUSTOMER_MP 客户小程序提报、PHONE 电话下单、ADMIN 管理员录入。
 */
@Data
@TableName("biz_demand_order")
public class DemandOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 需求单编号，规则 XQ + yyyyMMdd + 4 位序号 */
    private String demandNo;

    /** 来源：CUSTOMER_MP / PHONE / ADMIN */
    private String source;

    /** 关联客户资料，管理员代录时可能暂缺 */
    private Long customerId;

    /** 关联客户联系人 */
    private Long contactId;

    /** 提报人姓名快照，防止联系人变更后丢失 */
    private String contactName;

    private String contactPhone;

    /** 客户地址 / 施工地址 */
    private String address;

    /** 地图选点经度 DECIMAL(10,7) */
    private BigDecimal longitude;

    /** 地图选点纬度 DECIMAL(10,7) */
    private BigDecimal latitude;

    /** 需求描述 */
    private String description;

    /** 需求图片路径数组，JSON 字符串存储 */
    private String imageUrls;

    /** 意向车辆 */
    private Long intendedVehicleId;

    /** 吊车吨位需求（需管理员复核） */
    private BigDecimal craneTonnage;

    /** 被吊装货物重量（吨，需管理员复核） */
    private BigDecimal cargoWeight;

    private LocalDateTime expectedStartAt;

    /** 状态：PENDING / REVIEWING / CONVERTED / REJECTED / CLOSED */
    private String status;

    /** 复核意见或驳回原因，对客户可见 */
    private String reviewRemark;

    /** 驳回次数，支撑驳回后重提的链路展示 */
    private Integer rejectCount;

    /** 重提前指向被驳回的原需求单，原记录保留 */
    private Long parentDemandId;

    /** 转化后的工单，实现需求单与工单双向回溯 */
    private Long convertedOrderId;

    /** 暂存到期时间（创建时间 + demand.retention_days，可配置），超期自动转 CLOSED */
    private LocalDateTime expiresAt;

    private LocalDateTime closedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Long createdBy;

    private Long updatedBy;

    @TableLogic
    private Integer deleted;
}
