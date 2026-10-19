package com.crane.modules.stat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.crane.common.exception.BizException;
import com.crane.common.exception.ErrorCode;
import com.crane.modules.customer.entity.Customer;
import com.crane.modules.customer.mapper.CustomerMapper;
import com.crane.modules.demand.entity.DemandOrder;
import com.crane.modules.demand.mapper.DemandOrderMapper;
import com.crane.modules.exception.entity.ExceptionRecord;
import com.crane.modules.exception.mapper.ExceptionRecordMapper;
import com.crane.modules.staff.entity.Staff;
import com.crane.modules.staff.entity.Vehicle;
import com.crane.modules.staff.entity.VehicleStatusLog;
import com.crane.modules.staff.mapper.StaffMapper;
import com.crane.modules.staff.mapper.VehicleMapper;
import com.crane.modules.staff.mapper.VehicleStatusLogMapper;
import com.crane.modules.stat.dto.StatDtos.AlarmItem;
import com.crane.modules.stat.dto.StatDtos.AlarmListVO;
import com.crane.modules.stat.dto.StatDtos.CustomerStat;
import com.crane.modules.stat.dto.StatDtos.DashboardVO;
import com.crane.modules.stat.dto.StatDtos.ExceptionTypeCount;
import com.crane.modules.stat.dto.StatDtos.MapPoint;
import com.crane.modules.stat.dto.StatDtos.OrderStat;
import com.crane.modules.stat.dto.StatDtos.PendingDemand;
import com.crane.modules.stat.dto.StatDtos.StaffPerformance;
import com.crane.modules.stat.dto.StatDtos.StaffRank;
import com.crane.modules.stat.dto.StatDtos.StaffStat;
import com.crane.modules.stat.dto.StatDtos.StatusCount;
import com.crane.modules.stat.dto.StatDtos.TrendPoint;
import com.crane.modules.stat.dto.StatDtos.VehicleMapVO;
import com.crane.modules.stat.dto.StatDtos.VehicleStat;
import com.crane.modules.stat.entity.ConflictAlert;
import com.crane.modules.stat.mapper.ConflictAlertMapper;
import com.crane.modules.system.service.SystemConfigService;
import com.crane.modules.workorder.entity.WorkOrder;
import com.crane.modules.workorder.mapper.WorkOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 统计域服务：数据驾驶舱总览、报警中心、车辆地图与员工绩效。
 * 聚合查询均以一次分组聚合（selectMaps + groupBy）或少量批量查询实现，避免列表 N+1。
 */
@Service
@RequiredArgsConstructor
public class StatService {

    private static final String STATUS_PENDING_ACCEPT = "PENDING_ACCEPT";
    private static final String STATUS_PENDING_ARRIVE = "PENDING_ARRIVE";
    private static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    private static final String STATUS_SUSPENDED = "SUSPENDED";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_CANCELLED = "CANCELLED";

    private static final String VEHICLE_IDLE = "IDLE";
    private static final String VEHICLE_BUSY = "BUSY";
    private static final String VEHICLE_BUSY_THIRD = "BUSY_THIRD";
    private static final String VEHICLE_MAINTENANCE = "MAINTENANCE";
    private static final String VEHICLE_DISABLED = "DISABLED";

    private static final String DEMAND_PENDING = "PENDING";
    private static final String DEMAND_REVIEWING = "REVIEWING";

    private static final String CONFLICT_PENDING = "PENDING";
    private static final String CONFLICT_ESCALATED = "ESCALATED";

    private static final DateTimeFormatter TREND_DATE_FORMAT = DateTimeFormatter.ofPattern("MM-dd");
    private static final DateTimeFormatter CLOCK_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    /** 异常类型固定标签映射，未知类型归入「其他」 */
    private static final Map<String, String> EXCEPTION_LABELS = new LinkedHashMap<>();

    static {
        EXCEPTION_LABELS.put("RAIN", "下雨");
        EXCEPTION_LABELS.put("PARTY_STOP", "甲方停工");
        EXCEPTION_LABELS.put("VEHICLE_FAULT", "车辆故障");
        EXCEPTION_LABELS.put("CUSTOM", "其他");
    }

    private static final String EXCEPTION_LABEL_OTHER = "其他";

    /** 报警类型固定排序权重：冲突最前 */
    private static final Map<String, Integer> ALARM_TYPE_PRIORITY = new LinkedHashMap<>();

