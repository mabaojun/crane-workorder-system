package com.crane.modules.ai.service;

import com.crane.modules.ai.client.AiChatClient;
import com.crane.modules.ai.dto.AiOrderParseDtos.ParseResultVO;
import com.crane.modules.customer.entity.Customer;
import com.crane.modules.customer.mapper.CustomerMapper;
import com.crane.modules.demand.entity.DemandOrder;
import com.crane.modules.demand.mapper.DemandOrderMapper;
import com.crane.modules.place.mapper.CommonPlaceMapper;
import com.crane.modules.staff.entity.Staff;
import com.crane.modules.staff.entity.Vehicle;
import com.crane.modules.staff.mapper.StaffMapper;
import com.crane.modules.staff.mapper.VehicleMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 一句话下工单解析：AI 关闭时走本地规则，并完成客户/司机/车辆/需求单匹配。
 */
@ExtendWith(MockitoExtension.class)
class AiOrderParseServiceTest {

    @Mock
    private AiChatClient aiChatClient;
    @Mock
    private CustomerMapper customerMapper;
    @Mock
    private StaffMapper staffMapper;
    @Mock
    private VehicleMapper vehicleMapper;
    @Mock
    private DemandOrderMapper demandOrderMapper;
    @Mock
    private CommonPlaceMapper commonPlaceMapper;

    private AiOrderParseService service;

    @BeforeEach
    void setUp() {
        service = new AiOrderParseService(aiChatClient, new ObjectMapper(), customerMapper,
                staffMapper, vehicleMapper, demandOrderMapper, commonPlaceMapper);
        lenient().when(aiChatClient.loadConfig())
                .thenReturn(new AiChatClient.AiConfig(false, false, "", "", "", 30));

        Customer customer = new Customer();
        customer.setId(1L);
        customer.setName("测试客户有限公司");
        customer.setStatus("ACTIVE");
        customer.setAddress("客户档案地址");
        when(customerMapper.selectList(any())).thenReturn(List.of(customer));

        Staff staff = new Staff();
        staff.setId(11L);
        staff.setName("张超");
        staff.setPhone("13800000001");
        staff.setPosition("DRIVER");
        staff.setStatus("IDLE");
        when(staffMapper.selectList(any())).thenReturn(List.of(staff));

        Vehicle vehicle = new Vehicle();
        vehicle.setId(21L);
        vehicle.setPlateNo("鲁AY8679");
        vehicle.setStatus("IDLE");
        vehicle.setType("CRANE");
        vehicle.setTonnage(new BigDecimal("25"));
        when(vehicleMapper.selectList(any())).thenReturn(List.of(vehicle));

        when(commonPlaceMapper.selectList(any())).thenReturn(List.of());
        when(demandOrderMapper.selectList(any())).thenReturn(List.of());
    }

    @Test
    void 本地规则解析新建工单并匹配档案() {
        ParseResultVO result = service.parse(
                "明天上午八点，给测试客户有限公司在石横镇派25吨吊车，司机张超，车鲁AY8679，货物约5吨，备注带顶笼");

        assertEquals("CREATE_ORDER", result.getIntent());
        assertEquals("RULE", result.getParseMode());
        assertEquals(1L, result.getCustomerId());
        assertEquals(11L, result.getStaffId());
        assertEquals(21L, result.getVehicleId());
        assertEquals("石横镇", result.getAddress());
        assertEquals(new BigDecimal("25"), result.getCraneTonnage());
        assertEquals(new BigDecimal("5"), result.getCargoWeight());
        assertNull(result.getCargoName());
        assertEquals("带顶笼", result.getRemark());
        assertNotNull(result.getPlanStartAt());
        assertEquals(LocalDate.now().plusDays(1), result.getPlanStartAt().toLocalDate());
        assertEquals(8, result.getPlanStartAt().getHour());
        assertTrue(result.getMissingFields().contains("经纬度（需地图选点）"));
    }

    @Test
    void 显式需求单号按需求转工单处理() {
        DemandOrder demand = new DemandOrder();
        demand.setId(31L);
        demand.setDemandNo("XQ202610180018");
        demand.setCustomerId(1L);
        demand.setAddress("石横镇项目现场");
        demand.setExpectedStartAt(LocalDate.now().plusDays(1).atTime(9, 0));
        demand.setCraneTonnage(new BigDecimal("25"));
        demand.setCargoWeight(new BigDecimal("5"));
        demand.setDescription("现场需带顶笼");
        when(demandOrderMapper.selectList(any())).thenReturn(List.of(demand));

        ParseResultVO result = service.parse(
                "把需求单 XQ202610180018 转成工单，司机张超，车鲁AY8679，明天早上八点");

        assertEquals("DEMAND_CONVERT", result.getIntent());
        assertEquals(31L, result.getDemandId());
        assertEquals("XQ202610180018", result.getDemandNo());
        assertEquals("石横镇项目现场", result.getAddress());
        assertEquals(new BigDecimal("25"), result.getCraneTonnage());
        assertEquals(new BigDecimal("5"), result.getCargoWeight());
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("已匹配待转需求单")));
        assertTrue(result.getMissingFields().isEmpty());
    }
}
