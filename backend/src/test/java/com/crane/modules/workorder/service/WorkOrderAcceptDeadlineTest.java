package com.crane.modules.workorder.service;

import com.crane.modules.customer.mapper.CustomerMapper;
import com.crane.modules.exception.service.ExceptionService;
import com.crane.modules.notify.service.NotifyService;
import com.crane.modules.place.service.CommonPlaceService;
import com.crane.modules.dispatch.service.DispatchCheckService;
import com.crane.modules.staff.mapper.StaffMapper;
import com.crane.modules.staff.mapper.VehicleBindingMapper;
import com.crane.modules.staff.mapper.VehicleMapper;
import com.crane.modules.staff.mapper.VehicleStatusLogMapper;
import com.crane.modules.system.service.SystemConfigService;
import com.crane.modules.workorder.mapper.CustomerReviewMapper;
import com.crane.modules.workorder.mapper.WorkHourStatMapper;
import com.crane.modules.workorder.mapper.WorkOrderMapper;
import com.crane.modules.workorder.mapper.WorkOrderStatusLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 接单截止时间公式单元测试：
 * 底线 = 约定开始前 2 小时；班内下的第二天的单截止前一天 18:00；下班后下的单 1 小时内确认。
 */
@ExtendWith(MockitoExtension.class)
class WorkOrderAcceptDeadlineTest {

    @Mock
    private WorkOrderMapper workOrderMapper;
    @Mock
    private WorkOrderStatusLogMapper statusLogMapper;
    @Mock
    private CustomerReviewMapper customerReviewMapper;
    @Mock
    private WorkHourStatMapper workHourStatMapper;
    @Mock
    private StaffMapper staffMapper;
    @Mock
    private VehicleMapper vehicleMapper;
    @Mock
    private VehicleBindingMapper vehicleBindingMapper;
    @Mock
    private VehicleStatusLogMapper vehicleStatusLogMapper;
    @Mock
    private CustomerMapper customerMapper;
    @Mock
    private SystemConfigService systemConfigService;
    @Mock
    private WorkOrderStateMachine stateMachine;
    @Mock
    private NotifyService notifyService;
    @Mock
    private ExceptionService exceptionService;
    @Mock
    private CommonPlaceService commonPlaceService;
    @Mock
    private DispatchCheckService dispatchCheckService;

    private WorkOrderService workOrderService;

    @BeforeEach
    void setUp() {
        workOrderService = new WorkOrderService(workOrderMapper, statusLogMapper, customerReviewMapper,
                workHourStatMapper, staffMapper, vehicleMapper, vehicleBindingMapper, vehicleStatusLogMapper,
                customerMapper, systemConfigService, stateMachine, notifyService, exceptionService,
                commonPlaceService, dispatchCheckService);
    }

    private void stubConfig(int leadMinutes, int afterOffMinutes) {
        lenient().when(systemConfigService.getInt("order.accept_lead_minutes", 120)).thenReturn(leadMinutes);
        lenient().when(systemConfigService.getInt("order.after_off_accept_minutes", 60)).thenReturn(afterOffMinutes);
        lenient().when(systemConfigService.getOrDefault("overtime.normal_off_time", "18:00")).thenReturn("18:00");
    }

    @Test
    void 班内下的今天单截止为开始前两小时() {
        stubConfig(120, 60);
        LocalDateTime now = LocalDateTime.of(LocalDate.now(), LocalTime.of(10, 0));
        LocalDateTime start = now.plusHours(5); // 今天 15:00 开工

        LocalDateTime deadline = workOrderService.computeAcceptDeadline(start, now);

        assertEquals(start.minusHours(2), deadline);
    }

    @Test
    void 班内下的第二天单截止为前一天下班时间() {
        stubConfig(120, 60);
        LocalDateTime now = LocalDateTime.of(LocalDate.now(), LocalTime.of(10, 0));
        LocalDateTime start = LocalDate.now().plusDays(1).atTime(8, 0); // 明天 08:00 开工

        LocalDateTime deadline = workOrderService.computeAcceptDeadline(start, now);

        // 底线为明天 06:00，但须今天 18:00 下班前确认，取更早
        assertEquals(LocalDate.now().atTime(18, 0), deadline);
    }

    @Test
    void 下班后下的单一小时内确认() {
        stubConfig(120, 60);
        LocalDateTime now = LocalDateTime.of(LocalDate.now(), LocalTime.of(19, 0)); // 下班后
        LocalDateTime start = LocalDate.now().plusDays(1).atTime(8, 0); // 明天 08:00 开工

        LocalDateTime deadline = workOrderService.computeAcceptDeadline(start, now);

        // 1 小时规则（今天 20:00）早于底线（明天 06:00）
        assertEquals(now.plusHours(1), deadline);
    }

    @Test
    void 下班后下单且开始临近时取更早的底线() {
        stubConfig(120, 60);
        LocalDateTime now = LocalDateTime.of(LocalDate.now(), LocalTime.of(19, 0));
        LocalDateTime start = now.plusHours(2); // 今天 21:00 开工，底线 19:00 已过

        LocalDateTime deadline = workOrderService.computeAcceptDeadline(start, now);

        assertEquals(start.minusHours(2), deadline);
    }

    @Test
    void 底线分钟数可配() {
        stubConfig(30, 60);
        LocalDateTime now = LocalDateTime.of(LocalDate.now(), LocalTime.of(10, 0));
        LocalDateTime start = now.plusHours(5);

        LocalDateTime deadline = workOrderService.computeAcceptDeadline(start, now);

        assertEquals(start.minusMinutes(30), deadline);
    }
}