    static {
        ALARM_TYPE_PRIORITY.put("CONFLICT", 0);
        ALARM_TYPE_PRIORITY.put("NOT_ACCEPTED", 1);
        ALARM_TYPE_PRIORITY.put("NOT_ARRIVED", 2);
        ALARM_TYPE_PRIORITY.put("NOT_CHECKED_IN", 3);
        ALARM_TYPE_PRIORITY.put("VEHICLE_OCCUPIED", 4);
    }

    /** 报警级别排序权重：HIGH 在前 */
    private static final Map<String, Integer> ALARM_LEVEL_PRIORITY = Map.of("HIGH", 0, "MEDIUM", 1);

    /** 工单六态全量，用于状态分布兜底补零 */
    private static final String[] ORDER_ALL_STATUSES = {
            STATUS_PENDING_ACCEPT, STATUS_PENDING_ARRIVE, STATUS_IN_PROGRESS,
            STATUS_SUSPENDED, STATUS_COMPLETED, STATUS_CANCELLED
    };

    private final WorkOrderMapper workOrderMapper;
    private final StaffMapper staffMapper;
    private final VehicleMapper vehicleMapper;
    private final VehicleStatusLogMapper vehicleStatusLogMapper;
    private final DemandOrderMapper demandOrderMapper;
    private final ExceptionRecordMapper exceptionRecordMapper;
    private final CustomerMapper customerMapper;
    private final ConflictAlertMapper conflictAlertMapper;
    private final SystemConfigService systemConfigService;

    /**
     * 驾驶舱总览：工单、趋势、异常、车辆、员工、客户需求、待处理需求单与本月司机排名。
     */
    public DashboardVO dashboard() {
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime weekStart = startOfWeek(today).atStartOfDay();
        LocalDateTime monthStart = today.withDayOfMonth(1).atStartOfDay();

        DashboardVO vo = new DashboardVO();
        vo.setOrder(buildOrderStat());
        vo.setTrend7d(new ArrayList<>());
        vo.setExceptionDistribution(buildExceptionDistribution(todayStart));
        vo.setVehicle(buildVehicleStat(now));
        vo.setStaff(buildStaffStat());
        vo.setCustomer(buildCustomerStat(monthStart));
        vo.setPendingDemands(buildPendingDemands());
        vo.setStaffRank(new ArrayList<>());

        // 工单创建时间分桶一次查回，同时供今日/本周/本月与近 7 日趋势使用
        LocalDateTime trendStart = today.minusDays(6).atStartOfDay();
        LocalDateTime minStart = weekStart.isBefore(monthStart) ? weekStart : monthStart;
        if (trendStart.isBefore(minStart)) {
            minStart = trendStart;
        }
        Map<LocalDate, Long> createdBuckets = countByCreatedDate(minStart);
        fillOrderTimeCounts(vo.getOrder(), createdBuckets, today, weekStart.toLocalDate(), monthStart.toLocalDate());
        vo.setTrend7d(buildTrend7d(today, createdBuckets));
        vo.setStaffRank(buildStaffRank(monthStart));
        return vo;
    }

    /**
     * 报警中心：冲突、未到场、未签到、车辆持续占用四类报警聚合与排序。
     */
    public AlarmListVO alarms() {
        LocalDateTime now = LocalDateTime.now();
        List<AlarmItem> items = new ArrayList<>();
        collectConflictAlarms(items, now);
        collectNotAcceptedAlarms(items, now);
        collectNotArrivedAlarms(items, now);
        collectNotCheckedInAlarms(items, now);
        collectVehicleOccupiedAlarms(items, now);
        sortAlarms(items);

        AlarmListVO vo = new AlarmListVO();
        vo.setCount(items.size());
        vo.setList(items);
        return vo;
    }

