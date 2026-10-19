package com.crane.modules.dispatch.service;

import com.crane.modules.dispatch.dto.CheckItem;
import com.crane.modules.staff.entity.Staff;
import com.crane.modules.staff.entity.Vehicle;
import com.crane.modules.staff.mapper.StaffMapper;
import com.crane.modules.staff.mapper.VehicleMapper;
import com.crane.modules.system.service.SystemConfigService;
import com.crane.modules.workorder.mapper.WorkOrderMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 派单前置校验单元测试：五项规则的通过与不通过场景。
 */
@ExtendWith(MockitoExtension.class)
class DispatchCheckServiceTest {

    @Mock
    private VehicleMapper vehicleMapper;

    @Mock
    private StaffMapper staffMapper;

    @Mock
    private WorkOrderMapper workOrderMapper;

    @Mock
    private SystemConfigService systemConfigService;

    @InjectMocks
    private DispatchCheckService dispatchCheckService;

    private Vehicle idleVehicle() {
        Vehicle vehicle = new Vehicle();
        vehicle.setId(2L);
        vehicle.setStatus("IDLE");
        return vehicle;
    }

    private Vehicle busyVehicle() {
        Vehicle vehicle = new Vehicle();
        vehicle.setId(2L);
        vehicle.setStatus("BUSY");
        return vehicle;
    }

    private Staff onDutyStaff() {
        Staff staff = new Staff();
        staff.setId(1L);
        staff.setStatus("IDLE");
        return staff;
    }

    @Test
    void allPassWhenEverythingOk() {
        when(vehicleMapper.selectById(2L)).thenReturn(idleVehicle());
        when(staffMapper.selectById(1L)).thenReturn(onDutyStaff());
        when(workOrderMapper.selectCount(any())).thenReturn(0L);
        when(systemConfigService.getInt(anyString(), anyInt())).thenReturn(3);

        List<CheckItem> checks = dispatchCheckService.precheck(1L, 2L, LocalDateTime.now().plusHours(1));

        Map<String, CheckItem> byCode = checks.stream()
                .collect(Collectors.toMap(CheckItem::getCode, Function.identity()));
        assertThat(byCode).containsKeys("VEHICLE_IDLE", "STAFF_ON_DUTY", "STAFF_NO_ACTIVE_ORDER",
                "PENDING_DISPATCH_LIMIT", "TIME_VALID");
        assertThat(byCode.values()).allSatisfy(CheckItem::isPass);
    }

    @Test
    void vehicleNotIdleFails() {
        Vehicle maintenance = busyVehicle();
        maintenance.setStatus("MAINTENANCE");
        when(vehicleMapper.selectById(2L)).thenReturn(maintenance);
        lenient().when(staffMapper.selectById(1L)).thenReturn(onDutyStaff());
        lenient().when(workOrderMapper.selectCount(any())).thenReturn(0L);
        lenient().when(systemConfigService.getInt(anyString(), anyInt())).thenReturn(3);

        List<CheckItem> checks = dispatchCheckService.precheck(1L, 2L, LocalDateTime.now().plusHours(1));

        CheckItem item = find(checks, "VEHICLE_IDLE");
        assertThat(item.isPass()).isFalse();
        assertThat(item.getMessage()).contains("维保");
    }

    @Test
    void disabledVehicleFails() {
        Vehicle disabled = busyVehicle();
        disabled.setStatus("DISABLED");
        when(vehicleMapper.selectById(2L)).thenReturn(disabled);

        List<CheckItem> checks = dispatchCheckService.precheck(1L, 2L, LocalDateTime.now().plusHours(1));

        CheckItem item = find(checks, "VEHICLE_IDLE");
        assertThat(item.isPass()).isFalse();
        assertThat(item.getMessage()).contains("停用");
    }

    @Test
    void thirdPartyAwayVehicleFails() {
        Vehicle third = busyVehicle();
        third.setStatus("BUSY_THIRD");
        when(vehicleMapper.selectById(2L)).thenReturn(third);

        List<CheckItem> checks = dispatchCheckService.precheck(1L, 2L, LocalDateTime.now().plusHours(1));

        CheckItem item = find(checks, "VEHICLE_IDLE");
        assertThat(item.isPass()).isFalse();
        assertThat(item.getMessage()).contains("第三方");
    }

