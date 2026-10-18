package com.crane.modules.stat;

import com.crane.common.exception.BizException;
import com.crane.common.exception.ErrorCode;
import com.crane.modules.customer.mapper.CustomerMapper;
import com.crane.modules.demand.entity.DemandOrder;
import com.crane.modules.demand.mapper.DemandOrderMapper;
import com.crane.modules.exception.mapper.ExceptionRecordMapper;
import com.crane.modules.staff.entity.Staff;
import com.crane.modules.staff.entity.Vehicle;
import com.crane.modules.staff.entity.VehicleStatusLog;
import com.crane.modules.staff.mapper.StaffMapper;
import com.crane.modules.staff.mapper.VehicleMapper;
import com.crane.modules.staff.mapper.VehicleStatusLogMapper;
import com.crane.modules.stat.dto.StatDtos.AlarmItem;
import com.crane.modules.stat.dto.StatDtos.AlarmListVO;
import com.crane.modules.stat.dto.StatDtos.DashboardVO;
import com.crane.modules.stat.dto.StatDtos.ExceptionTypeCount;
import com.crane.modules.stat.dto.StatDtos.MapPoint;
import com.crane.modules.stat.dto.StatDtos.PendingDemand;
import com.crane.modules.stat.dto.StatDtos.StaffPerformance;
import com.crane.modules.stat.dto.StatDtos.StaffRank;
import com.crane.modules.stat.dto.StatDtos.StatusCount;
import com.crane.modules.stat.dto.StatDtos.TrendPoint;
import com.crane.modules.stat.dto.StatDtos.VehicleMapVO;
import com.crane.modules.stat.entity.ConflictAlert;
import com.crane.modules.stat.mapper.ConflictAlertMapper;
import com.crane.modules.stat.service.StatService;
import com.crane.modules.system.service.SystemConfigService;
import com.crane.modules.workorder.entity.WorkOrder;
import com.crane.modules.workorder.mapper.WorkOrderMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 统计域服务单元测试：全流程以 Mockito 桩定 Mapper 聚合结果，
 * 覆盖驾驶舱总览、报警聚合与排序、车辆地图点位与员工绩效计算。
 */
@ExtendWith(MockitoExtension.class)
class StatServiceTest {

    @Mock
    private WorkOrderMapper workOrderMapper;
    @Mock
    private StaffMapper staffMapper;
    @Mock
    private VehicleMapper vehicleMapper;
    @Mock
    private VehicleStatusLogMapper vehicleStatusLogMapper;
    @Mock
    private DemandOrderMapper demandOrderMapper;
    @Mock
    private ExceptionRecordMapper exceptionRecordMapper;
    @Mock
    private CustomerMapper customerMapper;
    @Mock
    private ConflictAlertMapper conflictAlertMapper;
    @Mock
    private SystemConfigService systemConfigService;

    @InjectMocks
    private StatService statService;

    private static final DateTimeFormatter TREND_FORMAT = DateTimeFormatter.ofPattern("MM-dd");