    /**
     * 车辆地图：进行中相关工单的施工点位，附未到场 / 未签到报警标记。
     */
    public VehicleMapVO vehicleMap() {
        LocalDateTime now = LocalDateTime.now();
        int graceMinutes = systemConfigService.getInt("alarm.arrive_grace_minutes", 15);
        int checkinMinutes = systemConfigService.getInt("alarm.checkin_deadline_minutes", 30);

        List<WorkOrder> orders = workOrderMapper.selectList(new LambdaQueryWrapper<WorkOrder>()
                .in(WorkOrder::getStatus, STATUS_PENDING_ARRIVE, STATUS_IN_PROGRESS, STATUS_SUSPENDED));

        Set<Long> staffIds = orders.stream().map(WorkOrder::getStaffId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> vehicleIds = orders.stream().map(WorkOrder::getVehicleId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> staffNames = staffNameMap(staffIds);
        Map<Long, Vehicle> vehicleById = vehicleByIdMap(vehicleIds);

        List<MapPoint> points = new ArrayList<>();
        for (WorkOrder order : orders) {
            // 缺坐标的工单不产出点位
            if (order.getLongitude() == null || order.getLatitude() == null) {
                continue;
            }
            MapPoint point = new MapPoint();
            point.setVehicleId(order.getVehicleId());
            Vehicle vehicle = order.getVehicleId() != null ? vehicleById.get(order.getVehicleId()) : null;
            point.setPlateNo(vehicle != null ? vehicle.getPlateNo() : "");
            point.setStaffName(order.getStaffId() != null ? staffNames.getOrDefault(order.getStaffId(), "") : "");
            point.setOrderNo(order.getOrderNo());
            point.setStatus(order.getStatus());
            point.setLng(order.getLongitude());
            point.setLatitude(order.getLatitude());
            point.setAlarm(isNotArrived(order, now, graceMinutes) || isNotCheckedIn(order, now, checkinMinutes));
            point.setUpdatedAt(order.getUpdatedAt());
            points.add(point);
        }

        VehicleMapVO vo = new VehicleMapVO();
        vo.setTotal(vehicleMapper.selectCount(null).intValue());
        vo.setOnMap(points.size());
        vo.setPoints(points);
        return vo;
    }

    /**
     * 员工绩效：按时间窗（MONTH 本月 / WEEK 本周 / DAY 今日）统计各司机工单数与完工率。
     */
    public List<StaffPerformance> staffPerformance(String period) {
        if (period == null || period.isBlank()) {
            throw new BizException(ErrorCode.PARAM_MISSING);
        }
        LocalDate today = LocalDate.now();
        LocalDateTime start;
        switch (period) {
            case "DAY" -> start = today.atStartOfDay();
            case "WEEK" -> start = startOfWeek(today).atStartOfDay();
            case "MONTH" -> start = today.withDayOfMonth(1).atStartOfDay();
            default -> throw new BizException(ErrorCode.PARAM_INVALID);
        }

        QueryWrapper<WorkOrder> qw = new QueryWrapper<>();
        qw.select("staff_id", "COUNT(*) AS cnt",
                        "SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END) AS completed_cnt")
                .ge("created_at", start)
                .isNotNull("staff_id")
                .groupBy("staff_id");
        List<Map<String, Object>> rows = workOrderMapper.selectMaps(qw);
        rows.sort(Comparator
                .comparing((Map<String, Object> row) -> num(row.get("cnt")), Comparator.reverseOrder())
                .thenComparing(row -> toLong(row.get("staff_id"))));

        List<Long> staffIds = rows.stream().map(row -> toLong(row.get("staff_id"))).filter(Objects::nonNull).toList();
        Map<Long, String> staffNames = staffNameMap(staffIds);

        List<StaffPerformance> result = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            long orderCount = num(row.get("cnt"));
            long completedCount = num(row.get("completed_cnt"));
            Long staffId = toLong(row.get("staff_id"));
            BigDecimal completionRate = null;
            if (orderCount > 0) {
                completionRate = BigDecimal.valueOf(completedCount)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(orderCount), 2, RoundingMode.HALF_UP);
            }
            result.add(new StaffPerformance(staffId, staffNames.getOrDefault(staffId, ""),
                    (int) orderCount, (int) completedCount, completionRate));
        }
        return result;
    }

    /* ================================================== 总览 ================================================== */

    private OrderStat buildOrderStat() {
        // 全量状态分布：一次分组聚合
        QueryWrapper<WorkOrder> statusQw = new QueryWrapper<>();
        statusQw.select("status", "COUNT(*) AS cnt").groupBy("status");
        Map<String, Long> byStatus = new HashMap<>();
        for (Map<String, Object> row : workOrderMapper.selectMaps(statusQw)) {
            Object status = row.get("status");
            if (status != null) {
                byStatus.merge(String.valueOf(status), num(row.get("cnt")), Long::sum);
            }
        }

        OrderStat stat = new OrderStat();
        stat.setInProgress((int) byStatus.getOrDefault(STATUS_IN_PROGRESS, 0L).longValue());
        stat.setSuspended((int) byStatus.getOrDefault(STATUS_SUSPENDED, 0L).longValue());
        stat.setCompleted((int) byStatus.getOrDefault(STATUS_COMPLETED, 0L).longValue());
        stat.setPendingAccept((int) byStatus.getOrDefault(STATUS_PENDING_ACCEPT, 0L).longValue());
        stat.setPendingArrive((int) byStatus.getOrDefault(STATUS_PENDING_ARRIVE, 0L).longValue());
        stat.setCancelled((int) byStatus.getOrDefault(STATUS_CANCELLED, 0L).longValue());

        List<StatusCount> distribution = new ArrayList<>(ORDER_ALL_STATUSES.length);
        for (String status : ORDER_ALL_STATUSES) {
            distribution.add(new StatusCount(status, byStatus.getOrDefault(status, 0L).intValue()));
        }
        stat.setStatusDistribution(distribution);
        return stat;
    }

