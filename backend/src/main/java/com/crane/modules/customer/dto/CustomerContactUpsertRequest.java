package com.crane.modules.customer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 客户联系人创建 / 更新入参（管理端）。
 * phone 全局唯一（库唯一索引兜底，服务层先查）；isDefault=1 时由服务层将同客户其他联系人置 0。
 */
@Data
public class CustomerContactUpsertRequest {

    /** 所属客户，独立创建 / 更新联系人时必填 */
    @NotNull(message = "所属客户不能为空")
    private Long customerId;

    @NotBlank(message = "联系人姓名不能为空")
    @Size(max = 64, message = "联系人姓名长度不能超过 64")
    private String name;

    @Size(max = 8, message = "性别长度不能超过 8")
    private String gender;

    @Size(max = 64, message = "职务长度不能超过 64")
    private String position;

    @NotBlank(message = "联系人手机号不能为空")
    @Size(max = 20, message = "联系人手机号长度不能超过 20")
    private String phone;

    @Size(max = 64, message = "微信号长度不能超过 64")
    private String wechat;

    @Size(max = 128, message = "邮箱长度不能超过 128")
    private String email;

    /** 是否默认联系人：1 是 / 0 否 */
    private Integer isDefault;

    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
