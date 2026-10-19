package com.crane.modules.staff.controller;

import com.crane.common.auth.AuthContext;
import com.crane.common.exception.BizException;
import com.crane.common.exception.ErrorCode;
import com.crane.common.result.ApiResult;
import com.crane.common.result.PageResult;
import com.crane.modules.staff.dto.StaffUpsertRequest;
import com.crane.modules.staff.dto.VehicleStatusChangeRequest;
import com.crane.modules.staff.dto.VehicleUpsertRequest;
import com.crane.modules.staff.entity.Staff;
import com.crane.modules.staff.entity.Vehicle;
import com.crane.modules.staff.service.StaffService;
import com.crane.modules.staff.service.VehicleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 员工与车辆档案接口（管理端专用）。
 * 全部接口仅 ADMIN 可访问，非 ADMIN 返回 10003。
 */
@RestController
@RequestMapping("/api/v1/staff")
@RequiredArgsConstructor
public class StaffController {

    private final StaffService staffService;
    private final VehicleService vehicleService;

    @GetMapping("/staffs")
    public ApiResult<PageResult<Staff>> pageStaffs(@RequestParam(defaultValue = "1") int pageNum,
                                                   @RequestParam(defaultValue = "20") int pageSize,
                                                   @RequestParam(required = false) String name,
                                                   @RequestParam(required = false) String phone,
                                                   @RequestParam(required = false) String status) {
        requireAdmin();
        return ApiResult.ok(staffService.page(pageNum, pageSize, name, phone, status));
    }

    @PostMapping("/staffs")
    public ApiResult<Staff> createStaff(@Valid @RequestBody StaffUpsertRequest request) {
        requireAdmin();
        return ApiResult.ok(staffService.create(request, currentUserId()));
    }

    @PutMapping("/staffs/{id}")
    public ApiResult<Staff> updateStaff(@PathVariable Long id, @Valid @RequestBody StaffUpsertRequest request) {
        requireAdmin();
        return ApiResult.ok(staffService.update(id, request, currentUserId()));
    }

    @DeleteMapping("/staffs/{id}")
    public ApiResult<Void> deleteStaff(@PathVariable Long id) {
        requireAdmin();
        staffService.delete(id, currentUserId());
        return ApiResult.ok();
    }

    @GetMapping("/vehicles")
    public ApiResult<PageResult<Vehicle>> pageVehicles(@RequestParam(defaultValue = "1") int pageNum,
                                                       @RequestParam(defaultValue = "20") int pageSize,
                                                       @RequestParam(required = false) String plateNo,
                                                       @RequestParam(required = false) String status) {
        requireAdmin();
        return ApiResult.ok(vehicleService.page(pageNum, pageSize, plateNo, status));
    }

    @PostMapping("/vehicles")
    public ApiResult<Vehicle> createVehicle(@Valid @RequestBody VehicleUpsertRequest request) {
        requireAdmin();
        return ApiResult.ok(vehicleService.create(request, currentUserId()));
    }

    @PutMapping("/vehicles/{id}")
    public ApiResult<Vehicle> updateVehicle(@PathVariable Long id, @Valid @RequestBody VehicleUpsertRequest request) {
        requireAdmin();
        return ApiResult.ok(vehicleService.update(id, request, currentUserId()));
    }

    @DeleteMapping("/vehicles/{id}")
    public ApiResult<Void> deleteVehicle(@PathVariable Long id) {
        requireAdmin();
        vehicleService.delete(id, currentUserId());
        return ApiResult.ok();
    }

    /** 管理员手动变更车辆状态（作业中由工单自动驱动，不可手动设置） */
    @PatchMapping("/vehicles/{id}/status")
    public ApiResult<Vehicle> changeVehicleStatus(@PathVariable Long id,
                                                  @RequestBody VehicleStatusChangeRequest request) {
        requireAdmin();
        if (request == null || request.getStatus() == null || request.getStatus().isBlank()) {
            throw new BizException(ErrorCode.PARAM_MISSING, "目标状态必填");
        }
        return ApiResult.ok(vehicleService.manualChangeStatus(id, request.getStatus(), currentUserId()));
    }

    /** 司机常用设备列表：本人为默认司机的车辆（含状态），供司机端主页维护 */
    @GetMapping("/my-vehicles")
    public ApiResult<List<Vehicle>> myVehicles() {
        AuthContext ctx = AuthContext.get();
        if (ctx == null || ctx.getStaffId() == null) {
            return ApiResult.ok(List.of());
        }
        return ApiResult.ok(vehicleService.listMyVehicles(ctx.getStaffId()));
    }

    /** 司机变更常用设备状态（空闲/第三方派单外出/维保；停用仅管理员） */
    @PostMapping("/vehicles/{id}/driver-status")
    public ApiResult<Vehicle> driverChangeVehicleStatus(@PathVariable Long id,
                                                        @RequestBody VehicleStatusChangeRequest request) {
        AuthContext ctx = AuthContext.get();
        if (ctx == null || ctx.getStaffId() == null) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅司机可操作");
        }
        if (request == null || request.getStatus() == null || request.getStatus().isBlank()) {
            throw new BizException(ErrorCode.PARAM_MISSING, "目标状态必填");
        }
        return ApiResult.ok(vehicleService.driverChangeStatus(id, request.getStatus(), ctx.getStaffId(), ctx.getUserId()));
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
