package com.crane.modules.dispatch.controller;

import com.crane.common.auth.AuthContext;
import com.crane.common.exception.BizException;
import com.crane.common.exception.ErrorCode;
import com.crane.common.result.ApiResult;
import com.crane.common.result.PageResult;
import com.crane.modules.dispatch.dto.CheckItem;
import com.crane.modules.dispatch.dto.DispatchRequest;
import com.crane.modules.dispatch.dto.PrecheckRequest;
import com.crane.modules.dispatch.dto.RejectRequest;
import com.crane.modules.dispatch.entity.OrderAcceptLog;
import com.crane.modules.dispatch.service.DispatchCheckService;
import com.crane.modules.dispatch.service.DispatchService;
import com.crane.modules.workorder.entity.WorkOrder;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 派单与接单接口（API-DISP-001~007）。控制器只做参数接收与角色校验，业务规则在 Service。
 * 角色口径：前置校验 / 派单 / 重新派单 / 待重派队列 / 接单记录仅管理员；
 * 接单 / 拒单仅被派司机本人。
 */
@RestController
@RequestMapping("/api/v1/dispatch")
@RequiredArgsConstructor
public class DispatchController {

    private final DispatchService dispatchService;
    private final DispatchCheckService dispatchCheckService;

    /** API-DISP-001 派单前置校验（仅管理员，权限码 dispatch:precheck） */
    @PostMapping("/precheck")
    public ApiResult<Map<String, Object>> precheck(@Valid @RequestBody PrecheckRequest request) {
        requireAdmin();
        List<CheckItem> checks = dispatchCheckService.precheck(
                request.getStaffId(), request.getVehicleId(), request.getPlanStartAt());
        boolean pass = checks.stream().allMatch(CheckItem::isPass);
        return ApiResult.ok(Map.of(
                "pass", pass,
                "checks", checks
        ));
    }

    /** API-DISP-002 派单（仅管理员，权限码 dispatch:create） */
    @PostMapping("/orders/{workOrderId}/dispatch")
    public ApiResult<Map<String, Object>> dispatch(@PathVariable Long workOrderId,
                                                   @Valid @RequestBody DispatchRequest request) {
        requireAdmin();
        Long operatorId = currentUserId();
        WorkOrder order = dispatchService.dispatch(workOrderId, request, operatorId);
        return ApiResult.ok(Map.of(
                "orderId", order.getId(),
                "orderNo", order.getOrderNo(),
                "status", order.getStatus(),
                "staffId", order.getStaffId(),
                "vehicleId", order.getVehicleId(),
                "customerVisibleStatus", order.getCustomerVisibleStatus() == null ? "" : order.getCustomerVisibleStatus()
        ));
    }

    /** API-DISP-003 接单（仅被派司机本人，权限码 dispatch:accept） */
    @PostMapping("/orders/{workOrderId}/accept")
    public ApiResult<Map<String, Object>> accept(@PathVariable Long workOrderId) {
        requireDriver();
        Long operatorId = currentUserId();
        WorkOrder order = dispatchService.accept(workOrderId, operatorId);
        return ApiResult.ok(Map.of(
                "orderId", order.getId(),
                "status", order.getStatus(),
                "acceptedAt", order.getAcceptedAt()
        ));
    }

    /** API-DISP-004 拒单（仅被派司机本人，权限码 dispatch:reject） */
    @PostMapping("/orders/{workOrderId}/reject")
    public ApiResult<Map<String, Object>> reject(@PathVariable Long workOrderId,
                                                 @Valid @RequestBody RejectRequest request) {
        requireDriver();
        Long operatorId = currentUserId();
        WorkOrder order = dispatchService.reject(workOrderId, request, operatorId);
        return ApiResult.ok(Map.of(
                "orderId", order.getId(),
                "status", order.getStatus(),
                "redispatchQueued", true
        ));
    }

    /** API-DISP-005 重新派单（仅管理员，权限码 dispatch:create） */
    @PostMapping("/orders/{workOrderId}/redispatch")
    public ApiResult<Map<String, Object>> redispatch(@PathVariable Long workOrderId,
                                                     @Valid @RequestBody DispatchRequest request) {
        requireAdmin();
        Long operatorId = currentUserId();
        WorkOrder order = dispatchService.redispatch(workOrderId, request, operatorId);
        // 重派后接单时间清空，等待新一轮接单
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderId", order.getId());
        data.put("status", order.getStatus());
        data.put("staffId", order.getStaffId());
        data.put("vehicleId", order.getVehicleId());
        data.put("acceptedAt", null);
        return ApiResult.ok(data);
    }

    /** API-DISP-006 待重新派单队列（仅管理员，权限码 dispatch:read） */
    @GetMapping("/pending-redispatch")
    public ApiResult<PageResult<Map<String, Object>>> pendingRedispatch(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword) {
        requireAdmin();
        return ApiResult.ok(dispatchService.pendingRedispatch(pageNum, pageSize, keyword));
    }

    /** API-DISP-007 接单拒单记录（仅管理员，权限码 dispatch:read） */
    @GetMapping("/orders/{workOrderId}/logs")
    public ApiResult<Map<String, Object>> acceptLogs(@PathVariable Long workOrderId) {
        requireAdmin();
        List<OrderAcceptLog> logs = dispatchService.acceptLogs(workOrderId);
        return ApiResult.ok(Map.of("list", logs));
    }

    private void requireAdmin() {
        AuthContext ctx = AuthContext.get();
        if (ctx == null || !ctx.isAdmin()) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
    }

    private void requireDriver() {
        AuthContext ctx = AuthContext.get();
        if (ctx == null || !ctx.isDriver()) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
    }

    private Long currentUserId() {
        AuthContext ctx = AuthContext.get();
        return ctx == null ? null : ctx.getUserId();
    }
}
