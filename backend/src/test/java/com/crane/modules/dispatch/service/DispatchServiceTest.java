package com.crane.modules.dispatch.service;

import com.crane.common.auth.AuthContext;
import com.crane.common.exception.BizException;
import com.crane.modules.dispatch.dto.CheckItem;
import com.crane.modules.dispatch.dto.DispatchRequest;
import com.crane.modules.dispatch.dto.RejectRequest;
import com.crane.modules.dispatch.entity.OrderAcceptLog;
import com.crane.modules.dispatch.mapper.OrderAcceptLogMapper;
import com.crane.modules.staff.entity.Vehicle;
import com.crane.modules.staff.entity.VehicleBinding;
import com.crane.modules.staff.mapper.VehicleBindingMapper;
import com.crane.modules.staff.mapper.VehicleMapper;
import com.crane.modules.staff.mapper.VehicleStatusLogMapper;
import com.crane.modules.workorder.entity.WorkOrder;
import com.crane.modules.workorder.mapper.WorkOrderMapper;
import com.crane.modules.workorder.service.WorkOrderStateMachine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 派单与接单服务单元测试：前置校验错误码映射、权限、接单拒单日志与车辆占用联动。
 */
@ExtendWith(MockitoExtension.class)
class DispatchServiceTest {

    @Mock
    private WorkOrderMapper workOrderMapper;

    @Mock
    private OrderAcceptLogMapper orderAcceptLogMapper;

    @Mock
    private VehicleMapper vehicleMapper;

    @Mock
    private VehicleBindingMapper vehicleBindingMapper;

    @Mock
    private VehicleStatusLogMapper vehicleStatusLogMapper;

    @Mock
    private DispatchCheckService dispatchCheckService;

    @Mock
    private WorkOrderStateMachine stateMachine;

    @InjectMocks
    private DispatchService dispatchService;

