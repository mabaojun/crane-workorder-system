package com.crane.modules.dispatch.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.crane.common.auth.AuthContext;
import com.crane.common.exception.BizException;
import com.crane.common.exception.ErrorCode;
import com.crane.common.result.PageResult;
import com.crane.modules.dispatch.dto.CheckItem;
import com.crane.modules.dispatch.dto.DispatchRequest;
import com.crane.modules.dispatch.dto.RejectRequest;
import com.crane.modules.dispatch.entity.OrderAcceptLog;
import com.crane.modules.dispatch.mapper.OrderAcceptLogMapper;
import com.crane.modules.customer.entity.Customer;
import com.crane.modules.customer.mapper.CustomerMapper;
import com.crane.modules.notify.service.NotifyService;
import com.crane.modules.staff.entity.Vehicle;
import com.crane.modules.staff.entity.VehicleBinding;
import com.crane.modules.staff.entity.VehicleStatusLog;
import com.crane.modules.staff.mapper.VehicleBindingMapper;
import com.crane.modules.staff.mapper.VehicleMapper;
import com.crane.modules.staff.mapper.VehicleStatusLogMapper;
import com.crane.modules.workorder.entity.WorkOrder;
import com.crane.modules.workorder.entity.WorkOrderStatusLog;
import com.crane.modules.workorder.mapper.WorkOrderMapper;
import com.crane.modules.workorder.mapper.WorkOrderStatusLogMapper;
import com.crane.modules.workorder.service.WorkOrderStateMachine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 派单与接单服务（API-DISP-002~007）。
 * 派单：仅 PENDING_ACCEPT 工单可派，跑前置校验后赋人车并同步车辆占用与绑定；
 * 接单：仅被派司机本人，走状态机转 PENDING_ARRIVE 并回写接单时间；
 * 拒单：工单保持 PENDING_ACCEPT 等待重新派单，写拒单日志；
 * 重新派单：同派单流程，轮次随拒单次数递增。
 * 约定：派单 / 重新派单本身不写接单日志，接单 / 拒单日志的 deliver_round = 已有拒单次数 + 1。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DispatchService {

    private static final String STATUS_PENDING_ACCEPT = "PENDING_ACCEPT";
    private static final String STATUS_PENDING_ARRIVE = "PENDING_ARRIVE";
    private static final String STATUS_CANCELLED = "CANCELLED";
    private static final String ACTION_ACCEPT = "ACCEPT";
    private static final String ACTION_REJECT = "REJECT";

    private final WorkOrderMapper workOrderMapper;
    private final WorkOrderStatusLogMapper workOrderStatusLogMapper;
    private final OrderAcceptLogMapper orderAcceptLogMapper;
    private final VehicleMapper vehicleMapper;
    private final VehicleBindingMapper vehicleBindingMapper;
    private final VehicleStatusLogMapper vehicleStatusLogMapper;
    private final DispatchCheckService dispatchCheckService;
    private final WorkOrderStateMachine stateMachine;
    private final NotifyService notifyService;
    private final CustomerMapper customerMapper;

    // ==================== 派单 ====================

    /** 派单（API-DISP-002）：校验通过后赋司机与车辆，状态保持 PENDING_ACCEPT 等待司机接单 */
    @Transactional
    public WorkOrder dispatch(Long orderId, DispatchRequest request, Long operatorId) {
        WorkOrder order = mustGetDispatchableOrder(orderId);
        runPrecheckOrThrow(orderId, request);
        assign(order, request, operatorId);
        workOrderMapper.updateById(order);
        log.info("派单成功 orderId={} staffId={} vehicleId={} operatorId={}",
                orderId, order.getStaffId(), order.getVehicleId(), operatorId);
        return order;
    }

    /** 重新派单（API-DISP-005）：流程同派单，用于拒单后改派 */
    @Transactional
    public WorkOrder redispatch(Long orderId, DispatchRequest request, Long operatorId) {
        return dispatch(orderId, request, operatorId);
    }

    private WorkOrder mustGetDispatchableOrder(Long orderId) {
        WorkOrder order = workOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (STATUS_CANCELLED.equals(order.getStatus())) {
            throw new BizException(ErrorCode.ORDER_CANCELLED);
        }
        if (!STATUS_PENDING_ACCEPT.equals(order.getStatus())) {
            throw new BizException(ErrorCode.RESOURCE_STATUS_NOT_ALLOWED, "工单当前状态不允许派单");
        }
        return order;
    }

    /** 前置校验不通过即抛对应错误码：车辆 40002、司机 30002、互斥 30001、上限 30003、时间 30004 */
    private void runPrecheckOrThrow(Long orderId, DispatchRequest request) {
        WorkOrder order = workOrderMapper.selectById(orderId);
        // 计划时间以请求为准，未携带时回退工单原值，保证车辆时段冲突判断基于最终生效时间
        LocalDateTime planStartAt = request.getPlanStartAt() != null
                ? request.getPlanStartAt()
                : (order != null ? order.getExpectedStartAt() : null);
        List<CheckItem> checks = dispatchCheckService.precheck(
                orderId, request.getStaffId(), request.getVehicleId(), planStartAt);
        for (CheckItem item : checks) {
            if (item.isPass()) {
                continue;
            }
            switch (item.getCode()) {
                // 车辆项消息含具体原因（停用/维保/第三方外出/时段冲突），透传给管理端提示
                case DispatchCheckService.CHECK_VEHICLE_IDLE ->
                        throw new BizException(ErrorCode.VEHICLE_NOT_IDLE, item.getMessage());
                case DispatchCheckService.CHECK_STAFF_ON_DUTY ->
                        throw new BizException(ErrorCode.DRIVER_NOT_AVAILABLE);
                case DispatchCheckService.CHECK_STAFF_NO_ACTIVE_ORDER ->
                        throw new BizException(ErrorCode.DRIVER_MUTEX_CONFLICT);
                case DispatchCheckService.CHECK_PENDING_DISPATCH_LIMIT ->
                        throw new BizException(ErrorCode.PENDING_DISPATCH_LIMIT);
                case DispatchCheckService.CHECK_TIME_VALID -> {
                    if (!Boolean.TRUE.equals(request.getConfirmPast())) {
                        throw new BizException(ErrorCode.PAST_START_TIME);
                    }
                }
                default -> throw new BizException(ErrorCode.SYSTEM_ERROR, "未知校验项: " + item.getCode());
            }
        }
    }

    /** 赋人车并联动车辆占用：人车变化时释放旧绑定与旧车、占用新车并新建绑定 */
    private void assign(WorkOrder order, DispatchRequest request, Long operatorId) {
        Long oldVehicleId = order.getVehicleId();
        boolean staffChanged = !request.getStaffId().equals(order.getStaffId());
        boolean vehicleChanged = !request.getVehicleId().equals(oldVehicleId);

        Vehicle newVehicle = null;
        if (vehicleChanged) {
            newVehicle = vehicleMapper.selectById(request.getVehicleId());
            // 前置校验已确保新车存在且空闲，此处防御性校验
            if (newVehicle == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "车辆不存在");
            }
        }

        order.setStaffId(request.getStaffId());
        order.setVehicleId(request.getVehicleId());
        if (request.getPlanStartAt() != null) {
            order.setExpectedStartAt(request.getPlanStartAt());
        }
        if (request.getRemark() != null && !request.getRemark().isBlank()) {
            order.setRemark(request.getRemark());
        }
        order.setUpdatedBy(operatorId);

        if (vehicleChanged) {
            // 旧车释放：状态回 IDLE、当前工单清空，状态日志 REASSIGN
            releaseVehicle(oldVehicleId, order.getId(), operatorId, "REASSIGN");
            // 新车占用：状态 BUSY、当前工单回填，状态日志 DISPATCH
            String before = newVehicle.getStatus();
            newVehicle.setStatus("BUSY");
            newVehicle.setCurrentOrderId(order.getId());
            vehicleMapper.updateById(newVehicle);
            insertVehicleStatusLog(newVehicle.getId(), before, "BUSY", "DISPATCH",
                    order.getId(), order.getStaffId(), operatorId, "派单占用车辆");
            // 绑定一释放一新建
            releaseActiveBinding(order.getId(), "REASSIGN");
            createBinding(order.getId(), order.getStaffId(), order.getVehicleId());
        } else if (staffChanged) {
            // 车辆不变仅换司机：同步生效绑定的司机
            VehicleBinding active = findActiveBinding(order.getId());
            if (active != null) {
                active.setStaffId(order.getStaffId());
                vehicleBindingMapper.updateById(active);
            }
        }
    }

    // ==================== 接单 / 拒单 ====================

    /** 接单（API-DISP-003）：仅被派司机本人，走状态机转 PENDING_ARRIVE 并写接单日志 */
    @Transactional
    public WorkOrder accept(Long orderId, Long operatorId) {
        WorkOrder order = mustGetOwnPendingOrder(orderId);
        order.setAcceptedAt(LocalDateTime.now());
        stateMachine.transition(order, WorkOrderStateMachine.PENDING_ARRIVE, operatorId, "司机接单");
        orderAcceptLogMapper.insert(buildAcceptLog(order, ACTION_ACCEPT, null, null));
        log.info("接单成功 orderId={} staffId={}", orderId, order.getStaffId());
        return order;
    }

    /**
     * 拒单（API-DISP-004）：仅被派司机本人；工单保持 PENDING_ACCEPT 与原司机等待重新派单，写拒单日志。
     */
    @Transactional
    public WorkOrder reject(Long orderId, RejectRequest request, Long operatorId) {
        WorkOrder order = mustGetOwnPendingOrder(orderId);
        String reasonType = validateRejectReason(request);
        // 拒单不改工单状态与派单归属，进入待重新派单队列
        orderAcceptLogMapper.insert(buildAcceptLog(order, ACTION_REJECT, reasonType, request.getReason()));
        log.info("拒单成功 orderId={} staffId={} reasonType={}", orderId, order.getStaffId(), reasonType);
        return order;
    }

    /**
     * 退单（API-DISP-008）：仅"待到场"（已接单 PENDING_ARRIVE）司机本人可发起；
     * 工单回退到 PENDING_ACCEPT 并清空接单时间，写状态日志与拒单日志进入待重新派单队列，
     * 并通过 SSE 实时通知全部管理端（毫秒级，满足 ≤500ms）。
     */
    @Transactional
    public WorkOrder withdraw(Long orderId, RejectRequest request, Long operatorId) {
        WorkOrder order = mustGetOwnArrivedOrder(orderId);
        String reasonType = validateRejectReason(request);
        // 状态机无 PENDING_ARRIVE→PENDING_ACCEPT 回退边，直接置状态并手写状态日志；
        // 接单时间需显式置 null，updateById 忽略 null 字段，必须用 UpdateWrapper
        workOrderMapper.update(null, new UpdateWrapper<WorkOrder>()
                .eq("id", orderId)
                .set("status", STATUS_PENDING_ACCEPT)
                .set("customer_visible_status", "DISPATCHED")
                .set("accepted_at", null)
                .set("updated_by", operatorId));
        workOrderStatusLogMapper.insert(buildStatusLog(orderId, STATUS_PENDING_ARRIVE, STATUS_PENDING_ACCEPT,
                operatorId, "司机退单：" + rejectReasonText(reasonType, request.getReason())));
        // 写拒单日志，进入待重新派单队列，轮次与既有规则一致
        OrderAcceptLog rejectLog = buildAcceptLog(order, ACTION_REJECT, reasonType, request.getReason());
        orderAcceptLogMapper.insert(rejectLog);
        order.setStatus(STATUS_PENDING_ACCEPT);
        order.setCustomerVisibleStatus("DISPATCHED");
        order.setAcceptedAt(null);
        // 实时通知全部管理端
        notifyService.notifyAdminsOrderEvent(order, "WITHDRAW", "司机退单",
                "司机已退单：" + order.getOrderNo() + " " + order.getName()
                        + "（" + rejectReasonText(reasonType, request.getReason()) + "），请尽快重新派单",
                rejectLog.getDeliverRound());
        log.info("退单成功 orderId={} staffId={} reasonType={}", orderId, order.getStaffId(), reasonType);
        return order;
    }

    /** 校验工单可接派：存在、待接单、且当前登录司机为被派司机 */
    private WorkOrder mustGetOwnPendingOrder(Long orderId) {
        WorkOrder order = workOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (!STATUS_PENDING_ACCEPT.equals(order.getStatus())) {
            throw new BizException(ErrorCode.RESOURCE_STATUS_NOT_ALLOWED, "工单当前状态不允许接单或拒单");
        }
        AuthContext ctx = AuthContext.get();
        if (ctx == null || ctx.getStaffId() == null
                || !ctx.getStaffId().equals(order.getStaffId())) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        return order;
    }

    /** 校验工单可退单：存在、待到场（已接单）、且当前登录司机为被派司机 */
    private WorkOrder mustGetOwnArrivedOrder(Long orderId) {
        WorkOrder order = workOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (!STATUS_PENDING_ARRIVE.equals(order.getStatus())) {
            throw new BizException(ErrorCode.RESOURCE_STATUS_NOT_ALLOWED, "工单当前状态不允许退单");
        }
        AuthContext ctx = AuthContext.get();
        if (ctx == null || ctx.getStaffId() == null
                || !ctx.getStaffId().equals(order.getStaffId())) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        return order;
    }

    /** 拒单 / 退单原因校验：类型枚举合法，其他必须填明细，返回规范化原因类型 */
    private String validateRejectReason(RejectRequest request) {
        String reasonType = request.getReasonType();
        if (!"VEHICLE_FAULT".equals(reasonType) && !"TIME_CONFLICT".equals(reasonType)
                && !"ABILITY_MISMATCH".equals(reasonType) && !"OTHER".equals(reasonType)) {
            throw new BizException(ErrorCode.ENUM_INVALID, "拒单原因类型取值非法: " + reasonType);
        }
        if ("OTHER".equals(reasonType) && (request.getReason() == null || request.getReason().isBlank())) {
            throw new BizException(ErrorCode.PARAM_MISSING, "拒单原因为其他时必须填写明细");
        }
        return reasonType;
    }

    /** 拒单 / 退单原因的中文展示文本（用于状态日志与通知内容） */
    private String rejectReasonText(String reasonType, String reason) {
        return switch (reasonType) {
            case "VEHICLE_FAULT" -> "车辆故障";
            case "TIME_CONFLICT" -> "时间冲突";
            case "ABILITY_MISMATCH" -> "能力不匹配";
            default -> "其他：" + (reason == null || reason.isBlank() ? "未填写" : reason);
        };
    }

    /** 工单状态日志（退单等绕过状态机的直接置状态场景使用） */
    private WorkOrderStatusLog buildStatusLog(Long orderId, String fromStatus, String toStatus,
                                              Long operatorId, String remark) {
        WorkOrderStatusLog statusLog = new WorkOrderStatusLog();
        statusLog.setOrderId(orderId);
        statusLog.setFromStatus(fromStatus);
        statusLog.setToStatus(toStatus);
        statusLog.setOperatorId(operatorId);
        statusLog.setRemark(remark);
        statusLog.setCreatedAt(LocalDateTime.now());
        return statusLog;
    }

    /** 接单 / 拒单日志：轮次 = 该工单已有拒单次数 + 1（首次派单轮次为 1） */
    private OrderAcceptLog buildAcceptLog(WorkOrder order, String action, String reasonType, String reason) {
        Long rejectCount = orderAcceptLogMapper.selectCount(new LambdaQueryWrapper<OrderAcceptLog>()
                .eq(OrderAcceptLog::getOrderId, order.getId())
                .eq(OrderAcceptLog::getAction, ACTION_REJECT));
        OrderAcceptLog acceptLog = new OrderAcceptLog();
        acceptLog.setOrderId(order.getId());
        acceptLog.setStaffId(order.getStaffId());
        acceptLog.setAction(action);
        acceptLog.setReasonType(reasonType);
        acceptLog.setReason(reason);
        acceptLog.setActedAt(LocalDateTime.now());
        acceptLog.setDeliverRound(rejectCount == null ? 1 : rejectCount.intValue() + 1);
        return acceptLog;
    }

    // ==================== 队列与记录 ====================

    /** 待重新派单队列（API-DISP-006）：PENDING_ACCEPT 且存在拒单记录的工单，附最近一次拒单信息 */
    public PageResult<Map<String, Object>> pendingRedispatch(int pageNum, int pageSize, String keyword) {
        validatePage(pageNum, pageSize);
        List<OrderAcceptLog> rejects = orderAcceptLogMapper.selectList(new LambdaQueryWrapper<OrderAcceptLog>()
                .eq(OrderAcceptLog::getAction, ACTION_REJECT)
                .orderByDesc(OrderAcceptLog::getActedAt)
                .orderByDesc(OrderAcceptLog::getId));
        if (rejects.isEmpty()) {
            return PageResult.of(0, Collections.emptyList());
        }
        // 保留倒序去重后的工单号顺序，便于按最近拒单时间展示
        Map<Long, OrderAcceptLog> latestRejects = new LinkedHashMap<>();
        for (OrderAcceptLog rejectLog : rejects) {
            latestRejects.putIfAbsent(rejectLog.getOrderId(), rejectLog);
        }
        LambdaQueryWrapper<WorkOrder> wrapper = new LambdaQueryWrapper<WorkOrder>()
                .in(WorkOrder::getId, latestRejects.keySet())
                .eq(WorkOrder::getStatus, STATUS_PENDING_ACCEPT);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(WorkOrder::getOrderNo, keyword).or().like(WorkOrder::getName, keyword));
        }
        // 先计数后追加排序，避免 count 查询携带 ORDER BY
        long total = workOrderMapper.selectCount(wrapper);
        wrapper.orderByDesc(WorkOrder::getId);
        int offset = (pageNum - 1) * pageSize;
        List<WorkOrder> orders = workOrderMapper.selectList(wrapper.last("LIMIT " + offset + "," + pageSize));
        List<Map<String, Object>> list = orders.stream()
                .map(order -> {
                    OrderAcceptLog rejectLog = latestRejects.get(order.getId());
                    Customer customer = order.getCustomerId() == null ? null : customerMapper.selectById(order.getCustomerId());
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("orderId", order.getId());
                    item.put("orderNo", order.getOrderNo());
                    item.put("taskName", order.getName());
                    // 原始工单信息：供管理员重新派单决策
                    item.put("customerId", order.getCustomerId());
                    item.put("customerName", customer == null ? null : customer.getName());
                    item.put("address", order.getAddress());
                    item.put("longitude", order.getLongitude());
                    item.put("latitude", order.getLatitude());
                    item.put("craneTonnage", order.getCraneTonnage());
                    item.put("cargoName", order.getCargoName());
                    item.put("cargoWeight", order.getCargoWeight());
                    item.put("remark", order.getRemark());
                    item.put("rejectReason", rejectLog == null ? null : rejectLog.getReason());
                    item.put("rejectReasonType", rejectLog == null ? null : rejectLog.getReasonType());
                    item.put("rejectedAt", rejectLog == null ? null : rejectLog.getActedAt());
                    item.put("planStartAt", order.getExpectedStartAt());
                    return item;
                })
                .collect(Collectors.toList());
        return PageResult.of(total, list);
    }

    /** 接单拒单记录（API-DISP-007）：按操作时间倒序 */
    public List<OrderAcceptLog> acceptLogs(Long orderId) {
        if (workOrderMapper.selectById(orderId) == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return orderAcceptLogMapper.selectList(new LambdaQueryWrapper<OrderAcceptLog>()
                .eq(OrderAcceptLog::getOrderId, orderId)
                .orderByDesc(OrderAcceptLog::getActedAt)
                .orderByDesc(OrderAcceptLog::getId));
    }

    // ==================== 私有工具 ====================

    private void releaseVehicle(Long vehicleId, Long orderId, Long operatorId, String changeType) {
        if (vehicleId == null) {
            return;
        }
        Vehicle vehicle = vehicleMapper.selectById(vehicleId);
        if (vehicle == null || "IDLE".equals(vehicle.getStatus())) {
            return;
        }
        String before = vehicle.getStatus();
        // 使用字符串列名 UpdateWrapper：避免单测环境初始化 MyBatis-Plus lambda 缓存，且支持显式置 null
        vehicleMapper.update(null, new UpdateWrapper<Vehicle>()
                .eq("id", vehicleId)
                .set("status", "IDLE")
                .set("current_order_id", null));
        insertVehicleStatusLog(vehicleId, before, "IDLE", changeType, orderId, null, operatorId,
                "改派释放车辆");
    }

    private void insertVehicleStatusLog(Long vehicleId, String beforeStatus, String afterStatus,
                                        String changeType, Long orderId, Long staffId, Long operatorId,
                                        String remark) {
        VehicleStatusLog statusLog = new VehicleStatusLog();
        statusLog.setVehicleId(vehicleId);
        statusLog.setBeforeStatus(beforeStatus);
        statusLog.setAfterStatus(afterStatus);
        statusLog.setChangeType(changeType);
        statusLog.setOrderId(orderId);
        statusLog.setStaffId(staffId);
        statusLog.setOperatorId(operatorId);
        statusLog.setOccurAt(LocalDateTime.now());
        statusLog.setRemark(remark);
        vehicleStatusLogMapper.insert(statusLog);
    }

    private void createBinding(Long orderId, Long staffId, Long vehicleId) {
        VehicleBinding binding = new VehicleBinding();
        binding.setOrderId(orderId);
        binding.setStaffId(staffId);
        binding.setVehicleId(vehicleId);
        binding.setBoundAt(LocalDateTime.now());
        binding.setIsActive(1);
        vehicleBindingMapper.insert(binding);
    }

    private VehicleBinding findActiveBinding(Long orderId) {
        return vehicleBindingMapper.selectOne(new LambdaQueryWrapper<VehicleBinding>()
                .eq(VehicleBinding::getOrderId, orderId)
                .eq(VehicleBinding::getIsActive, 1)
                .last("LIMIT 1"));
    }

    private void releaseActiveBinding(Long orderId, String releaseReason) {
        VehicleBinding active = findActiveBinding(orderId);
        if (active == null) {
            return;
        }
        active.setIsActive(0);
        active.setReleasedAt(LocalDateTime.now());
        active.setReleaseReason(releaseReason);
        vehicleBindingMapper.updateById(active);
    }

    private void validatePage(int pageNum, int pageSize) {
        if (pageNum < 1 || pageSize < 1 || pageSize > 100) {
            throw new BizException(ErrorCode.PAGE_INVALID);
        }
    }
}
