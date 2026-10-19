package com.crane.modules.staff;

import com.crane.common.exception.BizException;
import com.crane.modules.staff.dto.VehicleUpsertRequest;
import com.crane.modules.staff.entity.Vehicle;
import com.crane.modules.staff.mapper.StaffMapper;
import com.crane.modules.staff.mapper.VehicleMapper;
import com.crane.modules.staff.mapper.VehicleStatusLogMapper;
import com.crane.modules.staff.service.VehicleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 车辆档案服务单元测试：吊车/举升车类型规格互斥校验、手动状态变更权限与白名单。
 */
class VehicleServiceTest {

    private VehicleMapper vehicleMapper;
    private VehicleStatusLogMapper vehicleStatusLogMapper;
    private StaffMapper staffMapper;
    private VehicleService vehicleService;

    @BeforeEach
    void setUp() {
        vehicleMapper = mock(VehicleMapper.class);
        vehicleStatusLogMapper = mock(VehicleStatusLogMapper.class);
        staffMapper = mock(StaffMapper.class);
        vehicleService = new VehicleService(vehicleMapper, vehicleStatusLogMapper, staffMapper);
    }

    private VehicleUpsertRequest craneRequest() {
        VehicleUpsertRequest req = new VehicleUpsertRequest();
        req.setPlateNo("鲁A12345");
        req.setType("CRANE");
        req.setTonnage(new BigDecimal("25"));
        return req;
    }

    private VehicleUpsertRequest liftRequest() {
        VehicleUpsertRequest req = new VehicleUpsertRequest();
        req.setPlateNo("鲁B66666");
        req.setType("LIFT");
        req.setLiftHeight(new BigDecimal("20"));
        return req;
    }

    private void mockCreatePass() {
        when(vehicleMapper.selectCount(any())).thenReturn(0L);
        when(vehicleMapper.insert(any(Vehicle.class))).thenAnswer(inv -> {
            inv.getArgument(0, Vehicle.class).setId(1L);
            return 1;
        });
    }

    @Test
    void 举升车缺举升高度应抛出20001() {
        VehicleUpsertRequest req = liftRequest();
        req.setLiftHeight(null);
        BizException e = assertThrows(BizException.class, () -> vehicleService.create(req, 1L));
        assertEquals(20001, e.getCode());
    }

    @Test
    void 吊车缺吨位应抛出20001() {
        VehicleUpsertRequest req = craneRequest();
        req.setTonnage(null);
        BizException e = assertThrows(BizException.class, () -> vehicleService.create(req, 1L));
        assertEquals(20001, e.getCode());
    }

    @Test
    void 非法车辆类型应抛出20008() {
        VehicleUpsertRequest req = craneRequest();
        req.setType("TRUCK");
        BizException e = assertThrows(BizException.class, () -> vehicleService.create(req, 1L));
        assertEquals(20008, e.getCode());
    }

    @Test
    void 举升车创建成功且吨位被互斥清空() {
        mockCreatePass();
        VehicleUpsertRequest req = liftRequest();
        req.setTonnage(new BigDecimal("25"));
        Vehicle vehicle = vehicleService.create(req, 1L);
        assertEquals("LIFT", vehicle.getType());
        assertEquals(new BigDecimal("20"), vehicle.getLiftHeight());
        assertNull(vehicle.getTonnage());
    }

    @Test
    void 吊车创建成功且举升高度被互斥清空() {
        mockCreatePass();
        VehicleUpsertRequest req = craneRequest();
        req.setLiftHeight(new BigDecimal("30"));
        Vehicle vehicle = vehicleService.create(req, 1L);
        assertEquals("CRANE", vehicle.getType());
        assertEquals(new BigDecimal("25"), vehicle.getTonnage());
        assertNull(vehicle.getLiftHeight());
    }

    @Test
    void 缺省类型按吊车处理() {
        mockCreatePass();
        VehicleUpsertRequest req = craneRequest();
        req.setType(null);
        Vehicle vehicle = vehicleService.create(req, 1L);
        assertEquals("CRANE", vehicle.getType());
    }

    /** 构造 17 位 VIN，第 10 位为年款码 */
    private String vinWithYearCode(char code) {
        return "LSVAA24A0" + code + "1234567";
    }

    @Test
    void 车龄超过15年按VIN自动带出半年一审() {
        mockCreatePass();
        VehicleUpsertRequest req = craneRequest();
        req.setVin(vinWithYearCode('A')); // 2010 年款，车龄已超 15 年
        Vehicle vehicle = vehicleService.create(req, 1L);
        assertEquals("HALF_YEAR", vehicle.getInspectionCycle());
        assertEquals(vinWithYearCode('A'), vehicle.getVin());
    }

