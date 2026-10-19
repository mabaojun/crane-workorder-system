package com.crane.modules.exception.service;

import com.crane.common.auth.AuthContext;
import com.crane.common.exception.BizException;
import com.crane.modules.exception.dto.ExceptionReplyRequest;
import com.crane.modules.exception.dto.ExceptionReportRequest;
import com.crane.modules.exception.entity.ExceptionRecord;
import com.crane.modules.exception.mapper.ApprovalRecordMapper;
import com.crane.modules.exception.mapper.ExceptionRecordMapper;
import com.crane.modules.workorder.entity.WorkOrder;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 异常服务单元测试：覆盖状态前置校验、重复待批复阻断、批复指令与状态机联动。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExceptionServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long STAFF_ID = 100L;
    private static final Long ORDER_ID = 10L;
    private static final Long EXCEPTION_ID = 5L;

    @Mock
    private ExceptionRecordMapper exceptionRecordMapper;
    @Mock
    private ApprovalRecordMapper approvalRecordMapper;
    @Mock
    private WorkOrderMapper workOrderMapper;
    @Mock
    private WorkOrderStateMachine workOrderStateMachine;

    private ExceptionService service;

    @BeforeEach
    void setUp() {
        service = new ExceptionService(exceptionRecordMapper, approvalRecordMapper,
                workOrderMapper, workOrderStateMachine, new ObjectMapper());
        AuthContext.set(new AuthContext(USER_ID, "DRIVER", "SELF", STAFF_ID, null, null, List.of()));
        when(exceptionRecordMapper.selectCount(any())).thenReturn(0L);
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    private WorkOrder inProgressOrder() {
        WorkOrder order = new WorkOrder();
        order.setId(ORDER_ID);
        order.setStaffId(STAFF_ID);
        order.setStatus("IN_PROGRESS");
        return order;
    }

    private ExceptionReportRequest reportRequest() {
        ExceptionReportRequest req = new ExceptionReportRequest();
        req.setOrderId(ORDER_ID);
        req.setType("RAIN");
        req.setDescription("现场降雨无法作业");
        return req;
    }

    private ExceptionRecord pendingRecord() {
        ExceptionRecord record = new ExceptionRecord();
        record.setId(EXCEPTION_ID);
        record.setOrderId(ORDER_ID);
        record.setStaffId(STAFF_ID);
        record.setType("RAIN");
        record.setDescription("现场降雨无法作业");
        record.setStatus("PENDING");
        record.setOrderStatusBefore("SUSPENDED");
        record.setReportedAt(LocalDateTime.now());
        return record;
    }

    /** 仅进行中的工单可上报异常，其余状态返回 30007。 */
    @Test
    void reportOnlyAllowedWhenInProgressThrows30007() {
        WorkOrder order = inProgressOrder();
        order.setStatus("PENDING_ARRIVE");
        when(workOrderMapper.selectById(ORDER_ID)).thenReturn(order);

        BizException ex = assertThrows(BizException.class, () -> service.report(reportRequest(), USER_ID));
        assertEquals(30007, ex.getCode());
    }

    /** 工单已存在待批复异常时重复上报返回 30008，避免重复暂停。 */
    @Test
    void reportDuplicatedPendingThrows30008() {
        WorkOrder order = inProgressOrder();
        when(workOrderMapper.selectById(ORDER_ID)).thenReturn(order);
        when(exceptionRecordMapper.selectCount(any())).thenReturn(1L);

        BizException ex = assertThrows(BizException.class, () -> service.report(reportRequest(), USER_ID));
        assertEquals(30008, ex.getCode());
    }

    /** 上报成功：记录置 PENDING 且工单经状态机转 SUSPENDED。 */
    @Test
    void reportSuccessSuspendsOrder() {
        WorkOrder order = inProgressOrder();
        when(workOrderMapper.selectById(ORDER_ID)).thenReturn(order);

        Map<String, Object> data = service.report(reportRequest(), USER_ID);

        verify(workOrderStateMachine).transition(order, "SUSPENDED", STAFF_ID, "异常上报: RAIN");
        assertEquals("SUSPENDED", data.get("workOrderStatus"));
    }

    /** 批复 CONTINUE：工单经状态机恢复 IN_PROGRESS，记录回写已批复与状态快照。 */
    @Test
    void replyContinueResumesOrderViaStateMachine() {
        AuthContext.set(new AuthContext(USER_ID, "ADMIN", "ALL", null, null, null, List.of()));
        ExceptionRecord record = pendingRecord();
        when(exceptionRecordMapper.selectById(EXCEPTION_ID)).thenReturn(record);
        WorkOrder order = inProgressOrder();
        order.setStatus("SUSPENDED");
        when(workOrderMapper.selectById(ORDER_ID)).thenReturn(order);
        ExceptionReplyRequest req = new ExceptionReplyRequest();
        req.setApprove(true);
        req.setInstruction("CONTINUE");
        // 模拟状态机真实副作用：恢复执行后回写工单状态，服务批复后回读应取到 IN_PROGRESS
        doAnswer(inv -> {
            order.setStatus("IN_PROGRESS");
            return null;
        }).when(workOrderStateMachine).transition(order, "IN_PROGRESS", USER_ID, "异常批复: 继续施工");

        Map<String, Object> data = service.reply(EXCEPTION_ID, req, USER_ID);

        verify(workOrderStateMachine).transition(order, "IN_PROGRESS", USER_ID, "异常批复: 继续施工");
        verify(approvalRecordMapper).insert(any(com.crane.modules.exception.entity.ApprovalRecord.class));
        assertEquals("APPROVED", record.getStatus());
        assertEquals("IN_PROGRESS", record.getOrderStatusAfter());
        assertEquals("IN_PROGRESS", data.get("workOrderStatus"));
    }

    /** 批复 TERMINATE：工单经状态机取消并写对客户可见的取消原因。 */
    @Test
    void replyTerminateCancelsOrder() {
        AuthContext.set(new AuthContext(USER_ID, "ADMIN", "ALL", null, null, null, List.of()));
        ExceptionRecord record = pendingRecord();
        when(exceptionRecordMapper.selectById(EXCEPTION_ID)).thenReturn(record);
        WorkOrder order = inProgressOrder();
        order.setStatus("SUSPENDED");
        when(workOrderMapper.selectById(ORDER_ID)).thenReturn(order);
        ExceptionReplyRequest req = new ExceptionReplyRequest();
        req.setApprove(true);
        req.setInstruction("TERMINATE");
        // 模拟状态机真实副作用：取消后回写工单状态，服务批复后回读应取到 CANCELLED
        doAnswer(inv -> {
            order.setStatus("CANCELLED");
            return null;
        }).when(workOrderStateMachine).transition(order, "CANCELLED", USER_ID, "异常批复: 终止施工");

        Map<String, Object> data = service.reply(EXCEPTION_ID, req, USER_ID);

        verify(workOrderStateMachine).transition(order, "CANCELLED", USER_ID, "异常批复: 终止施工");
        assertEquals("异常终止: 现场降雨无法作业", order.getCancelledReason());
        assertEquals("CANCELLED", data.get("workOrderStatus"));
    }

    /** 已批复记录不可重复批复，返回 40004。 */
    @Test
    void replyAlreadyHandledThrows40004() {
        AuthContext.set(new AuthContext(USER_ID, "ADMIN", "ALL", null, null, null, List.of()));
        ExceptionRecord record = pendingRecord();
        record.setStatus("APPROVED");
        when(exceptionRecordMapper.selectById(EXCEPTION_ID)).thenReturn(record);
        ExceptionReplyRequest req = new ExceptionReplyRequest();
        req.setApprove(true);
        req.setInstruction("CONTINUE");

        BizException ex = assertThrows(BizException.class, () -> service.reply(EXCEPTION_ID, req, USER_ID));
        assertEquals(40004, ex.getCode());
    }
}
