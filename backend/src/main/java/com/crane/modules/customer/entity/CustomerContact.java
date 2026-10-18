package com.crane.modules.customer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客户联系人表（02 §3.3.3）。
 * 手机号全局唯一；默认联系人每客户唯一，由服务层在保存事务内保证。
 */
@Data
@TableName("biz_customer_contact")
public class CustomerContact {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long customerId;

    private String name;

    private String gender;

    /** 职务，如项目经理、采购、现场负责人 */
    private String position;

    private String phone;

    private String wechat;

    private String email;

    /** 是否默认联系人：1 是 / 0 否，每客户唯一 */
    private Integer isDefault;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Long createdBy;

    private Long updatedBy;

    @TableLogic
    private Integer deleted;
}