    @Test
    void 车龄不足15年按VIN自动带出一年一审() {
        mockCreatePass();
        VehicleUpsertRequest req = craneRequest();
        req.setVin(vinWithYearCode('Y')); // 2030 年款，车龄不足 15 年
        Vehicle vehicle = vehicleService.create(req, 1L);
        assertEquals("YEAR", vehicle.getInspectionCycle());
    }

    @Test
    void 未填车架号默认一年一审() {
        mockCreatePass();
        Vehicle vehicle = vehicleService.create(craneRequest(), 1L);
        assertEquals("YEAR", vehicle.getInspectionCycle());
    }

    @Test
    void 显式指定审车周期优先于VIN推算() {
        mockCreatePass();
        VehicleUpsertRequest req = craneRequest();
        req.setVin(vinWithYearCode('A'));
        req.setInspectionCycle("YEAR");
        Vehicle vehicle = vehicleService.create(req, 1L);
        assertEquals("YEAR", vehicle.getInspectionCycle());
    }

    @Test
    void 非法审车周期应抛出20008() {
        VehicleUpsertRequest req = craneRequest();
        req.setInspectionCycle("QUARTER");
        BizException e = assertThrows(BizException.class, () -> vehicleService.create(req, 1L));
        assertEquals(20008, e.getCode());
    }

    @Test
    void 证照信息落库() {
        mockCreatePass();
        VehicleUpsertRequest req = craneRequest();
        LocalDate insurance = LocalDate.now().minusMonths(11);
        LocalDate inspection = LocalDate.now().minusMonths(5);
        req.setVin(vinWithYearCode('Y'));
        req.setInsurancePurchaseDate(insurance);
        req.setInspectionDate(inspection);
        Vehicle vehicle = vehicleService.create(req, 1L);
        assertEquals(insurance, vehicle.getInsurancePurchaseDate());
        assertEquals(inspection, vehicle.getInspectionDate());
    }

    @Test
    void 管理员手动变更不允许设作业中() {
        BizException e = assertThrows(BizException.class,
                () -> vehicleService.manualChangeStatus(1L, "BUSY", 1L));
        assertEquals(40004, e.getCode());
    }

    @Test
    void 管理员手动变更非法状态应抛出20008() {
        BizException e = assertThrows(BizException.class,
                () -> vehicleService.manualChangeStatus(1L, "FLYING", 1L));
        assertEquals(20008, e.getCode());
    }

    @Test
    void 作业中车辆管理员不可手动变更() {
        Vehicle vehicle = new Vehicle();
        vehicle.setId(1L);
        vehicle.setStatus("BUSY");
        when(vehicleMapper.selectById(1L)).thenReturn(vehicle);
        BizException e = assertThrows(BizException.class,
                () -> vehicleService.manualChangeStatus(1L, "MAINTENANCE", 1L));
        assertEquals(40004, e.getCode());
    }

    @Test
    void 司机仅可切换白名单状态() {
        BizException e = assertThrows(BizException.class,
                () -> vehicleService.driverChangeStatus(1L, "DISABLED", 7L, 7L));
        assertEquals(20008, e.getCode());
    }

    @Test
    void 司机不可变更非本人常用设备() {
        Vehicle vehicle = new Vehicle();
        vehicle.setId(1L);
        vehicle.setStatus("IDLE");
        vehicle.setDefaultStaffId(8L);
        when(vehicleMapper.selectById(1L)).thenReturn(vehicle);
        BizException e = assertThrows(BizException.class,
                () -> vehicleService.driverChangeStatus(1L, "MAINTENANCE", 7L, 7L));
        assertEquals(10003, e.getCode());
    }

    @Test
    void 作业中车辆司机不可变更() {
        Vehicle vehicle = new Vehicle();
        vehicle.setId(1L);
        vehicle.setStatus("BUSY");
        vehicle.setDefaultStaffId(7L);
        when(vehicleMapper.selectById(1L)).thenReturn(vehicle);
        BizException e = assertThrows(BizException.class,
                () -> vehicleService.driverChangeStatus(1L, "MAINTENANCE", 7L, 7L));
        assertEquals(40004, e.getCode());
    }

    @Test
    void 司机变更本人车辆状态成功() {
        Vehicle vehicle = new Vehicle();
        vehicle.setId(1L);
        vehicle.setStatus("IDLE");
        vehicle.setDefaultStaffId(7L);
        when(vehicleMapper.selectById(1L)).thenReturn(vehicle);
        Vehicle result = vehicleService.driverChangeStatus(1L, "BUSY_THIRD", 7L, 7L);
        assertEquals("BUSY_THIRD", result.getStatus());
    }
}
