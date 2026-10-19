package com.crane.modules.stat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crane.modules.attendance.entity.Attendance;
import com.crane.modules.attendance.mapper.AttendanceMapper;
import com.crane.modules.customer.entity.Customer;
import com.crane.modules.customer.mapper.CustomerMapper;
import com.crane.modules.staff.entity.Staff;
import com.crane.modules.staff.entity.Vehicle;
import com.crane.modules.staff.mapper.StaffMapper;
import com.crane.modules.staff.mapper.VehicleMapper;
import com.crane.modules.stat.dto.StatusBoardDtos.BoardVO;
import com.crane.modules.stat.dto.StatusBoardDtos.CheckIn;
import com.crane.modules.stat.dto.StatusBoardDtos.OrderStatus;
import com.crane.modules.stat.dto.StatusBoardDtos.StaffStatus;
import com.crane.modules.stat.dto.StatusBoardDtos.VehicleStatus;
import com.crane.modules.workorder.entity.WorkOrder;
import com.crane.modules.workorder.mapper.WorkOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 工作台状态看板：按日期聚合人员、车辆、工单三种视角。
 * 数据源为工单 + 人员/车辆档案 + 最新签到记录，不引入地图。
 */
@Service
@RequiredArgsConstructor
public class StatusBoardService {

    private static final Set<String> ACTIVE_STATUSES = Set.of(
            "PENDING_ACCEPT", "PENDING_ARRIVE", "IN_PROGRESS", "SUSPENDED");
    private static final String STATUS_RESIGNED = "RESIGNED";
    private static final String TYPE_CHECK_IN = "CHECK_IN";

    private final WorkOrderMapper workOrderMapper;
    private final StaffMapper staffMapper;
    private final VehicleMapper vehicleMapper;
    private final CustomerMapper customerMapper;
    private final AttendanceMapper attendanceMapper;