    @Test
    void busyVehicleWithTimeConflictFails() {
        when(vehicleMapper.selectById(2L)).thenReturn(busyVehicle());
        // 活跃工单司机为 3L（非本次派单司机 1L），不构成同班组插单豁免
        when(workOrderMapper.selectList(any())).thenReturn(List.of(activeOrder(9L, "GD202610180001",
                3L, 2L, LocalDateTime.now().plusHours(2))));

        List<CheckItem> checks = dispatchCheckService.precheck(1L, 2L, LocalDateTime.now().plusHours(3));

        CheckItem item = find(checks, "VEHICLE_IDLE");
        assertThat(item.isPass()).isFalse();
        assertThat(item.getMessage()).contains("时间冲突").contains("GD202610180001");
    }

    @Test
    void busyVehicleWithWideTimeGapPasses() {
        when(vehicleMapper.selectById(2L)).thenReturn(busyVehicle());
        when(workOrderMapper.selectList(any())).thenReturn(List.of(activeOrder(9L, "GD202610180001",
                3L, 2L, LocalDateTime.now().plusHours(2))));
        lenient().when(staffMapper.selectById(1L)).thenReturn(onDutyStaff());
        lenient().when(workOrderMapper.selectCount(any())).thenReturn(0L);
        lenient().when(systemConfigService.getInt(anyString(), anyInt())).thenReturn(3);

        // 派单时刻距现有工单 8 小时，时段充裕应放行
        List<CheckItem> checks = dispatchCheckService.precheck(1L, 2L, LocalDateTime.now().plusHours(10));

        assertThat(find(checks, "VEHICLE_IDLE").isPass()).isTrue();
    }

    @Test
    void busyVehicleWithoutPlanStartFails() {
        when(vehicleMapper.selectById(2L)).thenReturn(busyVehicle());

        List<CheckItem> checks = dispatchCheckService.precheck(1L, 2L, null);

        CheckItem item = find(checks, "VEHICLE_IDLE");
        assertThat(item.isPass()).isFalse();
        assertThat(item.getMessage()).contains("计划开始时间");
    }

    private com.crane.modules.workorder.entity.WorkOrder activeOrder(Long id, String orderNo,
                                                                      Long staffId, Long vehicleId,
                                                                      LocalDateTime startAt) {
        com.crane.modules.workorder.entity.WorkOrder order = new com.crane.modules.workorder.entity.WorkOrder();
        order.setId(id);
        order.setOrderNo(orderNo);
        order.setStaffId(staffId);
        order.setVehicleId(vehicleId);
        order.setStatus("IN_PROGRESS");
        order.setExpectedStartAt(startAt);
        return order;
    }

    @Test
    void staffAwayFails() {
        when(vehicleMapper.selectById(2L)).thenReturn(idleVehicle());
        Staff away = onDutyStaff();
        away.setStatus("AWAY");
        when(staffMapper.selectById(1L)).thenReturn(away);
        lenient().when(workOrderMapper.selectCount(any())).thenReturn(0L);
        lenient().when(systemConfigService.getInt(anyString(), anyInt())).thenReturn(3);

        List<CheckItem> checks = dispatchCheckService.precheck(1L, 2L, LocalDateTime.now().plusHours(1));

        CheckItem item = find(checks, "STAFF_ON_DUTY");
        assertThat(item.isPass()).isFalse();
        assertThat(item.getMessage()).contains("离岗");
    }

    @Test
    void staffWithActiveOrderStillPasses() {
        // 司机工单并发放开：有进行中工单（不同车）也放行派发，取舍权在司机（接单判断/退单）
        when(vehicleMapper.selectById(2L)).thenReturn(idleVehicle());
        when(staffMapper.selectById(1L)).thenReturn(onDutyStaff());
        when(workOrderMapper.selectCount(any())).thenReturn(0L);
        when(systemConfigService.getInt(anyString(), anyInt())).thenReturn(3);

        List<CheckItem> checks = dispatchCheckService.precheck(1L, 2L, LocalDateTime.now().plusHours(1));

        assertThat(find(checks, "STAFF_NO_ACTIVE_ORDER").isPass()).isTrue();
    }

