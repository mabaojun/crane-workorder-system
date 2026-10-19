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

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 派单前置校验（API-DISP-001）。五项规则：
 * 车辆可用（空闲或作业中但时段不冲突）、司机在岗（非离岗 / 离职）、司机无进行中工单、未接单派单数未达上限、时间合法。
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

    /** 车辆时段冲突窗口：两单预计开始时间相差不足 6 小时视为同一施工时段 */
    private static final int VEHICLE_CONFLICT_HOURS = 6;

    /** 车辆占用中的活跃工单状态集合 */
    private static final List<String> ACTIVE_ORDER_STATUSES =
            List.of("PENDING_ACCEPT", "PENDING_ARRIVE", "IN_PROGRESS", "SUSPENDED");

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
     * 带工单上下文的校验： orderId 非空时排除本单自身的时段冲突判断，
     * 覆盖「建单即占用车辆后同车派单 / 拒单重派」场景。
     */
    public List<CheckItem> precheck(Long orderId, Long staffId, Long vehicleId, LocalDateTime planStartAt) {
        List<CheckItem> checks = new ArrayList<>();
        checks.add(checkVehicleAvailable(orderId, vehicleId, planStartAt));
        checks.add(checkStaffOnDuty(staffId));
        checks.add(checkStaffNoActiveOrder(staffId));
        checks.add(checkPendingDispatchLimit(staffId));
        checks.add(checkTimeValid(planStartAt));
        return checks;
    }

    /**
     * 车辆可用性校验（供派单预检与建单复用）：
     * 空闲直接通过；作业中（BUSY）按施工时段判断——当前忙不代表派单时刻忙，
     * 与该车活跃工单的预计开始时间相隔不足 6 小时视为时段冲突，充裕则放行；
     * 维保 / 第三方外出 / 停用属物理不可用，一律拒绝。
     */
    public CheckItem checkVehicleAvailable(Long orderId, Long vehicleId, LocalDateTime planStartAt) {
        Vehicle vehicle = vehicleId == null ? null : vehicleMapper.selectById(vehicleId);
        if (vehicle == null) {
            return CheckItem.fail(CHECK_VEHICLE_IDLE, "车辆不存在");
        }
        String status = vehicle.getStatus();
        return switch (status) {
            case "IDLE" -> CheckItem.pass(CHECK_VEHICLE_IDLE);
            case "DISABLED" -> CheckItem.fail(CHECK_VEHICLE_IDLE, "车辆已停用，不可派单");
            case "MAINTENANCE" -> CheckItem.fail(CHECK_VEHICLE_IDLE, "车辆维保中，不可派单");
            case "BUSY_THIRD" -> CheckItem.fail(CHECK_VEHICLE_IDLE, "车辆第三方派单外出，不可派单");
            case "BUSY" -> checkBusyVehicleTimeWindow(orderId, vehicle, planStartAt);
            default -> CheckItem.fail(CHECK_VEHICLE_IDLE, "车辆状态异常: " + status);
        };
    }

    /** 作业中车辆：被本单占用直接通过；否则与活跃工单按 6 小时窗口判断时段冲突 */
    private CheckItem checkBusyVehicleTimeWindow(Long orderId, Vehicle vehicle, LocalDateTime planStartAt) {
        boolean occupiedByThisOrder = orderId != null
                && orderId.equals(vehicle.getCurrentOrderId());
        if (occupiedByThisOrder) {
            return CheckItem.pass(CHECK_VEHICLE_IDLE);
        }
        if (planStartAt == null) {
            return CheckItem.fail(CHECK_VEHICLE_IDLE, "车辆作业中，请指定计划开始时间以确认时段不冲突");
        }
        List<WorkOrder> actives = workOrderMapper.selectList(new LambdaQueryWrapper<WorkOrder>()
                .eq(WorkOrder::getVehicleId, vehicle.getId())
                .in(WorkOrder::getStatus, ACTIVE_ORDER_STATUSES)
                .ne(orderId != null, WorkOrder::getId, orderId)
                .isNotNull(WorkOrder::getExpectedStartAt));
        for (WorkOrder active : actives) {
            long gapHours = Math.abs(Duration.between(planStartAt, active.getExpectedStartAt()).toMinutes());
            if (gapHours < VEHICLE_CONFLICT_HOURS * 60L) {
                return CheckItem.fail(CHECK_VEHICLE_IDLE, String.format(
                        "车辆在该时段已有工单 %s（%s 开工），6 小时内时间冲突，请调整时间或换车",
                        active.getOrderNo(), active.getExpectedStartAt().toLocalDate() + " "
                                + active.getExpectedStartAt().toLocalTime().withSecond(0).withNano(0)));
            }
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