    private void fillOrderTimeCounts(OrderStat stat, Map<LocalDate, Long> buckets,
                                     LocalDate today, LocalDate weekStart, LocalDate monthStart) {
        long todayCount = 0;
        long weekCount = 0;
        long monthCount = 0;
        for (Map.Entry<LocalDate, Long> entry : buckets.entrySet()) {
            LocalDate date = entry.getKey();
            if (!date.isBefore(today)) {
                todayCount += entry.getValue();
            }
            if (!date.isBefore(weekStart)) {
                weekCount += entry.getValue();
            }
            if (!date.isBefore(monthStart)) {
                monthCount += entry.getValue();
            }
        }
        stat.setToday((int) todayCount);
        stat.setWeek((int) weekCount);
        stat.setMonth((int) monthCount);
    }

    private Map<LocalDate, Long> countByCreatedDate(LocalDateTime minStart) {
        QueryWrapper<WorkOrder> qw = new QueryWrapper<>();
        qw.select("DATE(created_at) AS d", "COUNT(*) AS cnt")
                .ge("created_at", minStart)
                .groupBy("DATE(created_at)");
        Map<LocalDate, Long> buckets = new HashMap<>();
        for (Map<String, Object> row : workOrderMapper.selectMaps(qw)) {
            LocalDate date = parseDate(row.get("d"));
            if (date != null) {
                buckets.merge(date, num(row.get("cnt")), Long::sum);
            }
        }
        return buckets;
    }

    private List<TrendPoint> buildTrend7d(LocalDate today, Map<LocalDate, Long> buckets) {
        List<TrendPoint> trend = new ArrayList<>(7);
        for (int i = 6; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            trend.add(new TrendPoint(date.format(TREND_DATE_FORMAT), buckets.getOrDefault(date, 0L).intValue()));
        }
        return trend;
    }

    private List<ExceptionTypeCount> buildExceptionDistribution(LocalDateTime todayStart) {
        QueryWrapper<ExceptionRecord> qw = new QueryWrapper<>();
        qw.select("type", "COUNT(*) AS cnt").ge("reported_at", todayStart).groupBy("type");
        Map<String, Long> byType = new HashMap<>();
        for (Map<String, Object> row : exceptionRecordMapper.selectMaps(qw)) {
            Object type = row.get("type");
            if (type != null) {
                byType.merge(String.valueOf(type), num(row.get("cnt")), Long::sum);
            }
        }
        // 固定四类全量输出，未出现的计 0
        List<ExceptionTypeCount> list = new ArrayList<>(EXCEPTION_LABELS.size());
        for (Map.Entry<String, String> entry : EXCEPTION_LABELS.entrySet()) {
            list.add(new ExceptionTypeCount(entry.getKey(), entry.getValue(),
                    byType.getOrDefault(entry.getKey(), 0L).intValue()));
        }
        // 未知类型单独列出，标签归入「其他」
        for (Map.Entry<String, Long> entry : byType.entrySet()) {
            if (!EXCEPTION_LABELS.containsKey(entry.getKey())) {
                list.add(new ExceptionTypeCount(entry.getKey(), EXCEPTION_LABEL_OTHER, entry.getValue().intValue()));
            }
        }
        return list;
    }

