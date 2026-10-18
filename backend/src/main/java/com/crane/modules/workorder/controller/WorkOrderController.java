package com.crane.modules.workorder.controller;

import com.crane.common.auth.AuthContext;
import com.crane.common.exception.BizException;
import com.crane.common.exception.ErrorCode;
import com.crane.common.result.ApiResult;
import com.crane.common.result.PageResult;
import com.crane.modules.workorder.dto.ReviewRequest;
import com.crane.modules.workorder.dto.WorkOrderCreateRequest;
import com.crane.modules.workorder.dto.WorkOrderDetailResponse;
import com.crane.modules.workorder.dto.WorkOrderStatusChangeRequest;
import com.crane.modules.workorder.dto.WorkOrderUpdateRequest;
import com.crane.modules.workorder.entity.CustomerReview;
import com.crane.modules.workorder.entity.WorkOrder;
import com.crane.modules.workorder.entity.WorkOrderStatusLog;
import com.crane.modules.workorder.service.WorkOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

/**
 * 工单接口（API-WO-001~008）。控制器只做参数接收与角色校验，业务规则在 Service。
 * 角色口径：建单 / 修改 / 状态变更 / 结单仅管理员；评价仅客户本人；列表 / 详情 / 状态记录按角色过滤数据范围。
 */
@RestController
@RequestMapping("/api/v1/work-order")
@RequiredArgsConstructor
public class WorkOrderController {

    private final WorkOrderService workOrderService;

    /** API-WO-001 创建工单（仅管理员，权限码 workorder:create） */
    @PostMapping("/orders")
    public ApiResult<Map<String, Object>> create(@Valid @RequestBody WorkOrderCreateRequest request) {
        requireAdmin();
        Long operatorId = currentUserId();
        WorkOrder order = workOrderService.create(request, operatorId);
        return ApiResult.ok(Map.of(
                "orderId", order.getId(),
                "orderNo", order.getOrderNo(),
                "status", order.getStatus(),
                "customerVisibleStatus", order.getCustomerVisibleStatus()
        ));
    }

    /** API-WO-002 工单列表（权限码 workorder:read，数据范围按角色过滤） */
    @GetMapping("/orders")
    public ApiResult<PageResult<WorkOrderDetailResponse>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String customerVisibleStatus,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Long staffId,
            @RequestParam(required = false) Long vehicleId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ApiResult.ok(workOrderService.list(pageNum, pageSize, status, customerVisibleStatus, source,
                customerId, staffId, vehicleId, keyword, startDate, endDate));
    }

    /** API-WO-003 工单详情（权限码 workorder:read） */
    @GetMapping("/orders/{id}")
    public ApiResult<WorkOrderDetailResponse> detail(@PathVariable Long id) {
        return ApiResult.ok(workOrderService.detail(id));
    }

    /** API-WO-004 修改工单（仅管理员，权限码 workorder:update） */
    @PatchMapping("/orders/{id}")
    public ApiResult<Map<String, Object>> update(@PathVariable Long id,
                                                 @Valid @RequestBody WorkOrderUpdateRequest request) {
        requireAdmin();
        Long operatorId = currentUserId();
        WorkOrder order = workOrderService.update(id, request, operatorId);
        return ApiResult.ok(Map.of(
                "orderId", order.getId(),
                "status", order.getStatus()
        ));
    }

    /** API-WO-005 工单状态变更（仅管理员，权限码 workorder:update） */
    @PostMapping("/orders/{id}/status")
    public ApiResult<Map<String, Object>> changeStatus(@PathVariable Long id,
                                                       @Valid @RequestBody WorkOrderStatusChangeRequest request) {
        requireAdmin();
        Long operatorId = currentUserId();
        WorkOrder order = workOrderService.changeStatus(id, request, operatorId);
        return ApiResult.ok(Map.of(
                "orderId", order.getId(),
                "status", order.getStatus(),
                "customerVisibleStatus", order.getCustomerVisibleStatus() == null ? "" : order.getCustomerVisibleStatus()
        ));
    }

    /** API-WO-006 工单结单（仅管理员兜底，权限码 workorder:settle） */
    @PostMapping("/orders/{id}/settle")
    public ApiResult<Map<String, Object>> settle(@PathVariable Long id) {
        requireAdmin();
        Long operatorId = currentUserId();
        WorkOrder order = workOrderService.settle(id, operatorId);
        return ApiResult.ok(Map.of(
                "orderId", order.getId(),
                "status", order.getStatus(),
                "vehicleStatus", "IDLE",
                "reviewOpen", true
        ));
    }

    /** API-WO-007 状态变更记录（权限码 workorder:read） */
    @GetMapping("/orders/{id}/status-logs")
    public ApiResult<PageResult<WorkOrderStatusLog>> statusLogs(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResult.ok(workOrderService.statusLogs(id, pageNum, pageSize));
    }

    /** API-WO-008 提交客户评价（仅客户本人，权限码 workorder:review） */
    @PostMapping("/orders/{id}/review")
    public ApiResult<Map<String, Object>> review(@PathVariable Long id,
                                                 @Valid @RequestBody ReviewRequest request) {
        Long operatorId = currentUserId();
        CustomerReview review = workOrderService.review(id, request, operatorId);
        return ApiResult.ok(Map.of(
                "reviewId", review.getId(),
                "score", review.getScore(),
                "createdAt", review.getReviewedAt()
        ));
    }

    /** 司机对暂停工单申请继续施工（实时通知管理端，管理员批准后恢复） */
    @PostMapping("/orders/{id}/resume-request")
    public ApiResult<Map<String, Object>> resumeRequest(@PathVariable Long id) {
        workOrderService.resumeRequest(id, currentUserId());
        return ApiResult.ok(Map.of("requested", true));
    }

    /** 管理员批准暂停工单继续施工（SUSPENDED → IN_PROGRESS） */
    @PostMapping("/orders/{id}/resume")
    public ApiResult<Map<String, Object>> resume(@PathVariable Long id) {
        requireAdmin();
        Long operatorId = currentUserId();
        return ApiResult.ok(workOrderService.resume(id, operatorId));
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
