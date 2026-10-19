package com.crane.modules.staff.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 员工档案创建 / 更新入参（管理端）。
 * name/phone 必填；position 仅允许 DRIVER、EMPLOYEE，由服务层校验枚举字面量。
 */
@Data
public class StaffUpsertRequest {

    @NotBlank(message = "姓名不能为空")
    @Size(max = 64, message = "姓名长度不能超过 64")
    private String name;

    @NotBlank(message = "手机号不能为空")
    @Size(max = 20, message = "手机号长度不能超过 20")
    private String phone;

    /** 可空；提供时做重复校验 */
    @Size(max = 64, message = "身份证号长度不能超过 64")
    private String idCard;

    /** 岗位：DRIVER / EMPLOYEE */
    @NotBlank(message = "岗位不能为空")
    private String position;

    /** 状态：IDLE / BUSY / AWAY / RESIGNED，缺省 IDLE */
    private String status;

    @Size(max = 16, message = "驾驶证类型长度不能超过 16")
    private String licenseType;

    @Size(max = 64, message = "特种作业证编号长度不能超过 64")
    private String specialCertNo;

    @Size(max = 500, message = "证件照片地址长度不能超过 500")
    private String specialCertPhotoUrl;

    @Size(max = 64, message = "紧急联系人姓名长度不能超过 64")
    private String emergencyName;

    @Size(max = 20, message = "紧急联系人电话长度不能超过 20")
    private String emergencyPhone;

    private LocalDate hireDate;

    /** 创建时是否顺带创建登录账号（roleId=2 司机账号，状态 PENDING_BIND） */
    private Boolean createAccount;

    /** 是否赋予管理员角色（司机/员工可一人多角色，登录后在小程序选择身份进入） */
    private Boolean adminRole;

    /** 创建登录账号时可选的初始密码，提供时不少于 6 位，经 BCrypt 编码后写入新账号 */
    @Size(max = 64, message = "初始密码长度不能超过 64")
    private String initialPassword;

    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