    private VehicleStat buildVehicleStat(LocalDateTime now) {
        QueryWrapper<Vehicle> qw = new QueryWrapper<>();
        qw.select("status", "COUNT(*) AS cnt").groupBy("status");
        Map<String, Long> byStatus = new HashMap<>();
        for (Map<String, Object> row : vehicleMapper.selectMaps(qw)) {
            Object status = row.get("status");
            if (status != null) {
                byStatus.merge(String.valueOf(status), num(row.get("cnt")), Long::sum);
            }
        }

        VehicleStat stat = new VehicleStat();
        stat.setTotal((int) byStatus.values().stream().mapToLong(Long::longValue).sum());
        stat.setIdle(byStatus.getOrDefault(VEHICLE_IDLE, 0L).intValue());
        stat.setBusy(byStatus.getOrDefault(VEHICLE_BUSY, 0L).intValue());
        stat.setBusyThird(byStatus.getOrDefault(VEHICLE_BUSY_THIRD, 0L).intValue());
        stat.setMaintenance(byStatus.getOrDefault(VEHICLE_MAINTENANCE, 0L).intValue());
        stat.setDisabled(byStatus.getOrDefault(VEHICLE_DISABLED, 0L).intValue());
        stat.setExternalOccupyVehicles(stat.getBusyThird());

        // 当日第三方占用时长：仍处于 BUSY_THIRD 标记中的车辆，起点取 third_party_marked_at（为空取 updated_at）
        List<Vehicle> thirds = vehicleMapper.selectList(new LambdaQueryWrapper<Vehicle>()
                .eq(Vehicle::getStatus, VEHICLE_BUSY_THIRD));
        long minutes = 0;
        for (Vehicle vehicle : thirds) {
            LocalDateTime markAt = vehicle.getThirdPartyMarkedAt() != null
                    ? vehicle.getThirdPartyMarkedAt() : vehicle.getUpdatedAt();
            minutes += minutesBetween(markAt, now);
        }
        stat.setExternalOccupyMinutesToday(minutes);
        return stat;
    }

    private StaffStat buildStaffStat() {
        QueryWrapper<Staff> qw = new QueryWrapper<>();
        qw.select("status", "COUNT(*) AS cnt").groupBy("status");
        Map<String, Long> byStatus = new HashMap<>();
        for (Map<String, Object> row : staffMapper.selectMaps(qw)) {
            Object status = row.get("status");
            if (status != null) {
                byStatus.merge(String.valueOf(status), num(row.get("cnt")), Long::sum);
            }
        }

        // 有进行中工单的司机数（去重）
        QueryWrapper<WorkOrder> busyQw = new QueryWrapper<>();
        busyQw.select("COUNT(DISTINCT staff_id) AS cnt")
                .eq("status", STATUS_IN_PROGRESS)
                .isNotNull("staff_id");
        List<Map<String, Object>> rows = workOrderMapper.selectMaps(busyQw);
        long busy = rows.isEmpty() ? 0 : num(rows.get(0).get("cnt"));

        StaffStat stat = new StaffStat();
        long total = byStatus.values().stream().mapToLong(Long::longValue).sum();
        long onDuty = byStatus.getOrDefault("IDLE", 0L) + byStatus.getOrDefault("BUSY", 0L);
        stat.setTotal((int) total);
        stat.setOnDuty((int) onDuty);
        stat.setBusy((int) busy);
        stat.setIdle((int) (onDuty - busy));
        stat.setAway(byStatus.getOrDefault("AWAY", 0L).intValue());
        return stat;
    }

    private CustomerStat buildCustomerStat(LocalDateTime monthStart) {
        CustomerStat stat = new CustomerStat();
        stat.setNewThisMonth(customerMapper.selectCount(new LambdaQueryWrapper<Customer>()
                .ge(Customer::getCreatedAt, monthStart)).intValue());
        stat.setDemandMonth(demandOrderMapper.selectCount(new LambdaQueryWrapper<DemandOrder>()
                .ge(DemandOrder::getCreatedAt, monthStart)).intValue());
        stat.setDemandPending(demandOrderMapper.selectCount(new LambdaQueryWrapper<DemandOrder>()
                .in(DemandOrder::getStatus, DEMAND_PENDING, DEMAND_REVIEWING)).intValue());
        return stat;
    }

    private List<PendingDemand> buildPendingDemands() {
        List<DemandOrder> demands = demandOrderMapper.selectList(new LambdaQueryWrapper<DemandOrder>()
                .in(DemandOrder::getStatus, DEMAND_PENDING, DEMAND_REVIEWING)
                .orderByDesc(DemandOrder::getId)
                .last("LIMIT 8"));
        List<PendingDemand> list = new ArrayList<>(demands.size());
        for (DemandOrder demand : demands) {
            PendingDemand item = new PendingDemand();
            item.setDemandNo(demand.getDemandNo());
            item.setTitle(truncate(demand.getDescription(), 60));
            item.setContactName(demand.getContactName());
            item.setStatus(demand.getStatus());
            item.setCreatedAt(demand.getCreatedAt());
            list.add(item);
        }
        return list;
    }