    /** 驾驶舱总览：状态分布补零、时间口径、异常标签映射、车辆/员工/客户统计与排名 */
    @Test
    void dashboardAggregatesAllBlocks() {
        LocalDate today = LocalDate.now();
        // 工单聚合四连：状态分布、进行中司机数、创建日期分桶、本月排名
        when(workOrderMapper.selectMaps(any())).thenReturn(
                rows(row("status", "IN_PROGRESS", "cnt", 3L), row("status", "COMPLETED", "cnt", 2L),
                        row("status", "CANCELLED", "cnt", 1L)),
                rows(row("cnt", 2L)),
                rows(row("d", java.sql.Date.valueOf(today), "cnt", 5L)),
                rows(row("staff_id", 1L, "cnt", 10L, "completed_cnt", 8L),
                        row("staff_id", 2L, "cnt", 7L, "completed_cnt", 7L)));
        when(staffMapper.selectMaps(any())).thenReturn(
                rows(row("status", "IDLE", "cnt", 3L), row("status", "AWAY", "cnt", 1L),
                        row("status", "RESIGNED", "cnt", 1L)));
        when(staffMapper.selectList(any())).thenReturn(List.of(staff(1L, "张三"), staff(2L, "李四")));
        when(vehicleMapper.selectMaps(any())).thenReturn(
                rows(row("status", "IDLE", "cnt", 2L), row("status", "BUSY_THIRD", "cnt", 1L)));
        Vehicle third = new Vehicle();
        third.setId(9L);
        third.setStatus("BUSY_THIRD");
        third.setThirdPartyMarkedAt(LocalDateTime.now().minusMinutes(30));
        when(vehicleMapper.selectList(any())).thenReturn(List.of(third));
        when(exceptionRecordMapper.selectMaps(any())).thenReturn(
                rows(row("type", "RAIN", "cnt", 2L), row("type", "SOME_NEW", "cnt", 1L)));
        when(customerMapper.selectCount(any())).thenReturn(4L);
        when(demandOrderMapper.selectCount(any())).thenReturn(6L, 3L);
        DemandOrder demand1 = new DemandOrder();
        demand1.setId(2L);
        demand1.setDemandNo("XQ202610180002");
        demand1.setStatus("REVIEWING");
        demand1.setContactName("联系人甲");
        demand1.setDescription("需要一台五十吨吊车吊装钢结构" + "字".repeat(80));
        demand1.setCreatedAt(LocalDateTime.now());
        DemandOrder demand2 = new DemandOrder();
        demand2.setId(1L);
        demand2.setDemandNo("XQ202610180001");
        demand2.setStatus("PENDING");
        demand2.setContactName("联系人乙");
        demand2.setDescription("吊装设备");
        demand2.setCreatedAt(LocalDateTime.now());
        when(demandOrderMapper.selectList(any())).thenReturn(List.of(demand1, demand2));

        DashboardVO vo = statService.dashboard();

        // 工单块：六态全量 + 时间口径（分桶仅含今日，故三口径同值）
        assertEquals(5, vo.getOrder().getToday());
        assertEquals(5, vo.getOrder().getWeek());
        assertEquals(5, vo.getOrder().getMonth());
        assertEquals(3, vo.getOrder().getInProgress());
        assertEquals(2, vo.getOrder().getCompleted());
        assertEquals(1, vo.getOrder().getCancelled());
        assertEquals(0, vo.getOrder().getPendingAccept());
        List<StatusCount> distribution = vo.getOrder().getStatusDistribution();
        assertEquals(6, distribution.size());
        assertTrue(distribution.stream().anyMatch(s -> "IN_PROGRESS".equals(s.getStatus()) && s.getCount() == 3));
        assertTrue(distribution.stream().anyMatch(s -> "PENDING_ACCEPT".equals(s.getStatus()) && s.getCount() == 0));

        // 近 7 日趋势：仅今日有量
        List<TrendPoint> trend = vo.getTrend7d();
        assertEquals(7, trend.size());
        assertEquals(today.minusDays(6).format(TREND_FORMAT), trend.get(0).getDate());
        assertEquals(0, trend.get(0).getCount());
        assertEquals(today.format(TREND_FORMAT), trend.get(6).getDate());
        assertEquals(5, trend.get(6).getCount());

        // 异常分布：固定四类补零，未知类型归入「其他」
        List<ExceptionTypeCount> exceptions = vo.getExceptionDistribution();
        assertEquals(5, exceptions.size());
        assertEquals("RAIN", exceptions.get(0).getType());
        assertEquals("下雨", exceptions.get(0).getLabel());
        assertEquals(2, exceptions.get(0).getCount());
        assertEquals("CUSTOM", exceptions.get(3).getType());
        assertEquals("其他", exceptions.get(3).getLabel());
        assertEquals("SOME_NEW", exceptions.get(4).getType());
        assertEquals("其他", exceptions.get(4).getLabel());
        assertEquals(1, exceptions.get(4).getCount());

        // 车辆块：状态分布与第三方占用时长
        assertEquals(3, vo.getVehicle().getTotal());
        assertEquals(2, vo.getVehicle().getIdle());
        assertEquals(1, vo.getVehicle().getBusyThird());
        assertEquals(1, vo.getVehicle().getExternalOccupyVehicles());
        assertEquals(30, vo.getVehicle().getExternalOccupyMinutesToday());

        // 员工块：在职 = 空闲 + 忙碌，忙碌按进行中工单去重，离职仅计入总量
        assertEquals(5, vo.getStaff().getTotal());
        assertEquals(3, vo.getStaff().getOnDuty());
        assertEquals(2, vo.getStaff().getBusy());
        assertEquals(1, vo.getStaff().getIdle());
        assertEquals(1, vo.getStaff().getAway());

        // 客户与需求块
        assertEquals(4, vo.getCustomer().getNewThisMonth());
        assertEquals(6, vo.getCustomer().getDemandMonth());
        assertEquals(3, vo.getCustomer().getDemandPending());

        // 待处理需求单：描述截断 60 字
        List<PendingDemand> demands = vo.getPendingDemands();
        assertEquals(2, demands.size());
        assertEquals("XQ202610180002", demands.get(0).getDemandNo());
        assertEquals(60, demands.get(0).getTitle().length());
        assertEquals("吊装设备", demands.get(1).getTitle());
        assertEquals("PENDING", demands.get(1).getStatus());

        // 本月司机排名：按工单数倒序
        List<StaffRank> rank = vo.getStaffRank();
        assertEquals(2, rank.size());
        assertEquals(1L, rank.get(0).getStaffId());
        assertEquals("张三", rank.get(0).getName());
        assertEquals(10, rank.get(0).getOrderCount());
        assertEquals(8, rank.get(0).getCompletedCount());
        assertEquals("李四", rank.get(1).getName());
    }

