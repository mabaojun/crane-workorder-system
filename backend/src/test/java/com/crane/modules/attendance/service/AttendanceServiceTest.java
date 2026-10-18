package com.crane.modules.attendance.service;

import com.crane.common.auth.AuthContext;
import com.crane.common.exception.BizException;
import com.crane.modules.attendance.dto.CheckInRequest;
import com.crane.modules.attendance.dto.DailyCheckoutRequest;
import com.crane.modules.attendance.dto.TaskEndRequest;
import com.crane.modules.attendance.entity.Attendance;
import com.crane.modules.attendance.mapper.AttendanceMapper;
import com.crane.modules.attendance.mapper.DailyCheckoutMapper;
import com.crane.modules.attendance.mapper.OvertimeRecordMapper;
import com.crane.modules.exception.mapper.ExceptionRecordMapper;
import com.crane.modules.staff.entity.Vehicle;
import com.crane.modules.staff.mapper.VehicleMapper;
import com.crane.modules.staff.mapper.VehicleStatusLogMapper;
import com.crane.modules.system.service.SystemConfigService;
import com.crane.modules.workorder.entity.WorkOrder;
import com.crane.modules.workorder.mapper.WorkHourStatMapper;
import com.crane.modules.workorder.mapper.WorkOrderMapper;
import com.crane.modules.workorder.service.WorkOrderStateMachine;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 打卡服务单元测试：覆盖范围强校验、照片必传、未批复异常阻断、当日签退判重与结单联动。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AttendanceServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long STAFF_ID = 100L;
    private static final Long ORDER_ID = 10L;
    private static final Long VEHICLE_ID = 20L;

    @Mock
    private AttendanceMapper attendanceMapper;
    @Mock
    private DailyCheckoutMapper dailyCheckoutMapper;
    @Mock
    private OvertimeRecordMapper overtimeRecordMapper;
    @Mock
    private WorkOrderMapper workOrderMapper;
    @Mock
    private WorkHourStatMapper workHourStatMapper;
    @Mock
    private WorkOrderStateMachine workOrderStateMachine;
    @Mock
    private VehicleMapper vehicleMapper;
    @Mock
    private VehicleStatusLogMapper vehicleStatusLogMapper;
    @Mock
    private ExceptionRecordMapper exceptionRecordMapper;
    @Mock
    private SystemConfigService systemConfigService;
    @Mock
    private GeoService geoService;
    @Mock
    private com.crane.modules.exception.service.ExceptionService exceptionService;
    @Mock
    private com.crane.modules.place.service.CommonPlaceService commonPlaceService;

    private AttendanceService service;

    @BeforeEach
    void setUp() {
        service = new AttendanceService(attendanceMapper, dailyCheckoutMapper, overtimeRecordMapper,
                workOrderMapper, workHourStatMapper, workOrderStateMachine, vehicleMapper,
                vehicleStatusLogMapper, exceptionRecordMapper, systemConfigService, geoService,
                new ObjectMapper(), exceptionService, commonPlaceService);
        AuthContext.set(new AuthContext(USER_ID, "DRIVER", "SELF", STAFF_ID, null, List.of()));
        when(systemConfigService.getInt("attendance.valid_range_meters", 1000)).thenReturn(1000);
        when(systemConfigService.getInt("attendance.overtime_range_meters", 1000)).thenReturn(1000);
        when(systemConfigService.getOrDefault("overtime.normal_off_time", "18:00")).thenReturn("18:00");
        // 默认测试阶段口径：定位与照片均不强制；需要验证强校验的用例单独开启
        when(systemConfigService.getOrDefault("attendance.location_required", "false")).thenReturn("true");
        when(systemConfigService.getOrDefault("attendance.photo_required", "false")).thenReturn("true");
        when(exceptionRecordMapper.selectCount(any())).thenReturn(0L);
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    private WorkOrder pendingArriveOrder() {
        WorkOrder order = new WorkOrder();
        order.setId(ORDER_ID);
        order.setStaffId(STAFF_ID);
        order.setVehicleId(VEHICLE_ID);
        order.setStatus("PENDING_ARRIVE");
        order.setLongitude(new BigDecimal("116.397428"));
        order.setLatitude(new BigDecimal("39.918164"));
        return order;
    }

    private CheckInRequest checkInRequest() {
        CheckInRequest req = new CheckInRequest();
        req.setOrderId(ORDER_ID);
        req.setLongitude(new BigDecimal("116.398000"));
        req.setLatitude(new BigDecimal("39.918500"));
        req.setPhotoUrls(List.of("oss://photo/check-in-1.jpg"));
        return req;
    }

    /** 超出签到有效范围必须拒绝并返回 30005，即使打卡记录允许标注 is_in_range = 0 也不放行。 */
    @Test
    void checkInOutOfRangeThrows30005() {
        WorkOrder order = pendingArriveOrder();
        when(workOrderMapper.selectById(ORDER_ID)).thenReturn(order);
        when(geoService.haversine(any(), any(), any(), any())).thenReturn(5000.0);
        CheckInRequest req = checkInRequest();

        BizException ex = assertThrows(BizException.class, () -> service.checkIn(req, USER_ID));
        assertEquals(30005, ex.getCode());
        verify(workOrderMapper).selectById(ORDER_ID);
    }

    /** 签到现场照片必传，缺失返回 30012。 */
    @Test
    void checkInWithoutPhotoThrows30012() {
        WorkOrder order = pendingArriveOrder();
        when(workOrderMapper.selectById(ORDER_ID)).thenReturn(order);
        when(geoService.haversine(any(), any(), any(), any())).thenReturn(50.0);
        CheckInRequest req = checkInRequest();
        req.setPhotoUrls(null);

        BizException ex = assertThrows(BizException.class, () -> service.checkIn(req, USER_ID));
        assertEquals(30012, ex.getCode());
    }

    /** 开关关闭（测试阶段默认）时：无定位、无照片也可签到成功，距离字段留空。 */
    @Test
    void checkInWithoutLocationAndPhotoSucceedsWhenSwitchOff() {
        when(systemConfigService.getOrDefault("attendance.location_required", "false")).thenReturn("false");
        when(systemConfigService.getOrDefault("attendance.photo_required", "false")).thenReturn("false");
        WorkOrder order = pendingArriveOrder();
        when(workOrderMapper.selectById(ORDER_ID)).thenReturn(order);
        CheckInRequest req = checkInRequest();
        req.setLongitude(null);
        req.setLatitude(null);
        req.setPhotoUrls(null);

        Map<String, Object> data = service.checkIn(req, USER_ID);
        assertEquals("IN_PROGRESS", data.get("workOrderStatus"));
        assertNull(data.get("distanceMeters"));
        verify(attendanceMapper).insert(any(Attendance.class));
        verify(workOrderStateMachine).transition(order, "IN_PROGRESS", STAFF_ID, "司机签到");
    }

    /** 开关关闭时：有坐标仍记录距离但不做范围拦截（超范围不抛 30005）。 */
    @Test
    void checkInRangeNotEnforcedWhenLocationSwitchOff() {
        when(systemConfigService.getOrDefault("attendance.location_required", "false")).thenReturn("false");
        WorkOrder order = pendingArriveOrder();
        when(workOrderMapper.selectById(ORDER_ID)).thenReturn(order);
        when(geoService.haversine(any(), any(), any(), any())).thenReturn(50000.0);

        Map<String, Object> data = service.checkIn(checkInRequest(), USER_ID);
        assertEquals("IN_PROGRESS", data.get("workOrderStatus"));
        assertEquals(50000, data.get("distanceMeters"));
    }

    /** 工单存在未批复异常时不可签到，返回 30008。 */
    @Test
    void checkInBlockedByPendingExceptionThrows30008() {
        WorkOrder order = pendingArriveOrder();
        when(workOrderMapper.selectById(ORDER_ID)).thenReturn(order);
        when(exceptionRecordMapper.selectCount(any())).thenReturn(1L);

        BizException ex = assertThrows(BizException.class, () -> service.checkIn(checkInRequest(), USER_ID));
        assertEquals(30008, ex.getCode());
    }

    /** 同一工单当日已有签退记录时重复签退返回 40004。 */
    @Test
    void dailyCheckoutDuplicatedThrows40004() {
        WorkOrder order = pendingArriveOrder();
        order.setStatus("IN_PROGRESS");
        order.setStartedAt(LocalDateTime.now().minusHours(9));
        when(workOrderMapper.selectById(ORDER_ID)).thenReturn(order);
        when(dailyCheckoutMapper.selectCount(any())).thenReturn(1L);
        DailyCheckoutRequest req = new DailyCheckoutRequest();
        req.setOrderId(ORDER_ID);
        req.setLongitude(new BigDecimal("116.398000"));
        req.setLatitude(new BigDecimal("39.918500"));

        BizException ex = assertThrows(BizException.class, () -> service.dailyCheckout(req, USER_ID));
        assertEquals(40004, ex.getCode());
        assertEquals("今日已完成签退", ex.getMessage());
    }

    /** 范围内签到成功：走状态机转 IN_PROGRESS，车辆由空闲转忙碌。 */
    @Test
    void checkInSuccessTransitionsOrderAndMarksVehicleBusy() {
        WorkOrder order = pendingArriveOrder();
        when(workOrderMapper.selectById(ORDER_ID)).thenReturn(order);
        when(geoService.haversine(any(), any(), any(), any())).thenReturn(50.0);
        Vehicle vehicle = new Vehicle();
        vehicle.setId(VEHICLE_ID);
        vehicle.setStatus("IDLE");
        when(vehicleMapper.selectById(VEHICLE_ID)).thenReturn(vehicle);

        Map<String, Object> data = service.checkIn(checkInRequest(), USER_ID);

        verify(workOrderStateMachine).transition(order, "IN_PROGRESS", STAFF_ID, "司机签到");
        verify(vehicleMapper).updateById(vehicle);
        assertEquals("BUSY", vehicle.getStatus());
        assertEquals("IN_PROGRESS", data.get("workOrderStatus"));
    }

    /** 任务结束成功：走状态机转 COMPLETED、释放车辆、落一条工时统计。 */
    @Test
    void taskEndSuccessCompletesOrderAndUpsertsWorkHourStat() {
        WorkOrder order = pendingArriveOrder();
        order.setStatus("IN_PROGRESS");
        order.setStartedAt(LocalDateTime.now().minusHours(9));
        when(workOrderMapper.selectById(ORDER_ID)).thenReturn(order);
        when(geoService.haversine(any(), any(), any(), any())).thenReturn(30.0);
        Vehicle vehicle = new Vehicle();
        vehicle.setId(VEHICLE_ID);
        vehicle.setStatus("BUSY");
        when(vehicleMapper.selectById(VEHICLE_ID)).thenReturn(vehicle);
        when(attendanceMapper.selectCount(any())).thenReturn(1L);
        when(overtimeRecordMapper.selectList(any())).thenReturn(List.of());
        when(workHourStatMapper.selectOne(any())).thenReturn(null);

        TaskEndRequest req = new TaskEndRequest();
        req.setOrderId(ORDER_ID);
        req.setLongitude(new BigDecimal("116.398000"));
        req.setLatitude(new BigDecimal("39.918500"));
        req.setPhotoUrls(List.of("oss://photo/task-end-1.jpg"));

        Map<String, Object> data = service.taskEnd(req, USER_ID);

        verify(workOrderStateMachine).transition(order, "COMPLETED", STAFF_ID, "任务结束");
        verify(vehicleMapper).update(any(), any());
        verify(vehicleStatusLogMapper).insert(any(com.crane.modules.staff.entity.VehicleStatusLog.class));
        verify(workHourStatMapper).insert(any(com.crane.modules.workorder.entity.WorkHourStat.class));
        assertEquals("COMPLETED", data.get("workOrderStatus"));
        assertEquals("IDLE", data.get("vehicleStatus"));
    }
}