    private List<StaffRank> buildStaffRank(LocalDateTime monthStart) {
        QueryWrapper<WorkOrder> qw = new QueryWrapper<>();
        qw.select("staff_id", "COUNT(*) AS cnt",
                        "SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END) AS completed_cnt")
                .ge("created_at", monthStart)
                .isNotNull("staff_id")
                .groupBy("staff_id");
        List<Map<String, Object>> rows = workOrderMapper.selectMaps(qw);
        rows.sort(Comparator
                .comparing((Map<String, Object> row) -> num(row.get("cnt")), Comparator.reverseOrder())
                .thenComparing(row -> toLong(row.get("staff_id"))));

        Map<Long, String> staffNames = staffNameMap(rows.stream()
                .map(row -> toLong(row.get("staff_id"))).filter(Objects::nonNull).toList());

        List<StaffRank> rank = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            if (rank.size() >= 5) {
                break;
            }
            rank.add(new StaffRank(toLong(row.get("staff_id")),
                    staffNames.getOrDefault(toLong(row.get("staff_id")), ""),
                    (int) num(row.get("cnt")), (int) num(row.get("completed_cnt"))));
        }
        return rank;
    }

    /* ================================================== 报警 ================================================== */

    private void collectConflictAlarms(List<AlarmItem> items, LocalDateTime now) {
        List<ConflictAlert> alerts = conflictAlertMapper.selectList(new LambdaQueryWrapper<ConflictAlert>()
                .in(ConflictAlert::getStatus, CONFLICT_PENDING, CONFLICT_ESCALATED)
                .orderByDesc(ConflictAlert::getGeneratedAt));
        Map<Long, String> staffNames = staffNameMap(alerts.stream()
                .map(ConflictAlert::getStaffId).filter(Objects::nonNull).collect(Collectors.toSet()));
        for (ConflictAlert alert : alerts) {
            AlarmItem item = new AlarmItem();
            item.setAlarmNo("AL-" + alert.getAlertNo());
            item.setType("CONFLICT");
            item.setLevel("HIGH");
            item.setTargetType("STAFF");
            item.setOrderId(alert.getPrevOrderId());
            item.setOrderNo(alert.getPrevOrderNo());
            item.setStaffId(alert.getStaffId());
            item.setStaffName(alert.getStaffId() != null ? staffNames.getOrDefault(alert.getStaffId(), "") : "");
            item.setSummary(alert.getConflictSummary());
            item.setTriggeredAt(alert.getGeneratedAt());
            item.setDurationMinutes(minutesBetween(alert.getGeneratedAt(), now));
            item.setPinned(true);
            items.add(item);
        }
    }

    /**
     * 接单超时报警：建单时按 order.accept_deadline_minutes（默认 60 分钟，后台可改）固化 accept_deadline_at，
     * 超时仍处于 PENDING_ACCEPT 的工单触发 HIGH 级报警，持续时长自超时点起算。
     */
    private void collectNotAcceptedAlarms(List<AlarmItem> items, LocalDateTime now) {
        List<WorkOrder> orders = workOrderMapper.selectList(new LambdaQueryWrapper<WorkOrder>()
                .eq(WorkOrder::getStatus, STATUS_PENDING_ACCEPT)
                .isNotNull(WorkOrder::getStaffId)
                .isNotNull(WorkOrder::getAcceptDeadlineAt)
                .lt(WorkOrder::getAcceptDeadlineAt, now));
        fillOrderRefAlarm(items, orders, "NOT_ACCEPTED", "HIGH", now);
    }

    private void collectNotArrivedAlarms(List<AlarmItem> items, LocalDateTime now) {
        int graceMinutes = systemConfigService.getInt("alarm.arrive_grace_minutes", 15);
        List<WorkOrder> orders = workOrderMapper.selectList(new LambdaQueryWrapper<WorkOrder>()
                .in(WorkOrder::getStatus, STATUS_PENDING_ACCEPT, STATUS_PENDING_ARRIVE)
                .isNotNull(WorkOrder::getExpectedStartAt)
                .lt(WorkOrder::getExpectedStartAt, now.minusMinutes(graceMinutes)));
        fillOrderRefAlarm(items, orders, "NOT_ARRIVED", "HIGH", now);
    }

    private void collectNotCheckedInAlarms(List<AlarmItem> items, LocalDateTime now) {
        int checkinMinutes = systemConfigService.getInt("alarm.checkin_deadline_minutes", 30);
        List<WorkOrder> orders = workOrderMapper.selectList(new LambdaQueryWrapper<WorkOrder>()
                .eq(WorkOrder::getStatus, STATUS_PENDING_ARRIVE)
                .isNotNull(WorkOrder::getAcceptedAt)
                .lt(WorkOrder::getAcceptedAt, now.minusMinutes(checkinMinutes)));
        fillOrderRefAlarm(items, orders, "NOT_CHECKED_IN", "MEDIUM", now);
    }

    /** 工单维度报警统一填充：未到场按预定时间提示，未签到按接单时间提示 */
    private void fillOrderRefAlarm(List<AlarmItem> items, List<WorkOrder> orders, String type,
                                   String level, LocalDateTime now) {
        if (orders.isEmpty()) {
            return;
        }
        Map<Long, String> staffNames = staffNameMap(orders.stream()
                .map(WorkOrder::getStaffId).filter(Objects::nonNull).collect(Collectors.toSet()));
        Map<Long, Vehicle> vehicleById = vehicleByIdMap(orders.stream()
                .map(WorkOrder::getVehicleId).filter(Objects::nonNull).collect(Collectors.toSet()));
        for (WorkOrder order : orders) {
            AlarmItem item = new AlarmItem();
            item.setAlarmNo("AL-" + order.getOrderNo());
            item.setType(type);
            item.setLevel(level);
            item.setTargetType("ORDER");
            if ("NOT_ARRIVED".equals(type)) {
                item.setSummary("预定 " + (order.getExpectedStartAt() != null
                        ? order.getExpectedStartAt().format(CLOCK_FORMAT) : "") + " 未到场");
                item.setTriggeredAt(order.getExpectedStartAt());
            } else if ("NOT_ACCEPTED".equals(type)) {
                item.setSummary("派单超时未确认接单");
                item.setTriggeredAt(order.getAcceptDeadlineAt() != null
                        ? order.getAcceptDeadlineAt() : order.getCreatedAt());
            } else {
                item.setSummary("接单超时未签到");
                item.setTriggeredAt(order.getAcceptedAt());
            }
            item.setDurationMinutes(minutesBetween(item.getTriggeredAt(), now));
            item.setPinned(true);
            fillOrderRef(item, order, staffNames, vehicleById);
            items.add(item);
        }
    }

    private void collectVehicleOccupiedAlarms(List<AlarmItem> items, LocalDateTime now) {
        int busyHours = systemConfigService.getInt("alarm.vehicle_busy_hours", 12);
        int thirdPartyHours = systemConfigService.getInt("alarm.vehicle_third_party_hours", 24);
        List<Vehicle> vehicles = vehicleMapper.selectList(new LambdaQueryWrapper<Vehicle>()
                .in(Vehicle::getStatus, VEHICLE_BUSY, VEHICLE_BUSY_THIRD));
        if (vehicles.isEmpty()) {
            return;
        }
        // 批量取每辆车最近一条 DISPATCH 记录，作为施工占用起点
        List<VehicleStatusLog> logs = vehicleStatusLogMapper.selectList(new LambdaQueryWrapper<VehicleStatusLog>()
                .in(VehicleStatusLog::getVehicleId, vehicles.stream().map(Vehicle::getId).toList())
                .eq(VehicleStatusLog::getChangeType, "DISPATCH")
                .orderByDesc(VehicleStatusLog::getOccurAt));
        Map<Long, LocalDateTime> lastDispatchAt = new HashMap<>();
        for (VehicleStatusLog log : logs) {
            lastDispatchAt.putIfAbsent(log.getVehicleId(), log.getOccurAt());
        }

        for (Vehicle vehicle : vehicles) {
            LocalDateTime start;
            String level;
            long thresholdMinutes;
            if (VEHICLE_BUSY.equals(vehicle.getStatus())) {
                start = lastDispatchAt.get(vehicle.getId());
                if (start == null) {
                    start = vehicle.getUpdatedAt();
                }
                level = "HIGH";
                thresholdMinutes = busyHours * 60L;
            } else {
                start = vehicle.getThirdPartyMarkedAt() != null
                        ? vehicle.getThirdPartyMarkedAt() : vehicle.getUpdatedAt();
                level = "MEDIUM";
                thresholdMinutes = thirdPartyHours * 60L;
            }
            if (start == null) {
                continue;
            }
            long minutes = minutesBetween(start, now);
            if (minutes <= thresholdMinutes) {
                continue;
            }
            AlarmItem item = new AlarmItem();
            item.setAlarmNo("AL-" + vehicle.getPlateNo());
            item.setType("VEHICLE_OCCUPIED");
            item.setLevel(level);
            item.setTargetType("VEHICLE");
            item.setVehicleId(vehicle.getId());
            item.setPlateNo(vehicle.getPlateNo());
            item.setSummary("车辆持续占用 " + (minutes / 60) + " 小时");
            item.setTriggeredAt(start);
            item.setDurationMinutes(minutes);
            item.setPinned(false);
            items.add(item);
        }
    }

    private void sortAlarms(List<AlarmItem> items) {
        items.sort(Comparator
                .comparing((AlarmItem item) -> ALARM_TYPE_PRIORITY.getOrDefault(item.getType(), 99))
                .thenComparing(item -> Boolean.TRUE.equals(item.isPinned()) ? 0 : 1)
                .thenComparing(item -> ALARM_LEVEL_PRIORITY.getOrDefault(item.getLevel(), 9))
                .thenComparing(AlarmItem::getTriggeredAt, Comparator.nullsLast(Comparator.reverseOrder())));
    }

    /** 报警项工单维度字段填充：订单、车辆、司机引用 */
    private void fillOrderRef(AlarmItem item, WorkOrder order,
                              Map<Long, String> staffNames, Map<Long, Vehicle> vehicleById) {
        item.setOrderId(order.getId());
        item.setOrderNo(order.getOrderNo());
        item.setVehicleId(order.getVehicleId());
        Vehicle vehicle = order.getVehicleId() != null ? vehicleById.get(order.getVehicleId()) : null;
        item.setPlateNo(vehicle != null ? vehicle.getPlateNo() : "");
        item.setStaffId(order.getStaffId());
        item.setStaffName(order.getStaffId() != null ? staffNames.getOrDefault(order.getStaffId(), "") : "");
    }

    /* ============================================ 报警共用判定 ============================================ */

    /**
     * 未到场判定：待接单 / 待到场且预定开始时间超出宽限期仍未到场。
     * 供报警中心与车辆地图共用。
     */
    private boolean isNotArrived(WorkOrder order, LocalDateTime now, int graceMinutes) {
        if (!STATUS_PENDING_ACCEPT.equals(order.getStatus()) && !STATUS_PENDING_ARRIVE.equals(order.getStatus())) {
            return false;
        }
        return order.getExpectedStartAt() != null
                && now.isAfter(order.getExpectedStartAt().plusMinutes(graceMinutes));
    }

    /**
     * 未签到判定：待到场且已接单、接单时间超出签到时限仍未签到。
     * 供报警中心与车辆地图共用。
     */
    private boolean isNotCheckedIn(WorkOrder order, LocalDateTime now, int deadlineMinutes) {
        if (!STATUS_PENDING_ARRIVE.equals(order.getStatus())) {
            return false;
        }
        return order.getAcceptedAt() != null
                && now.isAfter(order.getAcceptedAt().plusMinutes(deadlineMinutes));
    }

    /* ================================================== 工具 ================================================== */

    private LocalDate startOfWeek(LocalDate today) {
        return today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /** 批量查询司机姓名映射 */
    private Map<Long, String> staffNameMap(Collection<Long> staffIds) {
        if (staffIds == null || staffIds.isEmpty()) {
            return Map.of();
        }
        List<Staff> staffList = staffMapper.selectList(new LambdaQueryWrapper<Staff>().in(Staff::getId, staffIds));
        Map<Long, String> map = new HashMap<>();
        for (Staff staff : staffList) {
            map.put(staff.getId(), staff.getName());
        }
        return map;
    }

    /** 批量查询车辆映射 */
    private Map<Long, Vehicle> vehicleByIdMap(Collection<Long> vehicleIds) {
        if (vehicleIds == null || vehicleIds.isEmpty()) {
            return Map.of();
        }
        List<Vehicle> vehicles = vehicleMapper.selectList(new LambdaQueryWrapper<Vehicle>().in(Vehicle::getId, vehicleIds));
        Map<Long, Vehicle> map = new HashMap<>();
        for (Vehicle vehicle : vehicles) {
            map.put(vehicle.getId(), vehicle);
        }
        return map;
    }

    /** 聚合行数值统一读取，兼容各驱动返回的整型包装类型 */
    private long num(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private Long toLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private LocalDate parseDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate date) {
            return date;
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate();
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime.toLocalDate();
        }
        String text = String.valueOf(value);
        return text.length() >= 10 ? LocalDate.parse(text.substring(0, 10)) : null;
    }

    /** 时间差分钟数，起点为空或时钟倒挂时按 0 计 */
    private long minutesBetween(LocalDateTime start, LocalDateTime now) {
        if (start == null) {
            return 0;
        }
        return Math.max(0, Duration.between(start, now).toMinutes());
    }

    private String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }
}