    public BoardVO board(LocalDate date) {
        LocalDate day = date == null ? LocalDate.now() : date;
        LocalDateTime dayStart = day.atStartOfDay();
        LocalDateTime dayEnd = day.atTime(LocalTime.MAX);
        LocalDateTime now = LocalDateTime.now();

        // 当日工单：严格按计划开始时间落在所选日期，保证日期视角准确；
        // 跨日或仍在进行中的工单单独作为人员/车辆的「当前工单」展示
        LambdaQueryWrapper<WorkOrder> dateWrapper = new LambdaQueryWrapper<WorkOrder>()
                .between(WorkOrder::getExpectedStartAt, dayStart, dayEnd)
                .orderByAsc(WorkOrder::getExpectedStartAt)
                .last("LIMIT 500");
        List<WorkOrder> dateOrders = workOrderMapper.selectList(dateWrapper);

        // 当前进行中工单：任何日期查看车辆/人员当前状态都可回溯，不受日期筛选影响
        List<WorkOrder> activeOrders = workOrderMapper.selectList(new LambdaQueryWrapper<WorkOrder>()
                .in(WorkOrder::getStatus, ACTIVE_STATUSES)
                .orderByAsc(WorkOrder::getExpectedStartAt)
                .last("LIMIT 500"));

        Map<Long, WorkOrder> allOrders = new LinkedHashMap<>();
        for (WorkOrder o : dateOrders) {
            allOrders.put(o.getId(), o);
        }
        for (WorkOrder o : activeOrders) {
            allOrders.putIfAbsent(o.getId(), o);
        }

        Map<Long, Customer> customers = loadCustomers(allOrders.values());
        Map<Long, Staff> staffMap = staffMapper.selectList(new LambdaQueryWrapper<Staff>()
                        .orderByAsc(Staff::getId)).stream()
                .collect(Collectors.toMap(Staff::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        Map<Long, Vehicle> vehicleMap = vehicleMapper.selectList(new LambdaQueryWrapper<Vehicle>()
                        .orderByAsc(Vehicle::getId)).stream()
                .collect(Collectors.toMap(Vehicle::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));

        Map<Long, OrderStatus> itemMap = new LinkedHashMap<>();
        for (WorkOrder o : allOrders.values()) {
            itemMap.put(o.getId(), toOrderStatus(o, customers, staffMap, vehicleMap, now));
        }

        Map<Long, Attendance> latestCheckInByOrder = loadLatestCheckIns(allOrders.keySet());

        BoardVO vo = new BoardVO();
        vo.setDate(day.toString());
        vo.setTotalOrders(dateOrders.size());
        vo.setOrders(dateOrders.stream()
                .map(o -> itemMap.get(o.getId()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList()));
        vo.setStaff(buildStaff(dateOrders, activeOrders, itemMap, staffMap));
        vo.setVehicles(buildVehicles(dateOrders, allOrders, itemMap, vehicleMap, latestCheckInByOrder));
        return vo;
    }

    private List<StaffStatus> buildStaff(List<WorkOrder> dateOrders, List<WorkOrder> activeOrders,
                                         Map<Long, OrderStatus> itemMap, Map<Long, Staff> staffMap) {
        Map<Long, List<OrderStatus>> dateByStaff = dateOrders.stream()
                .filter(o -> o.getStaffId() != null)
                .collect(Collectors.groupingBy(WorkOrder::getStaffId,
                        Collectors.mapping(o -> itemMap.get(o.getId()), Collectors.toList())));
        Map<Long, OrderStatus> activeByStaff = new LinkedHashMap<>();
        for (WorkOrder o : activeOrders) {
            if (o.getStaffId() != null) {
                activeByStaff.putIfAbsent(o.getStaffId(), itemMap.get(o.getId()));
            }
        }

        List<StaffStatus> list = new ArrayList<>();
        for (Staff staff : staffMap.values()) {
            StaffStatus item = new StaffStatus();
            item.setStaffId(staff.getId());
            item.setName(staff.getName());
            item.setPhone(staff.getPhone());
            item.setPosition(staff.getPosition());
            item.setStatus(staff.getStatus());
            List<OrderStatus> orders = new ArrayList<>(dateByStaff.getOrDefault(staff.getId(), List.of()));
            orders.sort(Comparator.comparing(OrderStatus::getExpectedStartAt,
                    Comparator.nullsLast(Comparator.naturalOrder())));
            item.setOrders(orders);
            item.setCurrentOrder(activeByStaff.get(staff.getId()));
            item.setTotalMinutes(orders.stream()
                    .map(OrderStatus::getDurationMinutes)
                    .filter(Objects::nonNull)
                    .mapToLong(Long::longValue)
                    .sum());
            list.add(item);
        }
        list.sort(Comparator
                .comparing((StaffStatus s) -> STATUS_RESIGNED.equals(s.getStatus()))
                .thenComparing(s -> s.getCurrentOrder() == null)
                .thenComparing(s -> s.getOrders().isEmpty())
                .thenComparing(StaffStatus::getName, Comparator.nullsLast(String::compareTo)));
        return list;
    }

    private List<VehicleStatus> buildVehicles(List<WorkOrder> dateOrders, Map<Long, WorkOrder> allOrders,
                                              Map<Long, OrderStatus> itemMap, Map<Long, Vehicle> vehicleMap,
                                              Map<Long, Attendance> latestCheckInByOrder) {
        Map<Long, List<OrderStatus>> dateByVehicle = dateOrders.stream()
                .filter(o -> o.getVehicleId() != null)
                .collect(Collectors.groupingBy(WorkOrder::getVehicleId,
                        Collectors.mapping(o -> itemMap.get(o.getId()), Collectors.toList())));

        Map<Long, OrderStatus> activeByVehicle = new LinkedHashMap<>();
        for (WorkOrder o : allOrders.values()) {
            if (o.getVehicleId() != null && ACTIVE_STATUSES.contains(o.getStatus())) {
                activeByVehicle.putIfAbsent(o.getVehicleId(), itemMap.get(o.getId()));
            }
        }

        // 每个车辆取最新一次签到：先按工单归集，再按签到时间取最新
        Map<Long, Attendance> latestByVehicle = new HashMap<>();
        for (Attendance attendance : latestCheckInByOrder.values()) {
            WorkOrder order = allOrders.get(attendance.getOrderId());
            if (order == null || order.getVehicleId() == null) {
                continue;
            }
            latestByVehicle.merge(order.getVehicleId(), attendance,
                    (a, b) -> a.getCheckedAt().isAfter(b.getCheckedAt()) ? a : b);
        }

        List<VehicleStatus> list = new ArrayList<>();
        for (Vehicle vehicle : vehicleMap.values()) {
            VehicleStatus item = new VehicleStatus();
            item.setVehicleId(vehicle.getId());
            item.setPlateNo(vehicle.getPlateNo());
            item.setType(vehicle.getType());
            item.setTonnage(vehicle.getTonnage());
            item.setLiftHeight(vehicle.getLiftHeight());
            item.setStatus(vehicle.getStatus());
            item.setCurrentOrderId(vehicle.getCurrentOrderId());
            List<OrderStatus> orders = new ArrayList<>(dateByVehicle.getOrDefault(vehicle.getId(), List.of()));
            orders.sort(Comparator.comparing(OrderStatus::getExpectedStartAt,
                    Comparator.nullsLast(Comparator.naturalOrder())));
            item.setOrders(orders);
            OrderStatus current = activeByVehicle.get(vehicle.getId());
            item.setCurrentOrder(current);
            Attendance attendance = latestByVehicle.get(vehicle.getId());
            if (attendance != null) {
                CheckIn checkIn = new CheckIn();
                checkIn.setLng(attendance.getLongitude());
                checkIn.setLatitude(attendance.getLatitude());
                checkIn.setCheckedAt(attendance.getCheckedAt());
                checkIn.setDistanceMeters(attendance.getDistanceMeters());
                item.setLatestCheckIn(checkIn);
            }
            list.add(item);
        }
        list.sort(Comparator
                .comparingInt((VehicleStatus v) -> vehicleStatusRank(v.getStatus()))
                .thenComparing(v -> v.getCurrentOrder() == null)
                .thenComparing(VehicleStatus::getPlateNo, Comparator.nullsLast(String::compareTo)));
        return list;
    }

    private OrderStatus toOrderStatus(WorkOrder order, Map<Long, Customer> customers,
                                      Map<Long, Staff> staffMap, Map<Long, Vehicle> vehicleMap,
                                      LocalDateTime now) {
        OrderStatus item = new OrderStatus();
        item.setId(order.getId());
        item.setOrderNo(order.getOrderNo());
        item.setTaskName(order.getName());
        item.setCustomerId(order.getCustomerId());
        Customer customer = order.getCustomerId() == null ? null : customers.get(order.getCustomerId());
        item.setCustomerName(customer == null ? null : customer.getName());
        item.setStaffId(order.getStaffId());
        Staff staff = order.getStaffId() == null ? null : staffMap.get(order.getStaffId());
        item.setStaffName(staff == null ? null : staff.getName());
        item.setVehicleId(order.getVehicleId());
        Vehicle vehicle = order.getVehicleId() == null ? null : vehicleMap.get(order.getVehicleId());
        if (vehicle != null) {
            item.setVehiclePlateNo(vehicle.getPlateNo());
            item.setVehicleType(vehicle.getType());
            item.setTonnage(vehicle.getTonnage());
            item.setLiftHeight(vehicle.getLiftHeight());
        }
        item.setStatus(order.getStatus());
        item.setAddress(order.getAddress());
        item.setExpectedStartAt(order.getExpectedStartAt());
        item.setAcceptedAt(order.getAcceptedAt());
        item.setStartedAt(order.getStartedAt());
        item.setCompletedAt(order.getCompletedAt());
        item.setCancelledAt(order.getCancelledAt());
        item.setOngoing(ACTIVE_STATUSES.contains(order.getStatus()));
        if (order.getStartedAt() != null) {
            LocalDateTime end = order.getCompletedAt() != null ? order.getCompletedAt() : now;
            item.setDurationMinutes(Math.max(0, Duration.between(order.getStartedAt(), end).toMinutes()));
        }
        return item;
    }

    private Map<Long, Customer> loadCustomers(Collection<WorkOrder> orders) {
        Set<Long> ids = new HashSet<>();
        for (WorkOrder order : orders) {
            if (order.getCustomerId() != null) {
                ids.add(order.getCustomerId());
            }
            if (order.getSettlementCustomerId() != null) {
                ids.add(order.getSettlementCustomerId());
            }
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        return customerMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Customer::getId, Function.identity()));
    }

    /** 批量取每张工单最新一次到场签到，避免逐单查询 */
    private Map<Long, Attendance> loadLatestCheckIns(Collection<Long> orderIds) {
        if (orderIds.isEmpty()) {
            return Map.of();
        }
        List<Attendance> records = attendanceMapper.selectList(new LambdaQueryWrapper<Attendance>()
                .in(Attendance::getOrderId, orderIds)
                .eq(Attendance::getType, TYPE_CHECK_IN)
                .orderByDesc(Attendance::getCheckedAt));
        Map<Long, Attendance> latest = new HashMap<>();
        for (Attendance record : records) {
            latest.putIfAbsent(record.getOrderId(), record);
        }
        return latest;
    }

    private int vehicleStatusRank(String status) {
        if ("BUSY".equals(status)) {
            return 0;
        }
        if ("BUSY_THIRD".equals(status)) {
            return 1;
        }
        if ("MAINTENANCE".equals(status)) {
            return 2;
        }
        if ("IDLE".equals(status)) {
            return 3;
        }
        return 4;
    }
}
