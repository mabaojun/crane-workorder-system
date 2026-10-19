package com.crane.modules.customer.controller;

import com.crane.common.auth.AuthContext;
import com.crane.common.exception.BizException;
import com.crane.common.exception.ErrorCode;
import com.crane.common.result.ApiResult;
import com.crane.common.result.PageResult;
import com.crane.modules.customer.dto.CustomerContactUpsertRequest;
import com.crane.modules.customer.dto.CustomerUpsertRequest;
import com.crane.modules.customer.entity.Customer;
import com.crane.modules.customer.entity.CustomerContact;
import com.crane.modules.customer.service.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 客户档案与联系人接口（管理端专用）。
 * MVP 简化：客户角色暂不开放自助查询，非 ADMIN 一律返回 10003。
 */
@RestController
@RequestMapping("/api/v1/customer")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @GetMapping("/customers")
    public ApiResult<PageResult<Customer>> pageCustomers(@RequestParam(defaultValue = "1") int pageNum,
                                                         @RequestParam(defaultValue = "20") int pageSize,
                                                         @RequestParam(required = false) String name,
                                                         @RequestParam(required = false) String phone,
                                                         @RequestParam(required = false) String status) {
        requireAdmin();
        return ApiResult.ok(customerService.page(pageNum, pageSize, name, phone, status));
    }

    @PostMapping("/customers")
    public ApiResult<Customer> createCustomer(@Valid @RequestBody CustomerUpsertRequest request) {
        requireAdmin();
        return ApiResult.ok(customerService.create(request, currentUserId()));
    }

    @PutMapping("/customers/{id}")
    public ApiResult<Customer> updateCustomer(@PathVariable Long id, @Valid @RequestBody CustomerUpsertRequest request) {
        requireAdmin();
        return ApiResult.ok(customerService.update(id, request, currentUserId()));
    }

    @DeleteMapping("/customers/{id}")
    public ApiResult<Void> deleteCustomer(@PathVariable Long id) {
        requireAdmin();
        customerService.delete(id, currentUserId());
        return ApiResult.ok();
    }

    @GetMapping("/customers/{id}/contacts")
    public ApiResult<List<CustomerContact>> listContacts(@PathVariable Long id) {
        requireAdmin();
        return ApiResult.ok(customerService.listContacts(id));
    }

    @PostMapping("/contacts")
    public ApiResult<CustomerContact> createContact(@Valid @RequestBody CustomerContactUpsertRequest request) {
        requireAdmin();
        return ApiResult.ok(customerService.createContact(request, currentUserId()));
    }

    @PutMapping("/contacts/{id}")
    public ApiResult<CustomerContact> updateContact(@PathVariable Long id,
                                                    @Valid @RequestBody CustomerContactUpsertRequest request) {
        requireAdmin();
        return ApiResult.ok(customerService.updateContact(id, request, currentUserId()));
    }

    @DeleteMapping("/contacts/{id}")
    public ApiResult<Void> deleteContact(@PathVariable Long id) {
        requireAdmin();
        customerService.deleteContact(id, currentUserId());
        return ApiResult.ok();
    }

    /** 为已有联系人补建 / 绑定登录账号（企业多联系人独立提报的绑定入口） */
    @PostMapping("/contacts/{id}/account")
    public ApiResult<CustomerContact> createContactAccount(@PathVariable Long id,
                                                           @RequestBody(required = false) ContactAccountRequest request) {
        requireAdmin();
        String password = request == null ? null : request.initialPassword();
        return ApiResult.ok(customerService.createContactAccount(id, password, currentUserId()));
    }

    /** 联系人建号请求体：initialPassword 选填（≥6 位，留空系统默认生成） */
    public record ContactAccountRequest(String initialPassword) {
    }

    private void requireAdmin() {
        AuthContext ctx = AuthContext.get();
        if (ctx == null || !ctx.isAdmin()) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
    }

    private Long currentUserId() {
        AuthContext ctx = AuthContext.get();
        return ctx == null ? null : ctx.getUserId();
    }
}
