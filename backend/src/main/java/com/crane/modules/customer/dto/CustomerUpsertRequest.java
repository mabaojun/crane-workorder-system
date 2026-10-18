package com.crane.modules.customer.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 客户档案创建 / 更新入参（管理端）。
 * type 仅允许 COMPANY、PERSON；COMPANY 必须提供 creditCode（服务层校验）。
 * 创建时可顺带提交初始联系人与是否创建客户登录账号。
 */
@Data
public class CustomerUpsertRequest {

    @NotBlank(message = "客户名称不能为空")
    @Size(max = 128, message = "客户名称长度不能超过 128")
    private String name;

    /** 客户类型：COMPANY / PERSON */
    @NotBlank(message = "客户类型不能为空")
    private String type;

    /** 统一社会信用代码，公司客户必填 */
    @Size(max = 64, message = "统一社会信用代码长度不能超过 64")
    private String creditCode;

    @Size(max = 32, message = "所属行业长度不能超过 32")
    private String industry;

    @Size(max = 255, message = "联系地址长度不能超过 255")
    private String address;

    @NotBlank(message = "客户电话不能为空")
    @Size(max = 20, message = "客户电话长度不能超过 20")
    private String phone;

    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;

    /** 创建时是否顺带创建客户登录账号（roleId=3，状态 PENDING_BIND） */
    private Boolean createAccount;

    /** 初始联系人列表，仅创建时生效 */
    @Valid
    private List<CustomerContactUpsertRequest> contacts;
}
