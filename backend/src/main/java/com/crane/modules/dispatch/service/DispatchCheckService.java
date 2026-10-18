package com.crane.modules.dispatch.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crane.modules.dispatch.dto.CheckItem;
import com.crane.modules.staff.entity.Staff;
import com.crane.modules.staff.entity.Vehicle;
import com.crane.modules.staff.mapper.StaffMapper;
import com.crane.modules.staff.mapper.VehicleMapper;
import com.crane.modules.system.service.SystemConfigService;
import com.crane.modules.workorder.entity.WorkOrder;
import com.crane.modules.workorder.mapper.WorkOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 派单前置校验（API-DISP-001）。五项规则：
 * 车辆空闲、司机在岗（非离岗 / 离职）、司机无进行中工单、未接单派单数未达上限、时间合法。
 * 只读校验不落库，供派单与建单前的显式检查复用。
 */
@Service
@RequiredArgsConstructor
public class DispatchCheckService {

    public static final String CHECK_VEHICLE_IDLE = "VEHICLE_IDLE";
    public static final String CHECK_STAFF_ON_DUTY = "STAFF_ON_DUTY";
    public static final String CHECK_STAFF_NO_ACTIVE_ORDER = "STAFF_NO_ACTIVE_ORDER";
    public static final String CHECK_PENDING_DISPATCH_LIMIT = "PENDING_DISPATCH_LIMIT";
    public static final String CHECK_TIME_VALID = "TIME_VALID";

    private final VehicleMapper vehicleMapper;
    private final StaffMapper staffMapper;
    private final WorkOrderMapper workOrderMapper;
    private final SystemConfigService systemConfigService;

    /**
     * 执行全部前置校验，逐项返回结果（不抛异常），由调用方决定如何阻断。
     */
    public List<CheckItem> precheck(Long staffId, Long vehicleId, LocalDateTime planStartAt) {
        return precheck(null, staffId, vehicleId, planStartAt);
    }

    /**
     * 带工单上下文的校验：车辆已被本工单占用（BUSY 且 current_order_id 指向本单）视为通过，
     * 覆盖「建单即占用车辆后同车派单 / 拒单重派」场景。
     */
    public List<CheckItem> precheck(Long orderId, Long staffId, Long vehicleId, LocalDateTime planStartAt) {
        List<CheckItem> checks = new ArrayList<>();
        checks.add(checkVehicleIdle(orderId, vehicleId));
        checks.add(checkStaffOnDuty(staffId));
        checks.add(checkStaffNoActiveOrder(staffId));
        checks.add(checkPendingDispatchLimit(staffId));
        checks.add(checkTimeValid(planStartAt));
        return checks;
    }

    /** 车辆存在且状态为 IDLE；或 BUSY 且当前占用工单即被派单的工单 */
    private CheckItem checkVehicleIdle(Long orderId, Long vehicleId) {
        Vehicle vehicle = vehicleId == null ? null : vehicleMapper.selectById(vehicleId);
        if (vehicle == null) {
            return CheckItem.fail(CHECK_VEHICLE_IDLE, "车辆不存在");
        }
        boolean occupiedByThisOrder = orderId != null
                && "BUSY".equals(vehicle.getStatus())
                && orderId.equals(vehicle.getCurrentOrderId());
        if (!"IDLE".equals(vehicle.getStatus()) && !occupiedByThisOrder) {
            return CheckItem.fail(CHECK_VEHICLE_IDLE, "车辆非空闲，当前状态: " + vehicle.getStatus());
        }
        return CheckItem.pass(CHECK_VEHICLE_IDLE);
    }

    /** 司机存在且状态为 IDLE / BUSY（AWAY、RESIGNED 不过） */
    private CheckItem checkStaffOnDuty(Long staffId) {
        Staff staff = staffId == null ? null : staffMapper.selectById(staffId);
        if (staff == null) {
            return CheckItem.fail(CHECK_STAFF_ON_DUTY, "司机不存在");
        }
        if ("AWAY".equals(staff.getStatus()) || "RESIGNED".equals(staff.getStatus())) {
            return CheckItem.fail(CHECK_STAFF_ON_DUTY, "司机处于离岗或离职状态");
        }
        return CheckItem.pass(CHECK_STAFF_ON_DUTY);
    }

    /** 司机无进行中（IN_PROGRESS）工单 */
    private CheckItem checkStaffNoActiveOrder(Long staffId) {
        if (staffId == null) {
            return CheckItem.fail(CHECK_STAFF_NO_ACTIVE_ORDER, "司机不存在");
        }
        Long count = workOrderMapper.selectCount(new LambdaQueryWrapper<WorkOrder>()
                .eq(WorkOrder::getStaffId, staffId)
                .eq(WorkOrder::getStatus, "IN_PROGRESS"));
        if (count != null && count > 0) {
            return CheckItem.fail(CHECK_STAFF_NO_ACTIVE_ORDER, "司机已有未暂停的进行中工单");
        }
        return CheckItem.pass(CHECK_STAFF_NO_ACTIVE_ORDER);
    }

    /** 该司机待接单（PENDING_ACCEPT）工单数须低于可配上限 */
    private CheckItem checkPendingDispatchLimit(Long staffId) {
        if (staffId == null) {
            return CheckItem.fail(CHECK_PENDING_DISPATCH_LIMIT, "司机不存在");
        }
        int limit = systemConfigService.getInt("order.max_pending_orders_per_staff", 3);
        Long count = workOrderMapper.selectCount(new LambdaQueryWrapper<WorkOrder>()
                .eq(WorkOrder::getStaffId, staffId)
                .eq(WorkOrder::getStatus, "PENDING_ACCEPT"));
        if (count != null && count >= limit) {
            return CheckItem.fail(CHECK_PENDING_DISPATCH_LIMIT,
                    "同一司机未接单派单数已达上限 " + limit);
        }
        return CheckItem.pass(CHECK_PENDING_DISPATCH_LIMIT);
    }

    /** 预计开始时间为空或晚于当前时间；早于则需二次确认 */
    private CheckItem checkTimeValid(LocalDateTime planStartAt) {
        if (planStartAt != null && planStartAt.isBefore(LocalDateTime.now())) {
            return CheckItem.fail(CHECK_TIME_VALID, "预计开始时间早于当前时间，需二次确认");
        }
        return CheckItem.pass(CHECK_TIME_VALID);
    }
}
