package com.crane.modules.customer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客户资料表（02 §3.3.2）。
 * 类型：COMPANY 公司、PERSON 个人；审核状态：PENDING/APPROVED/REJECTED；状态：ACTIVE/DISABLED。
 */
@Data
@TableName("biz_customer")
public class Customer {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 客户编号，规则 KH + yyyyMMdd + 4 位序号 */
    private String customerNo;

    /** 客户名称，公司名称或个人姓名 */
    private String name;

    /** 客户类型：COMPANY / PERSON */
    private String type;

    /** 统一社会信用代码，公司客户必填 */
    private String creditCode;

    /** 所属行业：吊装、供暖配套、其他 */
    private String industry;

    private String address;

    /** 客户总机 / 主号码 */
    private String phone;

    /** 审核状态：PENDING / APPROVED / REJECTED */
    private String auditStatus;

    /** 客户状态：ACTIVE / DISABLED */
    private String status;

    /** 绑定的客户登录账号 sys_user.id */
    private Long userId;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Long createdBy;

    private Long updatedBy;

    @TableLogic
    private Integer deleted;
}
