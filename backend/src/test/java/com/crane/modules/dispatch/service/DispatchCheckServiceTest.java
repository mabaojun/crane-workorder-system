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
        when(vehicleMapper.selectById(2L)).thenReturn(busyVehicle());
        lenient().when(staffMapper.selectById(1L)).thenReturn(onDutyStaff());
        lenient().when(workOrderMapper.selectCount(any())).thenReturn(0L);
        lenient().when(systemConfigService.getInt(anyString(), anyInt())).thenReturn(3);

        List<CheckItem> checks = dispatchCheckService.precheck(1L, 2L, LocalDateTime.now().plusHours(1));

        CheckItem item = find(checks, "VEHICLE_IDLE");
        assertThat(item.isPass()).isFalse();
        assertThat(item.getMessage()).contains("非空闲");
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
    void staffWithActiveOrderFails() {
        when(vehicleMapper.selectById(2L)).thenReturn(idleVehicle());
        when(staffMapper.selectById(1L)).thenReturn(onDutyStaff());
        // 第一次查询为进行中工单数（命中互斥），第二次为待接单数
        when(workOrderMapper.selectCount(any())).thenReturn(1L, 0L);
        when(systemConfigService.getInt(anyString(), anyInt())).thenReturn(3);

        List<CheckItem> checks = dispatchCheckService.precheck(1L, 2L, LocalDateTime.now().plusHours(1));

        CheckItem item = find(checks, "STAFF_NO_ACTIVE_ORDER");
        assertThat(item.isPass()).isFalse();
        assertThat(item.getMessage()).contains("进行中");
    }

    @Test
    void pendingDispatchLimitReachedFails() {
        when(vehicleMapper.selectById(2L)).thenReturn(idleVehicle());
        when(staffMapper.selectById(1L)).thenReturn(onDutyStaff());
        // 第一次为进行中工单数（0），第二次为待接单数（达到上限 3）
        when(workOrderMapper.selectCount(any())).thenReturn(0L, 3L);
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
