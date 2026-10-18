package com.crane.modules.demand.controller;

import com.crane.common.result.ApiResult;
import com.crane.common.result.PageResult;
import com.crane.modules.demand.dto.DemandConvertRequest;
import com.crane.modules.demand.dto.DemandCreateRequest;
import com.crane.modules.demand.dto.DemandReviewRequest;
import com.crane.modules.demand.entity.DemandOrder;
import com.crane.modules.demand.service.DemandService;
import com.crane.common.auth.AuthContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 需求单接口。
 * 提报登录即可（CUSTOMER 提报本人需求单，ADMIN 代录）；查询按数据范围过滤；
 * 复核与转工单仅 ADMIN（服务层校验，非 ADMIN 返回 10003）。
 */
@RestController
@RequestMapping("/api/v1/demand")
@RequiredArgsConstructor
public class DemandController {

    private final DemandService demandService;

    @PostMapping("/orders")
    public ApiResult<DemandOrder> create(@Valid @RequestBody DemandCreateRequest request) {
        return ApiResult.ok(demandService.create(request, currentUserId()));
    }

    @GetMapping("/orders")
    public ApiResult<PageResult<DemandOrder>> page(@RequestParam(defaultValue = "1") int pageNum,
                                                   @RequestParam(defaultValue = "20") int pageSize,
                                                   @RequestParam(required = false) String status) {
        return ApiResult.ok(demandService.page(pageNum, pageSize, status));
    }

    @GetMapping("/orders/{id}")
    public ApiResult<DemandOrder> detail(@PathVariable Long id) {
        return ApiResult.ok(demandService.getDetail(id));
    }

    @PostMapping("/orders/{id}/review")
    public ApiResult<DemandOrder> review(@PathVariable Long id, @Valid @RequestBody DemandReviewRequest request) {
        return ApiResult.ok(demandService.review(id, request, currentUserId()));
    }

    @PostMapping("/orders/{id}/convert")
    public ApiResult<DemandOrder> convert(@PathVariable Long id, @Valid @RequestBody DemandConvertRequest request) {
        return ApiResult.ok(demandService.convert(id, request, currentUserId()));
    }

    private Long currentUserId() {
        AuthContext ctx = AuthContext.get();
        return ctx == null ? null : ctx.getUserId();
    }
}