    /** 驾驶舱总览：空库时全部归零且结构完整 */
    @Test
    void dashboardReturnsZeroStructureWhenNoData() {
        when(workOrderMapper.selectMaps(any())).thenReturn(new ArrayList<>(), new ArrayList<>(),
                new ArrayList<>(), new ArrayList<>());
        when(staffMapper.selectMaps(any())).thenReturn(new ArrayList<>());
        when(vehicleMapper.selectMaps(any())).thenReturn(new ArrayList<>());
        when(vehicleMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(exceptionRecordMapper.selectMaps(any())).thenReturn(new ArrayList<>());
        when(customerMapper.selectCount(any())).thenReturn(0L);
        when(demandOrderMapper.selectCount(any())).thenReturn(0L, 0L);
        when(demandOrderMapper.selectList(any())).thenReturn(new ArrayList<>());

        DashboardVO vo = statService.dashboard();

        assertEquals(0, vo.getOrder().getToday());
        assertEquals(6, vo.getOrder().getStatusDistribution().size());
        assertEquals(7, vo.getTrend7d().size());
        assertTrue(vo.getTrend7d().stream().allMatch(t -> t.getCount() == 0));
        assertEquals(4, vo.getExceptionDistribution().size());
        assertEquals(0, vo.getVehicle().getTotal());
        assertEquals(0, vo.getVehicle().getExternalOccupyMinutesToday());
        assertEquals(0, vo.getStaff().getTotal());
        assertTrue(vo.getPendingDemands().isEmpty());
        assertTrue(vo.getStaffRank().isEmpty());
    }

    /** 报警中心：四类报警聚合、阈值判定与固定排序（冲突最前、置顶次之、级别、时间倒序） */
    @Test
    void alarmsAggregatesAndSortsByPriority() {
        // 阈值一律取传入默认值
        when(systemConfigService.getInt(anyString(), anyInt())).thenAnswer(inv -> inv.getArgument(1));

        LocalDateTime now = LocalDateTime.now();
        ConflictAlert alert = new ConflictAlert();
        alert.setId(1L);
        alert.setAlertNo("CA202610180001");
        alert.setStaffId(5L);
        alert.setPrevOrderId(11L);
        alert.setPrevOrderNo("GD202610180011");
        alert.setConflictSummary("前后两单服务时间冲突");
        alert.setStatus("PENDING");
        alert.setGeneratedAt(now.minusMinutes(90));
        alert.setIsPinned(1);
        when(conflictAlertMapper.selectList(any())).thenReturn(List.of(alert));

        // 未到场工单（预定 20 分钟前）与未签到工单（接单 40 分钟前）
        WorkOrder notArrived = workOrder(100L, "GD202610180100", "PENDING_ARRIVE", 5L, 7L);
        notArrived.setExpectedStartAt(now.minusMinutes(20));
        WorkOrder notCheckedIn = workOrder(200L, "GD202610180200", "PENDING_ARRIVE", 5L, 7L);
        notCheckedIn.setAcceptedAt(now.minusMinutes(40));
        when(workOrderMapper.selectList(any())).thenReturn(List.of(notArrived), List.of(notCheckedIn));

        // 车辆：BUSY 施工占用 13 小时（超 12 小时阈值），BUSY_THIRD 第三方占用 25 小时（超 24 小时阈值）
        Vehicle busy = new Vehicle();
        busy.setId(7L);
        busy.setPlateNo("鲁A88888");
        busy.setStatus("BUSY");
        Vehicle third = new Vehicle();
        third.setId(8L);
        third.setPlateNo("鲁B99999");
        third.setStatus("BUSY_THIRD");
        third.setThirdPartyMarkedAt(now.minusHours(25));
        when(vehicleMapper.selectList(any())).thenReturn(List.of(busy, third));
        VehicleStatusLog dispatchLog = new VehicleStatusLog();
        dispatchLog.setVehicleId(7L);
        dispatchLog.setChangeType("DISPATCH");
        dispatchLog.setOccurAt(now.minusHours(13));
        when(vehicleStatusLogMapper.selectList(any())).thenReturn(List.of(dispatchLog));
        when(staffMapper.selectList(any())).thenReturn(List.of(staff(5L, "王五")));

        AlarmListVO vo = statService.alarms();

        assertEquals(5, vo.getCount());
        List<AlarmItem> list = vo.getList();
        // 冲突最前
        assertEquals("CONFLICT", list.get(0).getType());
        assertEquals("AL-CA202610180001", list.get(0).getAlarmNo());
        assertEquals("HIGH", list.get(0).getLevel());
        assertEquals("STAFF", list.get(0).getTargetType());
        assertTrue(list.get(0).isPinned());
        assertEquals("GD202610180011", list.get(0).getOrderNo());
        assertEquals("王五", list.get(0).getStaffName());
        assertEquals(90, list.get(0).getDurationMinutes());
        // 未到场：HIGH、置顶、按预定时间提示
        assertEquals("NOT_ARRIVED", list.get(1).getType());
        assertEquals("HIGH", list.get(1).getLevel());
        assertTrue(list.get(1).isPinned());
        assertEquals("预定 " + notArrived.getExpectedStartAt().format(DateTimeFormatter.ofPattern("HH:mm")) + " 未到场",
                list.get(1).getSummary());
        assertEquals(20, list.get(1).getDurationMinutes());
        assertEquals("鲁A88888", list.get(1).getPlateNo());
        // 未签到：MEDIUM、置顶
        assertEquals("NOT_CHECKED_IN", list.get(2).getType());
        assertEquals("MEDIUM", list.get(2).getLevel());
        assertTrue(list.get(2).isPinned());
        assertEquals("接单超时未签到", list.get(2).getSummary());
        assertEquals(40, list.get(2).getDurationMinutes());
        // 车辆占用：置顶为假，BUSY 高级别在前、BUSY_THIRD 次之
        assertEquals("VEHICLE_OCCUPIED", list.get(3).getType());
        assertEquals("HIGH", list.get(3).getLevel());
        assertEquals("车辆持续占用 13 小时", list.get(3).getSummary());
        assertEquals(780, list.get(3).getDurationMinutes());
        assertTrue(!list.get(3).isPinned());
        assertEquals("VEHICLE_OCCUPIED", list.get(4).getType());
        assertEquals("MEDIUM", list.get(4).getLevel());
        assertEquals("车辆持续占用 25 小时", list.get(4).getSummary());
        assertEquals("鲁B99999", list.get(4).getPlateNo());
    }

    /** 车辆地图：缺坐标工单不产出点位，命中未到场 / 未签到规则时打报警标记 */
    @Test
    void vehicleMapSkipsMissingCoordinatesAndMarksAlarm() {
        when(systemConfigService.getInt(anyString(), anyInt())).thenAnswer(inv -> inv.getArgument(1));

        LocalDateTime now = LocalDateTime.now();
        WorkOrder withAlarm = workOrder(100L, "GD202610180100", "PENDING_ARRIVE", 5L, 7L);
        withAlarm.setLongitude(new BigDecimal("117.12"));
        withAlarm.setLatitude(new BigDecimal("36.71"));
        withAlarm.setExpectedStartAt(now.minusMinutes(30));
        withAlarm.setUpdatedAt(now.minusMinutes(10));
        WorkOrder noCoordinates = workOrder(101L, "GD202610180101", "IN_PROGRESS", 5L, 7L);
        WorkOrder calm = workOrder(102L, "GD202610180102", "SUSPENDED", 5L, 7L);
        calm.setLongitude(new BigDecimal("117.20"));
        calm.setLatitude(new BigDecimal("36.80"));
        calm.setUpdatedAt(now.minusMinutes(5));
        when(workOrderMapper.selectList(any())).thenReturn(List.of(withAlarm, noCoordinates, calm));
        when(vehicleMapper.selectCount(any())).thenReturn(10L);
        when(vehicleMapper.selectList(any())).thenReturn(List.of(vehicle(7L, "鲁A88888")));
        when(staffMapper.selectList(any())).thenReturn(List.of(staff(5L, "王五")));

        VehicleMapVO vo = statService.vehicleMap();

        assertEquals(10, vo.getTotal());
        assertEquals(2, vo.getOnMap());
        List<MapPoint> points = vo.getPoints();
        assertEquals(2, points.size());
        assertEquals("GD202610180100", points.get(0).getOrderNo());
        assertTrue(points.get(0).isAlarm());
        assertEquals("鲁A88888", points.get(0).getPlateNo());
        assertEquals("王五", points.get(0).getStaffName());
        assertEquals("PENDING_ARRIVE", points.get(0).getStatus());
        assertEquals("GD202610180102", points.get(1).getOrderNo());
        assertTrue(!points.get(1).isAlarm());
    }

    /** 员工绩效：完工率保留两位小数，按工单数倒序 */
    @Test
    void staffPerformanceComputesRateAndSorts() {
        when(workOrderMapper.selectMaps(any())).thenReturn(
                rows(row("staff_id", 1L, "cnt", 4L, "completed_cnt", 3L),
                        row("staff_id", 2L, "cnt", 2L, "completed_cnt", 1L)));
        when(staffMapper.selectList(any())).thenReturn(List.of(staff(1L, "张三"), staff(2L, "李四")));

        List<StaffPerformance> result = statService.staffPerformance("MONTH");

        assertEquals(2, result.size());
        assertEquals(1L, result.get(0).getStaffId());
        assertEquals("张三", result.get(0).getName());
        assertEquals(4, result.get(0).getOrderCount());
        assertEquals(3, result.get(0).getCompletedCount());
        assertEquals(0, result.get(0).getCompletionRate().compareTo(new BigDecimal("75.00")));
        assertEquals(0, result.get(1).getCompletionRate().compareTo(new BigDecimal("50.00")));
    }

    /** 员工绩效：周期参数缺失与非法分别返回 20001 / 20002 */
    @Test
    void staffPerformanceRejectsInvalidPeriod() {
        BizException missing = assertThrows(BizException.class, () -> statService.staffPerformance(null));
        assertEquals(ErrorCode.PARAM_MISSING.getCode(), missing.getCode());
        BizException invalid = assertThrows(BizException.class, () -> statService.staffPerformance("YEAR"));
        assertEquals(ErrorCode.PARAM_INVALID.getCode(), invalid.getCode());
    }

    /* ================================================== 桩数据 ================================================== */

    private WorkOrder workOrder(Long id, String orderNo, String status, Long staffId, Long vehicleId) {
        WorkOrder order = new WorkOrder();
        order.setId(id);
        order.setOrderNo(orderNo);
        order.setStatus(status);
        order.setStaffId(staffId);
        order.setVehicleId(vehicleId);
        return order;
    }

    private Staff staff(Long id, String name) {
        Staff staff = new Staff();
        staff.setId(id);
        staff.setName(name);
        return staff;
    }

    private Vehicle vehicle(Long id, String plateNo) {
        Vehicle vehicle = new Vehicle();
        vehicle.setId(id);
        vehicle.setPlateNo(plateNo);
        return vehicle;
    }

    private List<Map<String, Object>> rows(Map<String, Object>... rowArray) {
        return new ArrayList<>(List.of(rowArray));
    }

    private Map<String, Object> row(Object... keyValues) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return map;
    }
}
