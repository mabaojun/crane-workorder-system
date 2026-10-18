package com.crane.modules.workorder.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crane.common.exception.BizException;
import com.crane.common.exception.ErrorCode;
import com.crane.modules.workorder.entity.WorkOrder;
import com.crane.modules.workorder.entity.WorkOrderStatusLog;
import com.crane.modules.workorder.mapper.WorkOrderMapper;
import com.crane.modules.workorder.mapper.WorkOrderStatusLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

/**
 * 工单状态机（六态，字面量与 01 §6.3 一致）：
 * PENDING_ACCEPT → PENDING_ARRIVE | CANCELLED
 * PENDING_ARRIVE → IN_PROGRESS | CANCELLED
 * IN_PROGRESS → SUSPENDED | COMPLETED | CANCELLED（取消需二次确认，见 API-WO-005、PRD 4.17，异常批复终止亦走此边）
 * SUSPENDED → IN_PROGRESS | CANCELLED
 * COMPLETED / CANCELLED 为终态。
 * 互斥口径：司机占用 = 存在 status=IN_PROGRESS 的未删除工单（SUSPENDED 不占用）。
 * 取消亦可经 cancel() 直达：任意非终态可取消，取消后司机占用与车辆占用随之解除。
 */
@Service
@RequiredArgsConstructor
public class WorkOrderStateMachine {

    public static final String PENDING_ACCEPT = "PENDING_ACCEPT";
    public static final String PENDING_ARRIVE = "PENDING_ARRIVE";
    public static final String IN_PROGRESS = "IN_PROGRESS";
    public static final String SUSPENDED = "SUSPENDED";
    public static final String COMPLETED = "COMPLETED";
    public static final String CANCELLED = "CANCELLED";

    /** 合法流转表：目标状态 → 允许的来源状态集合 */
    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            PENDING_ACCEPT, Set.of(),
            PENDING_ARRIVE, Set.of(PENDING_ACCEPT),
            IN_PROGRESS, Set.of(PENDING_ARRIVE, SUSPENDED),
            SUSPENDED, Set.of(IN_PROGRESS),
            COMPLETED, Set.of(IN_PROGRESS),
            CANCELLED, Set.of(PENDING_ACCEPT, PENDING_ARRIVE, IN_PROGRESS, SUSPENDED)
    );

    /** 内部状态 → 客户可见状态映射 */
    private static final Map<String, String> VISIBLE_STATUS_MAPPING = Map.of(
            PENDING_ARRIVE, "DISPATCHED",
            IN_PROGRESS, "STARTED",
            SUSPENDED, "PAUSED",
            COMPLETED, "FINISHED",
            CANCELLED, "CANCELLED"
    );

    private final WorkOrderMapper workOrderMapper;
    private final WorkOrderStatusLogMapper statusLogMapper;

    /**
     * 按状态机流转：校验合法性、互斥占用，回写状态 / 客户可见状态 / 时间戳，并同事务写状态日志。
     * 非法流转抛 30007；恢复执行命中互斥抛 30001。
     */
    @Transactional
    public void transition(WorkOrder order, String targetStatus, Long operatorId, String remark) {
        String from = order.getStatus();
        Set<String> allowedFrom = TRANSITIONS.get(targetStatus);
        if (allowedFrom == null) {
            throw new BizException(ErrorCode.ENUM_INVALID, "非法工单目标状态: " + targetStatus);
        }
        if (from == null || !allowedFrom.contains(from)) {
            throw new BizException(ErrorCode.ORDER_STATUS_NOT_ALLOWED,
                    "非法状态流转: " + from + " → " + targetStatus);
        }
        checkDriverMutex(order, targetStatus);

        LocalDateTime now = LocalDateTime.now();
        order.setStatus(targetStatus);
        order.setCustomerVisibleStatus(VISIBLE_STATUS_MAPPING.get(targetStatus));
        applyTimestamp(order, targetStatus, now);
        workOrderMapper.updateById(order);
        writeStatusLog(order.getId(), from, targetStatus, operatorId, remark, now);
    }

    /**
     * 取消：仅限非终态（管理员动作），写取消原因与时间戳并落状态日志。
     * 车辆释放由调用方在同一事务内完成。
     */
    @Transactional
    public void cancel(WorkOrder order, Long operatorId, String reason) {
        String from = order.getStatus();
        if (COMPLETED.equals(from) || CANCELLED.equals(from)) {
            throw new BizException(ErrorCode.ORDER_STATUS_NOT_ALLOWED,
                    "非法状态流转: " + from + " → " + CANCELLED);
        }
        LocalDateTime now = LocalDateTime.now();
        order.setStatus(CANCELLED);
        order.setCustomerVisibleStatus(VISIBLE_STATUS_MAPPING.get(CANCELLED));
        order.setCancelledAt(now);
        if (reason != null && !reason.isBlank()) {
            order.setCancelledReason(reason);
        }
        workOrderMapper.updateById(order);
        writeStatusLog(order.getId(), from, CANCELLED, operatorId, reason, now);
    }

    /** 判断状态是否为终态 */
    public static boolean isTerminal(String status) {
        return COMPLETED.equals(status) || CANCELLED.equals(status);
    }

    /** 恢复执行（目标 IN_PROGRESS）时校验司机互斥：同司机存在其他进行中工单则阻断 */
    private void checkDriverMutex(WorkOrder order, String targetStatus) {
        if (!IN_PROGRESS.equals(targetStatus) || order.getStaffId() == null) {
            return;
        }
        Long count = workOrderMapper.selectCount(new LambdaQueryWrapper<WorkOrder>()
                .eq(WorkOrder::getStaffId, order.getStaffId())
                .eq(WorkOrder::getStatus, IN_PROGRESS)
                .ne(WorkOrder::getId, order.getId()));
        if (count != null && count > 0) {
            throw new BizException(ErrorCode.DRIVER_MUTEX_CONFLICT);
        }
    }

    /** 状态对应时间戳回写；恢复执行保留首次启动时间，避免拉长工时统计口径 */
    private void applyTimestamp(WorkOrder order, String targetStatus, LocalDateTime now) {
        switch (targetStatus) {
            case IN_PROGRESS -> {
                if (order.getStartedAt() == null) {
                    order.setStartedAt(now);
                }
            }
            case SUSPENDED -> order.setSuspendedAt(now);
            case COMPLETED -> order.setCompletedAt(now);
            case CANCELLED -> order.setCancelledAt(now);
            default -> {
                // PENDING_ARRIVE 无时间戳回写
            }
        }
    }

    private void writeStatusLog(Long orderId, String fromStatus, String toStatus,
                                Long operatorId, String remark, LocalDateTime now) {
        WorkOrderStatusLog logEntry = new WorkOrderStatusLog();
        logEntry.setOrderId(orderId);
        logEntry.setFromStatus(fromStatus);
        logEntry.setToStatus(toStatus);
        logEntry.setOperatorId(operatorId);
        logEntry.setRemark(remark);
        logEntry.setCreatedAt(now);
        statusLogMapper.insert(logEntry);
    }
}
