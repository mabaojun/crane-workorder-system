package com.crane.modules.workorder.service;

import com.crane.common.exception.BizException;
import com.crane.modules.workorder.entity.WorkOrder;
import com.crane.modules.workorder.entity.WorkOrderStatusLog;
import com.crane.modules.workorder.mapper.WorkOrderMapper;
import com.crane.modules.workorder.mapper.WorkOrderStatusLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 工单状态机单元测试：合法流转、非法流转、互斥校验、时间戳与客户可见状态回写。
 */
@ExtendWith(MockitoExtension.class)
class WorkOrderStateMachineTest {

    @Mock
    private WorkOrderMapper workOrderMapper;

    @Mock
    private WorkOrderStatusLogMapper statusLogMapper;

    @InjectMocks
    private WorkOrderStateMachine stateMachine;

    private WorkOrder order;

    @BeforeEach
    void setUp() {
        order = new WorkOrder();
        order.setId(100L);
        order.setStaffId(5L);
        order.setStatus("PENDING_ACCEPT");
    }

    @Test
    void legalTransitionPendingAcceptToPendingArrive() {
        stateMachine.transition(order, "PENDING_ARRIVE", 1L, "司机接单");

        assertThat(order.getStatus()).isEqualTo("PENDING_ARRIVE");
        assertThat(order.getCustomerVisibleStatus()).isEqualTo("DISPATCHED");
        verify(workOrderMapper).updateById(order);
        ArgumentCaptor<WorkOrderStatusLog> captor = ArgumentCaptor.forClass(WorkOrderStatusLog.class);
        verify(statusLogMapper).insert(captor.capture());
        assertThat(captor.getValue().getFromStatus()).isEqualTo("PENDING_ACCEPT");
        assertThat(captor.getValue().getToStatus()).isEqualTo("PENDING_ARRIVE");
        assertThat(captor.getValue().getOperatorId()).isEqualTo(1L);
        assertThat(captor.getValue().getOrderId()).isEqualTo(100L);
    }

    @Test
    void startOrderSetsStartedAtAndVisibleStatus() {
        order.setStatus("PENDING_ARRIVE");
        when(workOrderMapper.selectCount(any())).thenReturn(0L);

        stateMachine.transition(order, "IN_PROGRESS", 1L, "签到");

        assertThat(order.getStatus()).isEqualTo("IN_PROGRESS");
        assertThat(order.getCustomerVisibleStatus()).isEqualTo("STARTED");
        assertThat(order.getStartedAt()).isNotNull();
    }

    @Test
    void resumeKeepsOriginalStartedAt() {
        order.setStatus("SUSPENDED");
        LocalDateTime originalStart = LocalDateTime.of(2026, 10, 1, 8, 0);
        order.setStartedAt(originalStart);
        when(workOrderMapper.selectCount(any())).thenReturn(0L);

        stateMachine.transition(order, "IN_PROGRESS", 1L, "恢复执行");

        assertThat(order.getStatus()).isEqualTo("IN_PROGRESS");
        assertThat(order.getStartedAt()).isEqualTo(originalStart);
        assertThat(order.getCustomerVisibleStatus()).isEqualTo("STARTED");
    }

    @Test
    void suspendSetsSuspendedAtAndVisibleStatus() {
        order.setStatus("IN_PROGRESS");

        stateMachine.transition(order, "SUSPENDED", 1L, "下雨暂停");

        assertThat(order.getStatus()).isEqualTo("SUSPENDED");
        assertThat(order.getCustomerVisibleStatus()).isEqualTo("PAUSED");
        assertThat(order.getSuspendedAt()).isNotNull();
    }

    @Test
    void completeSetsCompletedAtAndVisibleStatus() {
        order.setStatus("IN_PROGRESS");

        stateMachine.transition(order, "COMPLETED", 1L, "任务结束");

        assertThat(order.getStatus()).isEqualTo("COMPLETED");
        assertThat(order.getCustomerVisibleStatus()).isEqualTo("FINISHED");
        assertThat(order.getCompletedAt()).isNotNull();
    }

    @Test
    void illegalTransitionThrows30007() {
        // 待接单不允许直接进入进行中
        assertThatThrownBy(() -> stateMachine.transition(order, "IN_PROGRESS", 1L, null))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getCode()).isEqualTo(30007))
                .hasMessageContaining("PENDING_ACCEPT")
                .hasMessageContaining("IN_PROGRESS");
        verify(workOrderMapper, never()).updateById(any(WorkOrder.class));
    }

    @Test
    void mutexConflictThrows30001() {
        order.setStatus("PENDING_ARRIVE");
        when(workOrderMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> stateMachine.transition(order, "IN_PROGRESS", 1L, null))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getCode()).isEqualTo(30001));
        verify(workOrderMapper, never()).updateById(any(WorkOrder.class));
    }

    @Test
    void mutexNotTriggeredForSuspendTarget() {
        order.setStatus("IN_PROGRESS");

        // SUSPENDED 目标不做互斥查询
        stateMachine.transition(order, "SUSPENDED", 1L, null);
        verify(workOrderMapper, never()).selectCount(any());
    }

    @Test
    void unknownTargetStatusThrows20008() {
        assertThatThrownBy(() -> stateMachine.transition(order, "FOO", 1L, null))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getCode()).isEqualTo(20008));
    }

    @Test
    void inProgressToCancelledAllowedForTerminate() {
        // API-WO-005 支持取消、异常批复终止（PRD 4.17）依赖该边
        order.setStatus("IN_PROGRESS");

        stateMachine.transition(order, "CANCELLED", 1L, "异常批复: 终止施工");

        assertThat(order.getStatus()).isEqualTo("CANCELLED");
        assertThat(order.getCustomerVisibleStatus()).isEqualTo("CANCELLED");
        assertThat(order.getCancelledAt()).isNotNull();
    }

    @Test
    void cancelFromNonTerminalWritesReasonAndLog() {
        order.setStatus("IN_PROGRESS");

        stateMachine.cancel(order, 1L, "客户取消");

        assertThat(order.getStatus()).isEqualTo("CANCELLED");
        assertThat(order.getCustomerVisibleStatus()).isEqualTo("CANCELLED");
        assertThat(order.getCancelledAt()).isNotNull();
        assertThat(order.getCancelledReason()).isEqualTo("客户取消");
        ArgumentCaptor<WorkOrderStatusLog> captor = ArgumentCaptor.forClass(WorkOrderStatusLog.class);
        verify(statusLogMapper).insert(captor.capture());
        assertThat(captor.getValue().getFromStatus()).isEqualTo("IN_PROGRESS");
        assertThat(captor.getValue().getToStatus()).isEqualTo("CANCELLED");
    }

    @Test
    void cancelFromTerminalThrows30007() {
        order.setStatus("COMPLETED");

        assertThatThrownBy(() -> stateMachine.cancel(order, 1L, "重复取消"))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getCode()).isEqualTo(30007));
    }
}
