package com.crane.modules.demand.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 需求单提报入参。客户小程序提报（source=CUSTOMER_MP）与管理员代录（PHONE/ADMIN）共用。
 * customerId/contactId 仅供管理员代录时填写；客户提报时由服务层按登录上下文回填。
 */
@Data
public class DemandCreateRequest {

    /** 来源：CUSTOMER_MP / PHONE / ADMIN，缺省 CUSTOMER_MP */
    private String source;

    /** 管理员代录时的关联客户，可暂缺 */
    private Long customerId;

    /** 管理员代录时的关联联系人 */
    private Long contactId;

    /** 提报人姓名快照，客户提报时缺省取当前用户昵称兜底 */
    @Size(max = 64, message = "提报人姓名长度不能超过 64")
    private String contactName;

    @NotBlank(message = "联系电话不能为空")
    @Size(max = 20, message = "联系电话长度不能超过 20")
    private String contactPhone;

    @NotBlank(message = "施工地址不能为空")
    @Size(max = 255, message = "施工地址长度不能超过 255")
    private String address;

    /** 地图选点经度 */
    private BigDecimal longitude;

    /** 地图选点纬度 */
    private BigDecimal latitude;

    /** 需求描述 */
    private String description;

    /** 需求图片路径列表，落库为 JSON 字符串 */
    private List<String> imageUrls;

    /** 意向车辆 */
    private Long intendedVehicleId;

    /** 用车种类：CRANE 吊车 / LIFT 举升车 / SEMI_TRAILER 板车（半挂）/ HEAVY_TRAILER 大件托盘车，缺省 CRANE */
    private String vehicleType;

    /** 吊车吨位需求（吨），仅 CRANE 类型填写 */
    private BigDecimal craneTonnage;

    /** 被吊装货物重量（吨） */
    private BigDecimal cargoWeight;

    /** 预计使用时长（天），选填，快捷 0.5/1/1.5/2 天或自定义，仅备注提示 */
    private BigDecimal estimatedDays;

    /** 结算主体：ENTERPRISE 企业主体（企业联系人提报默认自动带出所属企业，可切个人）/PERSONAL 个人主体；个人客户强制 PERSONAL */
    private String settlementType;

    /** 期望施工时间 */
    private LocalDateTime expectedStartAt;

    /** 驳回后重提时指向被驳回的原需求单 */
    private Long parentDemandId;
}
