package com.crane.modules.demand;

import com.crane.common.auth.AuthContext;
import com.crane.common.exception.BizException;
import com.crane.modules.customer.mapper.CustomerMapper;
import com.crane.modules.demand.dto.DemandConvertRequest;
import com.crane.modules.demand.dto.DemandCreateRequest;
import com.crane.modules.demand.dto.DemandReviewRequest;
import com.crane.modules.demand.entity.DemandOrder;
import com.crane.modules.demand.mapper.DemandOrderMapper;
import com.crane.modules.demand.service.DemandService;
import com.crane.modules.system.mapper.SysUserMapper;
import com.crane.modules.system.service.SystemConfigService;
import com.crane.modules.workorder.dto.WorkOrderCreateCommand;
import com.crane.modules.workorder.entity.WorkOrder;
import com.crane.modules.workorder.service.WorkOrderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 需求单服务单元测试：非 ADMIN 复核越权、CONVERTED 后不可再转工单、驳回后重提链路。
 */
class DemandServiceTest {

    private DemandOrderMapper demandOrderMapper;
    private SystemConfigService systemConfigService;
    private SysUserMapper sysUserMapper;
    private CustomerMapper customerMapper;
    private WorkOrderService workOrderService;
    private DemandService demandService;

    @BeforeEach
    void setUp() {
        demandOrderMapper = mock(DemandOrderMapper.class);
        systemConfigService = mock(SystemConfigService.class);
        sysUserMapper = mock(SysUserMapper.class);
        customerMapper = mock(CustomerMapper.class);
        workOrderService = mock(WorkOrderService.class);
        demandService = new DemandService(demandOrderMapper, systemConfigService,
                sysUserMapper, customerMapper, workOrderService);
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    private void loginAs(String roleCode, Long customerId) {
        AuthContext ctx = new AuthContext();
        ctx.setUserId(9L);
        ctx.setRoleCode(roleCode);
        ctx.setCustomerId(customerId);
        AuthContext.set(ctx);
    }

    private DemandReviewRequest rejectRequest() {
        DemandReviewRequest req = new DemandReviewRequest();
        req.setApprove(false);
        req.setReviewRemark("吨位需求与现场不符");
        return req;
    }

    @Test
    void 非管理员复核应抛出10003() {
        loginAs("CUSTOMER", 5L);
        when(demandOrderMapper.selectById(1L)).thenReturn(new DemandOrder());

        BizException e = assertThrows(BizException.class,
                () -> demandService.review(1L, rejectRequest(), 9L));
        assertEquals(10003, e.getCode());
    }

    @Test
    void 已转工单的需求单再转单应抛出40009() {
        loginAs("ADMIN", null);
        DemandOrder demand = new DemandOrder();
        demand.setId(1L);
        demand.setStatus("CONVERTED");
        when(demandOrderMapper.selectById(1L)).thenReturn(demand);

        DemandConvertRequest req = new DemandConvertRequest();
        req.setStaffId(2L);
        req.setVehicleId(3L);
        req.setPlanStartAt(LocalDateTime.now().plusDays(1));
        req.setCraneTonnage(new BigDecimal("25"));
        req.setCargoWeightTon(new BigDecimal("10"));
        req.setRemark("按需求单转工单");

        BizException e = assertThrows(BizException.class,
                () -> demandService.convert(1L, req, 9L));
        assertEquals(40009, e.getCode());
        verifyNoInteractions(workOrderService);
    }

    @Test
    void 驳回后可重提并保留需求单链路() {
        loginAs("ADMIN", null);
        DemandOrder demand = new DemandOrder();
        demand.setId(1L);
        demand.setDemandNo("XQ202610180001");
        demand.setStatus("PENDING");
        demand.setRejectCount(0);
        when(demandOrderMapper.selectById(1L)).thenReturn(demand);

        // 驳回：状态置 REJECTED、驳回次数 +1
        DemandOrder reviewed = demandService.review(1L, rejectRequest(), 9L);
        assertEquals("REJECTED", reviewed.getStatus());
        assertEquals(1, reviewed.getRejectCount());
        assertEquals("吨位需求与现场不符", reviewed.getReviewRemark());

        // 重提：parentDemandId 指向被驳回的原需求单
        when(demandOrderMapper.selectOne(any())).thenReturn(null);
        when(systemConfigService.getInt(eq("demand.retention_days"), eq(10))).thenReturn(10);
        when(demandOrderMapper.insert(any(DemandOrder.class))).thenAnswer(inv -> {
            inv.getArgument(0, DemandOrder.class).setId(2L);
            return 1;
        });

        DemandCreateRequest createReq = new DemandCreateRequest();
        createReq.setContactName("李四");
        createReq.setContactPhone("13900000002");
        createReq.setAddress("济南市历下区某工地");
        createReq.setParentDemandId(1L);
        createReq.setSource("ADMIN");

        DemandOrder resubmitted = demandService.create(createReq, 9L);

        assertEquals("PENDING", resubmitted.getStatus());
        assertEquals(1L, resubmitted.getParentDemandId());
        assertNotNull(resubmitted.getDemandNo());
        assertTrue(resubmitted.getDemandNo().startsWith("XQ"));
        assertNotNull(resubmitted.getExpiresAt());
    }

    @Test
    void 转工单成功后需求单置为已转化并回写工单号() {
        loginAs("ADMIN", null);
        DemandOrder demand = new DemandOrder();
        demand.setId(1L);
        demand.setDemandNo("XQ202610180001");
        demand.setStatus("REVIEWING");
        demand.setAddress("济南市历下区某工地");
        demand.setDescription("25 吨吊车吊装钢梁");
        when(demandOrderMapper.selectById(1L)).thenReturn(demand);

        WorkOrder order = new WorkOrder();
        order.setId(88L);
        when(workOrderService.create(any(WorkOrderCreateCommand.class), eq(9L))).thenReturn(order);

        DemandConvertRequest req = new DemandConvertRequest();
        req.setStaffId(2L);
        req.setVehicleId(3L);
        req.setPlanStartAt(LocalDateTime.now().plusDays(1));
        req.setCraneTonnage(new BigDecimal("25"));
        req.setCargoWeightTon(new BigDecimal("10"));
        req.setRemark("按需求单转工单");

        DemandOrder converted = demandService.convert(1L, req, 9L);

        assertEquals("CONVERTED", converted.getStatus());
        assertEquals(88L, converted.getConvertedOrderId());
    }

    @Test
    void 客户仅可查看本人需求单且不暴露存在性() {
        loginAs("CUSTOMER", 5L);
        DemandOrder demand = new DemandOrder();
        demand.setId(1L);
        demand.setCustomerId(6L);
        when(demandOrderMapper.selectById(1L)).thenReturn(demand);

        BizException e = assertThrows(BizException.class, () -> demandService.getDetail(1L));
        assertEquals(40001, e.getCode());
    }
}
