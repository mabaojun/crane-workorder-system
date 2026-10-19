package com.crane.modules.workorder.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.crane.common.auth.AuthContext;
import com.crane.common.exception.BizException;
import com.crane.common.exception.ErrorCode;
import com.crane.common.result.PageResult;
import com.crane.modules.customer.entity.Customer;
import com.crane.modules.customer.mapper.CustomerMapper;
import com.crane.modules.dispatch.dto.CheckItem;
import com.crane.modules.dispatch.service.DispatchCheckService;
import com.crane.modules.exception.service.ExceptionService;
import com.crane.modules.notify.service.NotifyService;
import com.crane.modules.place.service.CommonPlaceService;
import com.crane.modules.staff.entity.Staff;
import com.crane.modules.staff.entity.Vehicle;
import com.crane.modules.staff.entity.VehicleBinding;
import com.crane.modules.staff.entity.VehicleStatusLog;
import com.crane.modules.staff.mapper.StaffMapper;
import com.crane.modules.staff.mapper.VehicleBindingMapper;
import com.crane.modules.staff.mapper.VehicleMapper;
import com.crane.modules.staff.mapper.VehicleStatusLogMapper;
import com.crane.modules.system.service.SystemConfigService;
import com.crane.modules.workorder.dto.WorkOrderCreateCommand;
import com.crane.modules.workorder.dto.WorkOrderCreateRequest;
import com.crane.modules.workorder.dto.WorkOrderDetailResponse;
import com.crane.modules.workorder.dto.WorkOrderStatusChangeRequest;
import com.crane.modules.workorder.dto.WorkOrderUpdateRequest;
import com.crane.modules.workorder.dto.ReviewRequest;
import com.crane.modules.workorder.entity.CustomerReview;
import com.crane.modules.workorder.entity.WorkHourStat;
import com.crane.modules.workorder.entity.WorkOrder;
import com.crane.modules.workorder.entity.WorkOrderStatusLog;
import com.crane.modules.workorder.mapper.CustomerReviewMapper;
import com.crane.modules.workorder.mapper.WorkHourStatMapper;
import com.crane.modules.workorder.mapper.WorkOrderMapper;
import com.crane.modules.workorder.mapper.WorkOrderStatusLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 工单服务：建单（API 直建与需求单转入共用）、详情、分页、修改、状态变更、结单、评价、状态记录。
 * 规则要点：建单即做派单前置校验；建单占用车辆（BUSY + 绑定）；取消 / 结单释放车辆并落绑定释放；
 * 结单联动写工时统计；客户可见状态随内部状态同步映射。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkOrderService {

    private static final String STATUS_PENDING_ACCEPT = "PENDING_ACCEPT";
    private static final String VEHICLE_IDLE = "IDLE";
    private static final String VEHICLE_BUSY = "BUSY";
    private static final String STAFF_AWAY = "AWAY";
    private static final String STAFF_RESIGNED = "RESIGNED";

    /** 客户可见状态中文标签（02 §3.3.6） */
    private static final Map<String, String> VISIBLE_STATUS_LABELS = Map.of(
            "DISPATCHED", "已派单",
            "ACCEPTED", "已接单",
            "STARTED", "任务开始执行",
            "PAUSED", "任务暂停",
            "FINISHED", "任务已完成",
            "CANCELLED", "任务已取消"
    );

    private final WorkOrderMapper workOrderMapper;
    private final WorkOrderStatusLogMapper statusLogMapper;
    private final CustomerReviewMapper customerReviewMapper;
    private final WorkHourStatMapper workHourStatMapper;
    private final StaffMapper staffMapper;
    private final VehicleMapper vehicleMapper;
    private final VehicleBindingMapper vehicleBindingMapper;
    private final VehicleStatusLogMapper vehicleStatusLogMapper;
    private final CustomerMapper customerMapper;
    private final SystemConfigService systemConfigService;
    private final WorkOrderStateMachine stateMachine;
    private final NotifyService notifyService;
    private final ExceptionService exceptionService;
    private final CommonPlaceService commonPlaceService;
    private final DispatchCheckService dispatchCheckService;

    // ==================== 建单 ====================

    /** API-WO-001 建单入口：请求体转命令后复用同一实现 */
    public WorkOrder create(WorkOrderCreateRequest request, Long operatorId) {
        WorkOrderCreateCommand command = new WorkOrderCreateCommand();
        command.setTaskName(request.getTaskName());
        command.setSource(request.getSource());
        command.setDemandOrderId(request.getDemandOrderId());
        command.setCustomerId(request.getCustomerId());
        command.setContactId(request.getContactId());
        command.setStaffId(request.getStaffId());
        command.setVehicleId(request.getVehicleId());
        command.setAddress(request.getAddress());
        command.setLongitude(request.getLongitude());
        command.setLatitude(request.getLatitude());
        command.setCargoName(request.getCargoName());
        command.setCargoQuantity(request.getCargoQty() == null ? null : BigDecimal.valueOf(request.getCargoQty()));
        command.setCargoWeightTon(request.getCargoWeightTon());
        command.setCraneTonnage(request.getCraneTonnage());
        command.setPlanStartAt(request.getPlanStartAt());
        command.setRemark(request.getRemark());
        command.setRequirement(request.getRequirement());
        return doCreate(command, operatorId);
    }

    /** 供需求单域转工单调用的建单入口 */
    @Transactional
    public WorkOrder create(WorkOrderCreateCommand command, Long operatorId) {
        return doCreate(command, operatorId);
    }

    private WorkOrder doCreate(WorkOrderCreateCommand command, Long operatorId) {
        validateSource(command);
        if (command.getCustomerId() == null) {
            throw new BizException(ErrorCode.PARAM_MISSING, "customerId 必填");
        }
        if (command.getPlanStartAt() == null) {
            throw new BizException(ErrorCode.PARAM_MISSING, "planStartAt 必填");
        }
        // 客户必须存在
        Customer customer = customerMapper.selectById(command.getCustomerId());
        if (customer == null) {
            throw new BizException(ErrorCode.CUSTOMER_NOT_FOUND);
        }
        // 司机必须存在且非离岗 / 离职
        Staff staff = staffMapper.selectById(command.getStaffId());
        if (staff == null) {
            throw new BizException(ErrorCode.STAFF_NOT_FOUND);
        }
        if (STAFF_AWAY.equals(staff.getStatus()) || STAFF_RESIGNED.equals(staff.getStatus())) {
            throw new BizException(ErrorCode.DRIVER_NOT_AVAILABLE);
        }
        // 司机互斥：存在进行中工单即阻断建单（命中时由冲突域触发提醒）
        checkStaffNoActiveOrder(command.getStaffId(), null);
        // 车辆必须存在且可用：空闲直接通过；作业中按 6 小时施工时段判断冲突（当前忙不代表派单时刻忙）；
        // 维保 / 第三方外出 / 停用物理不可用一律拒绝。校验规则与派单预检共用 DispatchCheckService。
        Vehicle vehicle = vehicleMapper.selectById(command.getVehicleId());
        if (vehicle == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "车辆不存在");
        }
        CheckItem vehicleCheck = dispatchCheckService.checkVehicleAvailable(
                null, command.getVehicleId(), command.getPlanStartAt());
        if (!vehicleCheck.isPass()) {
            throw new BizException(ErrorCode.VEHICLE_NOT_IDLE, vehicleCheck.getMessage());
        }
        // 预计开始时间不得早于当前时间
        if (command.getPlanStartAt().isBefore(LocalDateTime.now())) {
            throw new BizException(ErrorCode.PAST_START_TIME);
        }

        WorkOrder order = new WorkOrder();
        // 任务名称选填：不填时按「客户名·吊车吨位·司机姓名」自动生成
        if (command.getTaskName() == null || command.getTaskName().isBlank()) {
            order.setName(autoTaskName(customer, staff, command.getCraneTonnage()));
        } else {
            order.setName(command.getTaskName().trim());
        }
        order.setSource(command.getSource());
        order.setDemandId(command.getDemandOrderId());
        order.setCustomerId(command.getCustomerId());
        order.setContactId(command.getContactId());
        order.setStaffId(command.getStaffId());
        order.setVehicleId(command.getVehicleId());
        order.setAddress(command.getAddress());
        order.setLongitude(command.getLongitude());
        order.setLatitude(command.getLatitude());
        order.setCargoName(command.getCargoName());
        order.setCargoQuantity(command.getCargoQuantity());
        order.setCargoWeight(command.getCargoWeightTon());
        order.setCraneTonnage(command.getCraneTonnage());
        order.setExpectedStartAt(command.getPlanStartAt());
        order.setRemark(command.getRemark());
        order.setRequirement(command.getRequirement());
        order.setStatus(STATUS_PENDING_ACCEPT);
        order.setCustomerVisibleStatus("DISPATCHED");
        int deadlineMinutes = systemConfigService.getInt("order.accept_deadline_minutes", 30);
        order.setAcceptDeadlineAt(LocalDateTime.now().plusMinutes(deadlineMinutes));
        order.setIsPinnedAlarm(0);
        order.setCreatedBy(operatorId);

        insertWithOrderNoRetry(order);

        // 建单即占用车辆：状态 BUSY + 当前工单回填 + 状态日志 + 三方绑定
        occupyVehicle(vehicle, order.getId(), command.getStaffId(), operatorId, "DISPATCH");
        createBinding(order.getId(), command.getStaffId(), command.getVehicleId());
        // 施工地点自动沉淀为常用地点（按名称去重回填坐标）：建单/转工单用过的地址均可复用
        commonPlaceService.upsertFromSite(order.getAddress(), order.getLongitude(), order.getLatitude());
        log.info("工单创建成功 orderId={} orderNo={} staffId={} vehicleId={}",
                order.getId(), order.getOrderNo(), order.getStaffId(), order.getVehicleId());
        return order;
    }

    /**
     * 任务名称自动生成：客户名 + 吊车吨位 + 司机姓名（如「济南钢铁·25吨·张三」）。
     */
    private String autoTaskName(Customer customer, Staff staff, java.math.BigDecimal craneTonnage) {
        StringBuilder sb = new StringBuilder(customer.getName() == null ? "客户" : customer.getName());
        if (craneTonnage != null) {
            sb.append('·').append(craneTonnage.stripTrailingZeros().toPlainString()).append("吨");
        }
        if (staff != null && staff.getName() != null) {
            sb.append('·').append(staff.getName());
        }
        String name = sb.toString();
        return name.length() > 100 ? name.substring(0, 100) : name;
    }

    // ==================== 暂停恢复 ====================

    /**
     * 司机对暂停工单发起「申请继续」：写状态日志留痕并通过 SSE 实时通知管理端。
     * round 按历史申请次数递增，保证同轮去重不吞掉后续申请。
     */
    public void resumeRequest(Long orderId, Long operatorId) {
        AuthContext ctx = AuthContext.get();
        if (ctx == null || !ctx.isDriver()) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅司机可申请继续施工");
        }
        WorkOrder order = workOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "工单不存在");
        }
        if (!WorkOrderStateMachine.SUSPENDED.equals(order.getStatus())) {
            throw new BizException(ErrorCode.ORDER_STATUS_NOT_ALLOWED, "仅暂停中的工单可申请继续");
        }
        if (!Objects.equals(order.getStaffId(), ctx.getStaffId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅可操作本人工单");
        }
        long round = statusLogMapper.selectCount(new LambdaQueryWrapper<WorkOrderStatusLog>()
                .eq(WorkOrderStatusLog::getOrderId, orderId)
                .eq(WorkOrderStatusLog::getToStatus, WorkOrderStateMachine.SUSPENDED)
                .like(WorkOrderStatusLog::getRemark, "司机申请继续")) + 1;
        WorkOrderStatusLog entry = new WorkOrderStatusLog();
        entry.setOrderId(orderId);
        entry.setFromStatus(WorkOrderStateMachine.SUSPENDED);
        entry.setToStatus(WorkOrderStateMachine.SUSPENDED);
        entry.setOperatorId(ctx.getStaffId());
        entry.setRemark("司机申请继续施工");
        entry.setCreatedAt(LocalDateTime.now());
        statusLogMapper.insert(entry);
        notifyService.notifyAdminsOrderEvent(order, "RESUME_REQUEST", "司机申请继续施工",
                "工单 " + order.getOrderNo() + " 司机申请继续施工，请及时处置", round);
        log.info("司机申请继续施工 orderId={} staffId={}", orderId, ctx.getStaffId());
    }

    /**
     * 管理员批准暂停工单继续施工：SUSPENDED → IN_PROGRESS，
     * 同事务自动按「继续施工」关闭未批复异常（不触发二次流转），并实时通知管理端。
     */
    @Transactional
    public Map<String, Object> resume(Long orderId, Long operatorId) {
        WorkOrder order = workOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "工单不存在");
        }
        if (!WorkOrderStateMachine.SUSPENDED.equals(order.getStatus())) {
            throw new BizException(ErrorCode.ORDER_STATUS_NOT_ALLOWED, "仅暂停中的工单可批准继续");
        }
        exceptionService.autoClosePendingForOrder(orderId, operatorId,
                "管理员批准继续施工，异常自动关闭", WorkOrderStateMachine.IN_PROGRESS);
        stateMachine.transition(order, WorkOrderStateMachine.IN_PROGRESS, operatorId, "管理员批准继续施工");
        notifyService.notifyAdminsOrderEvent(order, "RESUME", "工单已恢复施工",
                "工单 " + order.getOrderNo() + " 已批准继续施工", System.currentTimeMillis());
        return Map.of(
                "orderId", order.getId(),
                "status", order.getStatus(),
                "customerVisibleStatus", order.getCustomerVisibleStatus() == null ? "" : order.getCustomerVisibleStatus()
        );
    }

    /** 来源枚举校验：DEMAND、DIRECT；来源为需求单时必须携带需求单编号 */
    private void validateSource(WorkOrderCreateCommand command) {
        if (!"DEMAND".equals(command.getSource()) && !"DIRECT".equals(command.getSource())) {
            throw new BizException(ErrorCode.ENUM_INVALID, "工单来源取值非法: " + command.getSource());
        }
        if ("DEMAND".equals(command.getSource()) && command.getDemandOrderId() == null) {
            throw new BizException(ErrorCode.PARAM_MISSING, "source=DEMAND 时 demandOrderId 必填");
        }
    }

    /** 工单号规则：GD + yyyyMMdd + 4 位当日序号；冲突时以时间戳兜底并重试一次插入 */
    private void insertWithOrderNoRetry(WorkOrder order) {
        order.setOrderNo(generateOrderNo());
        try {
            workOrderMapper.insert(order);
        } catch (DuplicateKeyException e) {
            log.warn("工单号冲突，使用时间戳兜底重试一次: {}", order.getOrderNo());
            order.setId(null);
            String datePart = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
            order.setOrderNo("GD" + datePart + String.format("%04d", System.currentTimeMillis() % 10000));
            workOrderMapper.insert(order);
        }
    }

    /** 查当日最大序号 +1；无记录从 0001 起；序号用尽或已存在时以时间戳兜底 */
    private String generateOrderNo() {
        String prefix = "GD" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        WorkOrder max = workOrderMapper.selectOne(new LambdaQueryWrapper<WorkOrder>()
                .likeRight(WorkOrder::getOrderNo, prefix)
                .orderByDesc(WorkOrder::getOrderNo)
                .last("LIMIT 1"));
        int next = 1;
        if (max != null && max.getOrderNo() != null && max.getOrderNo().length() > prefix.length()) {
            try {
                next = Integer.parseInt(max.getOrderNo().substring(prefix.length())) + 1;
            } catch (NumberFormatException ignore) {
                next = 1;
            }
        }
        if (next > 9999) {
            return prefix + String.format("%04d", System.currentTimeMillis() % 10000);
        }
        String candidate = prefix + String.format("%04d", next);
        Long exists = workOrderMapper.selectCount(new LambdaQueryWrapper<WorkOrder>()
                .eq(WorkOrder::getOrderNo, candidate));
        if (exists != null && exists > 0) {
            return prefix + String.format("%04d", System.currentTimeMillis() % 10000);
        }
        return candidate;
    }

    // ==================== 详情与列表 ====================

    public WorkOrderDetailResponse detail(Long id) {
        WorkOrder order = mustGetOrder(id);
        AuthContext ctx = AuthContext.get();
        if (ctx != null && ctx.isCustomer()
                && (order.getCustomerId() == null || !order.getCustomerId().equals(ctx.getCustomerId()))) {
            // 客户只能查看本人关联工单，越权按不存在处理
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return toDetailResponse(order, batchLoadRefs(List.of(order)));
    }

    /**
     * 分页列表：筛选 status / customerVisibleStatus / source / customerId / staffId / vehicleId /
     * keyword（工单号、任务名模糊）与预计开始时间范围；客户强制本人、司机强制本人。
     */
    public PageResult<WorkOrderDetailResponse> list(int pageNum, int pageSize, String status,
                                                    String customerVisibleStatus, String source,
                                                    Long customerId, Long staffId, Long vehicleId,
                                                    String keyword, LocalDate startDate, LocalDate endDate) {
        validatePage(pageNum, pageSize);
        LambdaQueryWrapper<WorkOrder> wrapper = new LambdaQueryWrapper<>();
        AuthContext ctx = AuthContext.get();
        if (ctx != null && ctx.isCustomer()) {
            wrapper.eq(WorkOrder::getCustomerId, ctx.getCustomerId() == null ? -1L : ctx.getCustomerId());
        }
        if (ctx != null && ctx.isDriver()) {
            if (ctx.getStaffId() == null) {
                return PageResult.of(0, Collections.emptyList());
            }
            wrapper.eq(WorkOrder::getStaffId, ctx.getStaffId());
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(WorkOrder::getStatus, status);
        }
        if (customerVisibleStatus != null && !customerVisibleStatus.isBlank()) {
            wrapper.eq(WorkOrder::getCustomerVisibleStatus, customerVisibleStatus);
        }
        if (source != null && !source.isBlank()) {
            wrapper.eq(WorkOrder::getSource, source);
        }
        if (customerId != null) {
            wrapper.eq(WorkOrder::getCustomerId, customerId);
        }
        if (staffId != null) {
            wrapper.eq(WorkOrder::getStaffId, staffId);
        }
        if (vehicleId != null) {
            wrapper.eq(WorkOrder::getVehicleId, vehicleId);
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(WorkOrder::getOrderNo, keyword).or().like(WorkOrder::getName, keyword));
        }
        if (startDate != null) {
            wrapper.ge(WorkOrder::getExpectedStartAt, startDate.atStartOfDay());
        }
        if (endDate != null) {
            wrapper.le(WorkOrder::getExpectedStartAt, endDate.atTime(LocalTime.MAX));
        }
        // 先计数后追加排序，避免 count 查询携带 ORDER BY
        long total = workOrderMapper.selectCount(wrapper);
        wrapper.orderByDesc(WorkOrder::getId);
        int offset = (pageNum - 1) * pageSize;
        List<WorkOrder> orders = workOrderMapper.selectList(wrapper.last("LIMIT " + offset + "," + pageSize));
        if (orders.isEmpty()) {
            return PageResult.of(total, Collections.emptyList());
        }
        Refs refs = batchLoadRefs(orders);
        List<WorkOrderDetailResponse> list = orders.stream()
                .map(o -> toDetailResponse(o, refs))
                .collect(Collectors.toList());
        return PageResult.of(total, list);
    }

    private void validatePage(int pageNum, int pageSize) {
        if (pageNum < 1 || pageSize < 1 || pageSize > 100) {
            throw new BizException(ErrorCode.PAGE_INVALID);
        }
    }

    private record Refs(Map<Long, Customer> customers, Map<Long, Staff> staffs, Map<Long, Vehicle> vehicles) {
    }

    /** 批量加载客户 / 司机 / 车辆档案，避免列表逐行回表 */
    private Refs batchLoadRefs(List<WorkOrder> orders) {
        Set<Long> customerIds = new HashSet<>();
        Set<Long> staffIds = new HashSet<>();
        Set<Long> vehicleIds = new HashSet<>();
        for (WorkOrder o : orders) {
            if (o.getCustomerId() != null) {
                customerIds.add(o.getCustomerId());
            }
            if (o.getStaffId() != null) {
                staffIds.add(o.getStaffId());
            }
            if (o.getVehicleId() != null) {
                vehicleIds.add(o.getVehicleId());
            }
        }
        Map<Long, Customer> customers = customerIds.isEmpty() ? Map.of()
                : customerMapper.selectBatchIds(customerIds).stream()
                .collect(Collectors.toMap(Customer::getId, Function.identity()));
        Map<Long, Staff> staffs = staffIds.isEmpty() ? Map.of()
                : staffMapper.selectBatchIds(staffIds).stream()
                .collect(Collectors.toMap(Staff::getId, Function.identity()));
        Map<Long, Vehicle> vehicles = vehicleIds.isEmpty() ? Map.of()
                : vehicleMapper.selectBatchIds(vehicleIds).stream()
                .collect(Collectors.toMap(Vehicle::getId, Function.identity()));
        return new Refs(customers, staffs, vehicles);
    }

    private WorkOrderDetailResponse toDetailResponse(WorkOrder order, Refs refs) {
        WorkOrderDetailResponse resp = new WorkOrderDetailResponse();
        resp.setId(order.getId());
        resp.setOrderNo(order.getOrderNo());
        resp.setTaskName(order.getName());
        resp.setSource(order.getSource());
        resp.setDemandId(order.getDemandId());
        resp.setCustomerId(order.getCustomerId());
        Customer customer = order.getCustomerId() == null ? null : refs.customers().get(order.getCustomerId());
        resp.setCustomerName(customer == null ? null : customer.getName());
        resp.setContactId(order.getContactId());
        resp.setStaffId(order.getStaffId());
        Staff staff = order.getStaffId() == null ? null : refs.staffs().get(order.getStaffId());
        if (staff != null) {
            resp.setStaffName(staff.getName());
            resp.setStaffPhone(staff.getPhone());
        }
        resp.setVehicleId(order.getVehicleId());
        Vehicle vehicle = order.getVehicleId() == null ? null : refs.vehicles().get(order.getVehicleId());
        if (vehicle != null) {
            resp.setVehiclePlateNo(vehicle.getPlateNo());
            resp.setVehicleTonnage(vehicle.getTonnage());
        }
        resp.setAddress(order.getAddress());
        resp.setLongitude(order.getLongitude());
        resp.setLatitude(order.getLatitude());
        resp.setCargoName(order.getCargoName());
        resp.setCargoQuantity(order.getCargoQuantity());
        resp.setCargoWeight(order.getCargoWeight());
        resp.setCraneTonnage(order.getCraneTonnage());
        resp.setExpectedStartAt(order.getExpectedStartAt());
        resp.setAcceptedAt(order.getAcceptedAt());
        resp.setStartedAt(order.getStartedAt());
        resp.setSuspendedAt(order.getSuspendedAt());
        resp.setCompletedAt(order.getCompletedAt());
        resp.setCancelledAt(order.getCancelledAt());
        resp.setCancelledReason(order.getCancelledReason());
        resp.setStatus(order.getStatus());
        resp.setCustomerVisibleStatus(order.getCustomerVisibleStatus());
        resp.setCustomerVisibleStatusLabel(
                VISIBLE_STATUS_LABELS.get(order.getCustomerVisibleStatus()));
        resp.setAcceptDeadlineAt(order.getAcceptDeadlineAt());
        resp.setTimeoutRemindedAt(order.getTimeoutRemindedAt());
        resp.setIsPinnedAlarm(order.getIsPinnedAlarm());
        resp.setRemark(order.getRemark());
        resp.setRequirement(order.getRequirement());
        resp.setCreatedAt(order.getCreatedAt());
        resp.setUpdatedAt(order.getUpdatedAt());
        return resp;
    }

    // ==================== 修改 ====================

    /**
     * 管理员修改工单：转派由管理员自行判断是否合适，不做司机状态 / 工单互斥 / 车辆占用的强校验，
     * 仅校验司机与车辆存在性；车辆改派同步占用与绑定调整。
     */
    @Transactional
    public WorkOrder update(Long id, WorkOrderUpdateRequest request, Long operatorId) {
        requireAdmin();
        WorkOrder order = mustGetOrder(id);
        if (WorkOrderStateMachine.isTerminal(order.getStatus())) {
            throw new BizException(ErrorCode.ORDER_STATUS_NOT_ALLOWED,
                    "非法状态流转: " + order.getStatus() + " → 修改");
        }
        Long oldVehicleId = order.getVehicleId();

        boolean staffChanged = request.getStaffId() != null && !request.getStaffId().equals(order.getStaffId());
        if (staffChanged) {
            Staff staff = staffMapper.selectById(request.getStaffId());
            if (staff == null) {
                throw new BizException(ErrorCode.STAFF_NOT_FOUND);
            }
        }
        boolean vehicleChanged = request.getVehicleId() != null && !request.getVehicleId().equals(oldVehicleId);
        Vehicle newVehicle = null;
        if (vehicleChanged) {
            newVehicle = vehicleMapper.selectById(request.getVehicleId());
            if (newVehicle == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "车辆不存在");
            }
        }
        if (request.getPlanStartAt() != null && request.getPlanStartAt().isBefore(LocalDateTime.now())
                && !Boolean.TRUE.equals(request.getConfirmPast())) {
            throw new BizException(ErrorCode.PAST_START_TIME);
        }

        if (staffChanged) {
            order.setStaffId(request.getStaffId());
        }
        if (vehicleChanged) {
            order.setVehicleId(request.getVehicleId());
        }
        if (request.getPlanStartAt() != null) {
            order.setExpectedStartAt(request.getPlanStartAt());
        }
        if (request.getAddress() != null) {
            order.setAddress(request.getAddress());
        }
        if (request.getTaskName() != null && !request.getTaskName().isBlank()) {
            order.setName(request.getTaskName());
        }
        if (request.getCraneTonnage() != null) {
            order.setCraneTonnage(request.getCraneTonnage());
        }
        if (request.getRequirement() != null) {
            order.setRequirement(request.getRequirement());
        }
        if (request.getLongitude() != null) {
            order.setLongitude(request.getLongitude());
        }
        if (request.getLatitude() != null) {
            order.setLatitude(request.getLatitude());
        }
        if (request.getCargoWeightTon() != null) {
            order.setCargoWeight(request.getCargoWeightTon());
        }
        if (request.getRemark() != null) {
            order.setRemark(request.getRemark());
        }
        order.setUpdatedBy(operatorId);
        workOrderMapper.updateById(order);

        // 车辆改派联动：旧车释放（REASSIGN）、新车占用（DISPATCH）、绑定一释放一新建
        if (vehicleChanged) {
            releaseVehicle(oldVehicleId, order.getId(), order.getStaffId(), operatorId, "REASSIGN");
            releaseActiveBinding(order.getId(), "REASSIGN");
            occupyVehicle(newVehicle, order.getId(), order.getStaffId(), operatorId, "DISPATCH");
            createBinding(order.getId(), order.getStaffId(), order.getVehicleId());
        } else if (staffChanged) {
            // 车辆不变、仅换司机时同步生效绑定的司机
            VehicleBinding active = findActiveBinding(order.getId());
            if (active != null) {
                active.setStaffId(order.getStaffId());
                vehicleBindingMapper.updateById(active);
            }
        }
        writeModifyStatusLog(order, operatorId);
        return order;
    }

    /** 管理员修改工单留痕：状态未变化，from 与 to 相同 */
    private void writeModifyStatusLog(WorkOrder order, Long operatorId) {
        WorkOrderStatusLog logEntry = new WorkOrderStatusLog();
        logEntry.setOrderId(order.getId());
        logEntry.setFromStatus(order.getStatus());
        logEntry.setToStatus(order.getStatus());
        logEntry.setOperatorId(operatorId);
        logEntry.setRemark("管理员修改工单");
        logEntry.setCreatedAt(LocalDateTime.now());
        statusLogMapper.insert(logEntry);
    }

    // ==================== 状态变更与结单 ====================

    /** 状态变更：CANCEL 任意非终态可取消（释放车辆与绑定）；SUSPEND / RESUME 走状态机 */
    @Transactional
    public WorkOrder changeStatus(Long id, WorkOrderStatusChangeRequest request, Long operatorId) {
        requireAdmin();
        WorkOrder order = mustGetOrder(id);
        String action = request.getAction() == null ? "" : request.getAction();
        switch (action) {
            case "CANCEL" -> {
                if (request.getReason() == null || request.getReason().isBlank()) {
                    throw new BizException(ErrorCode.PARAM_MISSING, "取消必须填写原因");
                }
                stateMachine.cancel(order, operatorId, request.getReason());
                releaseVehicleAndBinding(order, "CANCELLED", operatorId);
            }
            case "SUSPEND" ->
                    stateMachine.transition(order, WorkOrderStateMachine.SUSPENDED, operatorId, request.getReason());
            case "RESUME" ->
                    stateMachine.transition(order, WorkOrderStateMachine.IN_PROGRESS, operatorId, request.getReason());
            default -> throw new BizException(ErrorCode.ENUM_INVALID, "非法状态变更动作: " + request.getAction());
        }
        return order;
    }

    /** 兜底结单（API-WO-006）：IN_PROGRESS / SUSPENDED 转 COMPLETED，联动车辆释放与工时统计；重复调用幂等返回 */
    @Transactional
    public WorkOrder settle(Long id, Long operatorId) {
        requireAdmin();
        WorkOrder order = mustGetOrder(id);
        if (WorkOrderStateMachine.COMPLETED.equals(order.getStatus())) {
            return order;
        }
        if (!WorkOrderStateMachine.IN_PROGRESS.equals(order.getStatus())
                && !WorkOrderStateMachine.SUSPENDED.equals(order.getStatus())) {
            throw new BizException(ErrorCode.RESOURCE_STATUS_NOT_ALLOWED, "工单当前状态不允许结单");
        }
        stateMachine.transition(order, WorkOrderStateMachine.COMPLETED, operatorId, "管理员兜底结单");
        releaseVehicleAndBinding(order, "COMPLETED", operatorId);
        upsertWorkHourStat(order);
        return order;
    }

    /** 车辆释放 + 绑定释放：状态回 IDLE、当前工单清空、状态日志 RELEASE、绑定置失效 */
    private void releaseVehicleAndBinding(WorkOrder order, String releaseReason, Long operatorId) {
        if (order.getVehicleId() != null) {
            releaseVehicle(order.getVehicleId(), order.getId(), order.getStaffId(), operatorId, "RELEASE");
        }
        releaseActiveBinding(order.getId(), releaseReason);
    }

    /** 释放车辆：LambdaUpdateWrapper 显式置空当前工单，避免 updateById 忽略 null 字段 */
    private void releaseVehicle(Long vehicleId, Long orderId, Long staffId, Long operatorId, String changeType) {
        if (vehicleId == null) {
            return;
        }
        Vehicle vehicle = vehicleMapper.selectById(vehicleId);
        if (vehicle == null || VEHICLE_IDLE.equals(vehicle.getStatus())) {
            return;
        }
        String before = vehicle.getStatus();
        vehicleMapper.update(null, new LambdaUpdateWrapper<Vehicle>()
                .eq(Vehicle::getId, vehicleId)
                .set(Vehicle::getStatus, VEHICLE_IDLE)
                .set(Vehicle::getCurrentOrderId, null));
        insertVehicleStatusLog(vehicleId, before, VEHICLE_IDLE, changeType, orderId, staffId, operatorId,
                changeType + " 释放车辆");
    }

    /** 占用车辆：状态 BUSY、当前工单回填、状态日志 DISPATCH */
    private void occupyVehicle(Vehicle vehicle, Long orderId, Long staffId, Long operatorId, String changeType) {
        String before = vehicle.getStatus();
        vehicle.setStatus(VEHICLE_BUSY);
        vehicle.setCurrentOrderId(orderId);
        vehicleMapper.updateById(vehicle);
        insertVehicleStatusLog(vehicle.getId(), before, VEHICLE_BUSY, changeType, orderId, staffId, operatorId,
                changeType + " 占用车辆");
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

    /** 建立车辆-工单-司机三方生效绑定 */
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

    /** 释放该工单当前生效绑定：置失效并回填释放时间与原因 */
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

    /** 结单联动：写 / 覆盖工时统计，totalMinutes = completedAt - startedAt（分钟） */
    private void upsertWorkHourStat(WorkOrder order) {
        WorkHourStat stat = workHourStatMapper.selectOne(new LambdaQueryWrapper<WorkHourStat>()
                .eq(WorkHourStat::getOrderId, order.getId()));
        long minutes = 0;
        if (order.getStartedAt() != null && order.getCompletedAt() != null) {
            minutes = Math.max(0, Duration.between(order.getStartedAt(), order.getCompletedAt()).toMinutes());
        }
        LocalDateTime now = LocalDateTime.now();
        if (stat == null) {
            stat = new WorkHourStat();
            stat.setOrderId(order.getId());
            stat.setStaffId(order.getStaffId());
            stat.setVehicleId(order.getVehicleId());
            stat.setStartedAt(order.getStartedAt());
            stat.setEndedAt(order.getCompletedAt());
            stat.setTotalMinutes((int) minutes);
            stat.setOvertimeMinutes(0);
            stat.setCheckInCount(0);
            stat.setCheckoutCount(0);
            stat.setCalcVersion(1);
            stat.setComputedAt(now);
            workHourStatMapper.insert(stat);
        } else {
            stat.setStaffId(order.getStaffId());
            stat.setVehicleId(order.getVehicleId());
            stat.setStartedAt(order.getStartedAt());
            stat.setEndedAt(order.getCompletedAt());
            stat.setTotalMinutes((int) minutes);
            stat.setComputedAt(now);
            stat.setCalcVersion(stat.getCalcVersion() == null ? 1 : stat.getCalcVersion() + 1);
            workHourStatMapper.updateById(stat);
        }
    }

    // ==================== 评价 ====================

    /** 客户评价：仅本人已完成工单，一单一评，评分 1~5 */
    @Transactional
    public CustomerReview review(Long id, ReviewRequest request, Long operatorId) {
        AuthContext ctx = AuthContext.get();
        if (ctx == null || !ctx.isCustomer()) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        WorkOrder order = mustGetOrder(id);
        if (order.getCustomerId() == null || !order.getCustomerId().equals(ctx.getCustomerId())) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        if (!WorkOrderStateMachine.COMPLETED.equals(order.getStatus())) {
            throw new BizException(ErrorCode.RESOURCE_STATUS_NOT_ALLOWED, "工单完成后才可评价");
        }
        Long exists = customerReviewMapper.selectCount(new LambdaQueryWrapper<CustomerReview>()
                .eq(CustomerReview::getOrderId, order.getId()));
        if (exists != null && exists > 0) {
            throw new BizException(ErrorCode.RESOURCE_STATUS_NOT_ALLOWED, "该工单已评价，请勿重复提交");
        }
        if (request.getScore() == null || request.getScore() < 1 || request.getScore() > 5) {
            throw new BizException(20005, "评分须在1~5之间");
        }
        CustomerReview review = new CustomerReview();
        review.setOrderId(order.getId());
        review.setCustomerId(order.getCustomerId());
        review.setContactId(order.getContactId());
        review.setScore(request.getScore());
        review.setContent(request.getContent());
        review.setIsDefault(0);
        review.setReviewedAt(LocalDateTime.now());
        review.setCreatedBy(operatorId);
        customerReviewMapper.insert(review);
        return review;
    }

    // ==================== 状态记录 ====================

    /** 状态变更记录分页倒序 */
    public PageResult<WorkOrderStatusLog> statusLogs(Long id, int pageNum, int pageSize) {
        mustGetOrder(id);
        validatePage(pageNum, pageSize);
        LambdaQueryWrapper<WorkOrderStatusLog> wrapper = new LambdaQueryWrapper<WorkOrderStatusLog>()
                .eq(WorkOrderStatusLog::getOrderId, id);
        // 先计数后追加排序，避免 count 查询携带 ORDER BY
        long total = statusLogMapper.selectCount(wrapper);
        wrapper.orderByDesc(WorkOrderStatusLog::getCreatedAt)
                .orderByDesc(WorkOrderStatusLog::getId);
        int offset = (pageNum - 1) * pageSize;
        List<WorkOrderStatusLog> records = statusLogMapper.selectList(wrapper.last("LIMIT " + offset + "," + pageSize));
        return PageResult.of(total, records);
    }

    // ==================== 公共 ====================

    private WorkOrder mustGetOrder(Long id) {
        WorkOrder order = workOrderMapper.selectById(id);
        if (order == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return order;
    }

    /** 司机互斥：存在进行中工单则阻断；excludeOrderId 用于恢复执行 / 改派排除自身 */
    private void checkStaffNoActiveOrder(Long staffId, Long excludeOrderId) {
        LambdaQueryWrapper<WorkOrder> wrapper = new LambdaQueryWrapper<WorkOrder>()
                .eq(WorkOrder::getStaffId, staffId)
                .eq(WorkOrder::getStatus, WorkOrderStateMachine.IN_PROGRESS);
        if (excludeOrderId != null) {
            wrapper.ne(WorkOrder::getId, excludeOrderId);
        }
        Long count = workOrderMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BizException(ErrorCode.DRIVER_MUTEX_CONFLICT);
        }
    }

    private void requireAdmin() {
        AuthContext ctx = AuthContext.get();
        if (ctx == null || !ctx.isAdmin()) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
    }
}