    @BeforeEach
    void setUp() {
        // 默认以司机身份（staffId=8）登录
        AuthContext.set(new AuthContext(1L, "DRIVER", "SELF", 8L, null, null));
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    private WorkOrder pendingOrder() {
        WorkOrder order = new WorkOrder();
        order.setId(100L);
        order.setOrderNo("GD202610180001");
        order.setName("钢构吊装");
        order.setStatus("PENDING_ACCEPT");
        order.setStaffId(8L);
        order.setVehicleId(1L);
        return order;
    }

    private DispatchRequest dispatchRequest() {
        DispatchRequest request = new DispatchRequest();
        request.setStaffId(2L);
        request.setVehicleId(2L);
        return request;
    }

    private List<CheckItem> allPass() {
        return List.of(
                CheckItem.pass("VEHICLE_IDLE"),
                CheckItem.pass("STAFF_ON_DUTY"),
                CheckItem.pass("STAFF_NO_ACTIVE_ORDER"),
                CheckItem.pass("PENDING_DISPATCH_LIMIT"),
                CheckItem.pass("TIME_VALID")
        );
    }

    // ==================== 派单 ====================

    @Test
    void dispatchVehicleNotIdleThrows40002() {
        when(workOrderMapper.selectById(100L)).thenReturn(pendingOrder());
        when(dispatchCheckService.precheck(any(), anyLong(), anyLong(), any()))
                .thenReturn(List.of(CheckItem.fail("VEHICLE_IDLE", "车辆非空闲")));

        assertThatThrownBy(() -> dispatchService.dispatch(100L, dispatchRequest(), 99L))
                .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getCode()).isEqualTo(40002));
    }

    @Test
    void dispatchStaffAwayThrows30002() {
        when(workOrderMapper.selectById(100L)).thenReturn(pendingOrder());
        when(dispatchCheckService.precheck(any(), anyLong(), anyLong(), any()))
                .thenReturn(List.of(CheckItem.fail("STAFF_ON_DUTY", "司机离岗")));

        assertThatThrownBy(() -> dispatchService.dispatch(100L, dispatchRequest(), 99L))
                .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getCode()).isEqualTo(30002));
    }

    @Test
    void dispatchPendingLimitThrows30003() {
        when(workOrderMapper.selectById(100L)).thenReturn(pendingOrder());
        when(dispatchCheckService.precheck(any(), anyLong(), anyLong(), any()))
                .thenReturn(List.of(CheckItem.fail("PENDING_DISPATCH_LIMIT", "已达上限")));

        assertThatThrownBy(() -> dispatchService.dispatch(100L, dispatchRequest(), 99L))
                .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getCode()).isEqualTo(30003));
    }

    @Test
    void dispatchMutexConflictThrows30001() {
        when(workOrderMapper.selectById(100L)).thenReturn(pendingOrder());
        when(dispatchCheckService.precheck(any(), anyLong(), anyLong(), any()))
                .thenReturn(List.of(CheckItem.fail("STAFF_NO_ACTIVE_ORDER", "司机已有进行中工单")));

        assertThatThrownBy(() -> dispatchService.dispatch(100L, dispatchRequest(), 99L))
                .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getCode()).isEqualTo(30001));
    }

    @Test
    void dispatchPastTimeWithoutConfirmThrows30004() {
        when(workOrderMapper.selectById(100L)).thenReturn(pendingOrder());
        when(dispatchCheckService.precheck(any(), anyLong(), anyLong(), any()))
                .thenReturn(List.of(CheckItem.fail("TIME_VALID", "需二次确认")));

        assertThatThrownBy(() -> dispatchService.dispatch(100L, dispatchRequest(), 99L))
                .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getCode()).isEqualTo(30004));
    }

    @Test
    void dispatchCancelledOrderThrows40010() {
        WorkOrder order = pendingOrder();
        order.setStatus("CANCELLED");
        when(workOrderMapper.selectById(100L)).thenReturn(order);

        assertThatThrownBy(() -> dispatchService.dispatch(100L, dispatchRequest(), 99L))
                .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getCode()).isEqualTo(40010));
    }

    @Test
    void dispatchInProgressOrderThrows40004() {
        WorkOrder order = pendingOrder();
        order.setStatus("IN_PROGRESS");
        when(workOrderMapper.selectById(100L)).thenReturn(order);

        assertThatThrownBy(() -> dispatchService.dispatch(100L, dispatchRequest(), 99L))
                .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getCode()).isEqualTo(40004));
    }

    @Test
    void dispatchSuccessReassignsVehicleAndBinding() {
        WorkOrder order = pendingOrder();
        when(workOrderMapper.selectById(100L)).thenReturn(order);
        when(dispatchCheckService.precheck(any(), anyLong(), anyLong(), any())).thenReturn(allPass());

        Vehicle oldVehicle = new Vehicle();
        oldVehicle.setId(1L);
        oldVehicle.setStatus("BUSY");
        Vehicle newVehicle = new Vehicle();
        newVehicle.setId(2L);
        newVehicle.setStatus("IDLE");
        when(vehicleMapper.selectById(1L)).thenReturn(oldVehicle);
        when(vehicleMapper.selectById(2L)).thenReturn(newVehicle);

        VehicleBinding activeBinding = new VehicleBinding();
        activeBinding.setId(10L);
        activeBinding.setOrderId(100L);
        activeBinding.setVehicleId(1L);
        activeBinding.setStaffId(8L);
        activeBinding.setIsActive(1);
        when(vehicleBindingMapper.selectOne(any())).thenReturn(activeBinding);

        WorkOrder result = dispatchService.dispatch(100L, dispatchRequest(), 99L);

        // 工单人车已更新，状态保持待接单
        assertThat(result.getStaffId()).isEqualTo(2L);
        assertThat(result.getVehicleId()).isEqualTo(2L);
        assertThat(result.getStatus()).isEqualTo("PENDING_ACCEPT");
        // 旧车释放走条件更新（置空当前工单），新车占用走实体更新
        verify(vehicleMapper, times(1)).update(isNull(), any());
        verify(vehicleMapper, times(1)).updateById(newVehicle);
        assertThat(newVehicle.getStatus()).isEqualTo("BUSY");
        assertThat(newVehicle.getCurrentOrderId()).isEqualTo(100L);
        // 旧车 REASSIGN + 新车 DISPATCH 两条车辆状态日志
        verify(vehicleStatusLogMapper, times(2)).insert(any(com.crane.modules.staff.entity.VehicleStatusLog.class));
        // 旧绑定释放 + 新绑定建立
        verify(vehicleBindingMapper, times(1)).updateById(activeBinding);
        assertThat(activeBinding.getIsActive()).isZero();
        verify(vehicleBindingMapper, times(1)).insert(any(com.crane.modules.staff.entity.VehicleBinding.class));
        // 派单不写接单日志
        verify(orderAcceptLogMapper, never()).insert(any(com.crane.modules.dispatch.entity.OrderAcceptLog.class));
    }

    // ==================== 接单 ====================

    @Test
    void acceptByOtherStaffThrows10003() {
        WorkOrder order = pendingOrder();
        order.setStaffId(9L);
        when(workOrderMapper.selectById(100L)).thenReturn(order);

        assertThatThrownBy(() -> dispatchService.accept(100L, 1L))
                .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getCode()).isEqualTo(10003));
    }

    @Test
    void acceptWrongStatusThrows40004() {
        WorkOrder order = pendingOrder();
        order.setStatus("PENDING_ARRIVE");
        when(workOrderMapper.selectById(100L)).thenReturn(order);

        assertThatThrownBy(() -> dispatchService.accept(100L, 1L))
                .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getCode()).isEqualTo(40004));
    }

    @Test
    void acceptSuccessWritesLogWithRoundOne() {
        WorkOrder order = pendingOrder();
        when(workOrderMapper.selectById(100L)).thenReturn(order);
        when(orderAcceptLogMapper.selectCount(any())).thenReturn(0L);

        WorkOrder result = dispatchService.accept(100L, 1L);

        // 走状态机流转到 PENDING_ARRIVE，接单时间回写
        verify(stateMachine).transition(order, "PENDING_ARRIVE", 1L, "司机接单");
        assertThat(result.getAcceptedAt()).isNotNull();
        ArgumentCaptor<OrderAcceptLog> captor = ArgumentCaptor.forClass(OrderAcceptLog.class);
        verify(orderAcceptLogMapper).insert(captor.capture());
        assertThat(captor.getValue().getAction()).isEqualTo("ACCEPT");
        assertThat(captor.getValue().getStaffId()).isEqualTo(8L);
        assertThat(captor.getValue().getDeliverRound()).isEqualTo(1);
    }

    // ==================== 拒单 ====================

    @Test
    void rejectByOtherStaffThrows10003() {
        WorkOrder order = pendingOrder();
        order.setStaffId(9L);
        when(workOrderMapper.selectById(100L)).thenReturn(order);

        RejectRequest request = new RejectRequest();
        request.setReasonType("VEHICLE_FAULT");

        assertThatThrownBy(() -> dispatchService.reject(100L, request, 1L))
                .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getCode()).isEqualTo(10003));
    }

    @Test
    void rejectInvalidReasonTypeThrows20008() {
        when(workOrderMapper.selectById(100L)).thenReturn(pendingOrder());

        RejectRequest request = new RejectRequest();
        request.setReasonType("BAD_TYPE");

        assertThatThrownBy(() -> dispatchService.reject(100L, request, 1L))
                .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getCode()).isEqualTo(20008));
    }

    @Test
    void rejectOtherTypeRequiresReason() {
        when(workOrderMapper.selectById(100L)).thenReturn(pendingOrder());

        RejectRequest request = new RejectRequest();
        request.setReasonType("OTHER");

        assertThatThrownBy(() -> dispatchService.reject(100L, request, 1L))
                .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getCode()).isEqualTo(20001));
    }

    @Test
    void rejectKeepsPendingAcceptAndWritesLog() {
        WorkOrder order = pendingOrder();
        when(workOrderMapper.selectById(100L)).thenReturn(order);
        when(orderAcceptLogMapper.selectCount(any())).thenReturn(0L);

        RejectRequest request = new RejectRequest();
        request.setReasonType("VEHICLE_FAULT");
        request.setReason("车况异常");

        WorkOrder result = dispatchService.reject(100L, request, 1L);

        // 工单保持待接单、司机保留，等待重新派单
        assertThat(result.getStatus()).isEqualTo("PENDING_ACCEPT");
        assertThat(result.getStaffId()).isEqualTo(8L);
        verify(workOrderMapper, never()).updateById(any(WorkOrder.class));
        ArgumentCaptor<OrderAcceptLog> captor = ArgumentCaptor.forClass(OrderAcceptLog.class);
        verify(orderAcceptLogMapper).insert(captor.capture());
        assertThat(captor.getValue().getAction()).isEqualTo("REJECT");
        assertThat(captor.getValue().getReasonType()).isEqualTo("VEHICLE_FAULT");
        assertThat(captor.getValue().getReason()).isEqualTo("车况异常");
        assertThat(captor.getValue().getDeliverRound()).isEqualTo(1);
        assertThat(captor.getValue().getActedAt()).isNotNull();
    }

    @Test
    void rejectAfterRedispatchWritesNextRound() {
        WorkOrder order = pendingOrder();
        when(workOrderMapper.selectById(100L)).thenReturn(order);
        // 已有一轮拒单记录，本轮次应为 2
        when(orderAcceptLogMapper.selectCount(any())).thenReturn(1L);

        RejectRequest request = new RejectRequest();
        request.setReasonType("TIME_CONFLICT");

        dispatchService.reject(100L, request, 1L);

        ArgumentCaptor<OrderAcceptLog> captor = ArgumentCaptor.forClass(OrderAcceptLog.class);
        verify(orderAcceptLogMapper).insert(captor.capture());
        assertThat(captor.getValue().getDeliverRound()).isEqualTo(2);
    }
}