    @Test
    void sameCrewBusyVehicleTimeWindowExempted() {
        // 车辆时段豁免：司机正执行工单 9（同车 2，1 小时后开工），插单 B 同车同司机不参与时段判断
        when(vehicleMapper.selectById(2L)).thenReturn(busyVehicle());
        when(staffMapper.selectById(1L)).thenReturn(onDutyStaff());
        when(workOrderMapper.selectList(any())).thenReturn(List.of(activeOrder(9L, "GD202610180009",
                1L, 2L, LocalDateTime.now().plusHours(1))));
        when(systemConfigService.getInt(anyString(), anyInt())).thenReturn(3);

        List<CheckItem> checks = dispatchCheckService.precheck(1L, 2L, LocalDateTime.now().plusHours(2));

        assertThat(find(checks, "VEHICLE_IDLE").isPass()).isTrue();
    }

    @Test
    void pendingDispatchLimitReachedFails() {
        when(vehicleMapper.selectById(2L)).thenReturn(idleVehicle());
        when(staffMapper.selectById(1L)).thenReturn(onDutyStaff());
        // 待接单派单数达到上限 3
        when(workOrderMapper.selectCount(any())).thenReturn(3L);
        when(systemConfigService.getInt(anyString(), anyInt())).thenReturn(3);

        List<CheckItem> checks = dispatchCheckService.precheck(1L, 2L, LocalDateTime.now().plusHours(1));

        CheckItem item = find(checks, "PENDING_DISPATCH_LIMIT");
        assertThat(item.isPass()).isFalse();
        assertThat(item.getMessage()).contains("上限");
    }

    @Test
    void pastStartTimeFails() {
        when(vehicleMapper.selectById(2L)).thenReturn(idleVehicle());
        when(staffMapper.selectById(1L)).thenReturn(onDutyStaff());
        when(workOrderMapper.selectCount(any())).thenReturn(0L);
        when(systemConfigService.getInt(anyString(), anyInt())).thenReturn(3);

        List<CheckItem> checks = dispatchCheckService.precheck(1L, 2L, LocalDateTime.now().minusHours(1));

        CheckItem item = find(checks, "TIME_VALID");
        assertThat(item.isPass()).isFalse();
        assertThat(item.getMessage()).contains("二次确认");
    }

    @Test
    void nullPlanStartAtPassesTimeCheck() {
        when(vehicleMapper.selectById(2L)).thenReturn(idleVehicle());
        when(staffMapper.selectById(1L)).thenReturn(onDutyStaff());
        when(workOrderMapper.selectCount(any())).thenReturn(0L);
        when(systemConfigService.getInt(anyString(), anyInt())).thenReturn(3);

        List<CheckItem> checks = dispatchCheckService.precheck(1L, 2L, null);

        assertThat(find(checks, "TIME_VALID").isPass()).isTrue();
    }

    @Test
    void staffResignedFails() {
        when(vehicleMapper.selectById(2L)).thenReturn(idleVehicle());
        Staff resigned = onDutyStaff();
        resigned.setStatus("RESIGNED");
        when(staffMapper.selectById(1L)).thenReturn(resigned);
        lenient().when(workOrderMapper.selectCount(any())).thenReturn(0L);
        lenient().when(systemConfigService.getInt(anyString(), anyInt())).thenReturn(3);

        List<CheckItem> checks = dispatchCheckService.precheck(1L, 2L, LocalDateTime.now().plusHours(1));

        assertThat(find(checks, "STAFF_ON_DUTY").isPass()).isFalse();
    }

    private CheckItem find(List<CheckItem> checks, String code) {
        return checks.stream()
                .filter(c -> code.equals(c.getCode()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("缺少校验项: " + code));
    }
}
