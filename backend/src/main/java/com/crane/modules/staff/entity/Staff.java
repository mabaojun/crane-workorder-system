package com.crane.modules.staff.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 员工档案表（02 §3.3.1）。
 * 岗位：DRIVER 司机、EMPLOYEE 员工；状态：IDLE/BUSY/AWAY/RESIGNED。
 * 一人多车由 biz_vehicle.default_staff_id 反向表达，本表不另建车辆关联。
 */
@Data
@TableName("biz_staff")
public class Staff {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 员工编号，规则 YG + yyyyMMdd + 4 位序号 */
    private String staffNo;

    private String name;

    private String phone;

    /** 身份证号（密文存储由文件域/存储层保障，本表存原文或密文均兼容） */
    private String idCard;

    /** 岗位：DRIVER / EMPLOYEE */
    private String position;

    /** 状态：IDLE / BUSY / AWAY / RESIGNED */
    private String status;

    /** 驾驶证类型，司机必填 */
    private String licenseType;

    /** 特种作业证（吊车操作证）编号，司机必填 */
    private String specialCertNo;

    private String specialCertPhotoUrl;

    private String emergencyName;

    private String emergencyPhone;

    private LocalDate hireDate;

    /** 绑定的登录账号 sys_user.id */
    private Long userId;

    /** 是否具有管理员角色（由绑定账号的角色集合计算，不落库） */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private Boolean adminRole;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Long createdBy;

    private Long updatedBy;

    @TableLogic
    private Integer deleted;
}
