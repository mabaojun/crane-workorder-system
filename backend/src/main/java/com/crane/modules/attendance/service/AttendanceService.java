package com.crane.modules.attendance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.crane.common.auth.AuthContext;
import com.crane.common.exception.BizException;
import com.crane.common.exception.ErrorCode;
import com.crane.common.result.PageResult;
import com.crane.modules.attendance.dto.CheckInRequest;
import com.crane.modules.attendance.dto.DailyCheckoutRequest;
import com.crane.modules.attendance.dto.TaskEndRequest;
import com.crane.modules.attendance.entity.Attendance;
import com.crane.modules.attendance.entity.DailyCheckout;
import com.crane.modules.attendance.entity.OvertimeRecord;
import com.crane.modules.attendance.mapper.AttendanceMapper;
import com.crane.modules.attendance.mapper.DailyCheckoutMapper;
import com.crane.modules.attendance.mapper.OvertimeRecordMapper;
import com.crane.modules.exception.entity.ExceptionRecord;
import com.crane.modules.exception.mapper.ExceptionRecordMapper;
import com.crane.modules.exception.service.ExceptionService;
import com.crane.modules.place.service.CommonPlaceService;
import com.crane.modules.staff.entity.Vehicle;
import com.crane.modules.staff.entity.VehicleStatusLog;
import com.crane.modules.staff.mapper.VehicleMapper;
import com.crane.modules.staff.mapper.VehicleStatusLogMapper;
import com.crane.modules.system.service.SystemConfigService;
import com.crane.modules.workorder.entity.WorkHourStat;
import com.crane.modules.workorder.entity.WorkOrder;
import com.crane.modules.workorder.mapper.WorkHourStatMapper;
import com.crane.modules.workorder.mapper.WorkOrderMapper;
import com.crane.modules.workorder.service.WorkOrderStateMachine;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 打卡与工时服务（API-ATT-001 ~ API-ATT-008）。
 * 范围口径：签到强制范围内（超范围返回 30005），签退弱校验（超范围不拒绝、标注不合规）。
 * 工单点位缺失时距离按 0 处理、不拦截。
 * 车辆联动与工时统计与状态机调用同事务提交。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceService {

    private static final String TYPE_CHECK_IN = "CHECK_IN";
    private static final String TYPE_DAILY_CHECKOUT = "DAILY_CHECKOUT";
    private static final String TYPE_TASK_END = "TASK_END";

    private static final String STATUS_PENDING_ARRIVE = "PENDING_ARRIVE";
    private static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    private static final String STATUS_SUSPENDED = "SUSPENDED";
    private static final String STATUS_COMPLETED = "COMPLETED";

    private static final String VEHICLE_STATUS_IDLE = "IDLE";
    private static final String VEHICLE_STATUS_BUSY = "BUSY";

    private static final String UPLOAD_MODE_ONLINE = "ONLINE";
    private static final String UPLOAD_MODE_OFFLINE_RETRY = "OFFLINE_RETRY";

    private final AttendanceMapper attendanceMapper;
    private final DailyCheckoutMapper dailyCheckoutMapper;
    private final OvertimeRecordMapper overtimeRecordMapper;
    private final WorkOrderMapper workOrderMapper;
    private final WorkHourStatMapper workHourStatMapper;
    private final WorkOrderStateMachine workOrderStateMachine;
    private final VehicleMapper vehicleMapper;
    private final VehicleStatusLogMapper vehicleStatusLogMapper;
    private final ExceptionRecordMapper exceptionRecordMapper;
    private final SystemConfigService systemConfigService;
    private final GeoService geoService;
    private final ObjectMapper objectMapper;
    private final ExceptionService exceptionService;
    private final CommonPlaceService commonPlaceService;

    /**
     * 到场签到（API-ATT-001，司机本人）。
     * 校验链：工单存在且属本人 -> 状态 PENDING_ARRIVE -> 无未批复异常 -> 坐标非空 -> 范围内 -> 照片必传。
     * 成功后工单经状态机转 IN_PROGRESS，车辆转 BUSY。
     */
    @Transactional
    public Map<String, Object> checkIn(CheckInRequest req, Long operatorId) {
        AuthContext ctx = requireDriver();
        if (req.getOrderId() == null) {
            throw new BizException(ErrorCode.PARAM_MISSING, "工单 id 必填");
        }
        WorkOrder order = requireOrderOwnedBy(req.getOrderId(), ctx);
        if (!STATUS_PENDING_ARRIVE.equals(order.getStatus())) {
            throw new BizException(ErrorCode.ORDER_STATUS_NOT_ALLOWED, "工单当前状态不允许签到");
        }
        if (hasPendingException(order.getId())) {
            throw new BizException(ErrorCode.PENDING_EXCEPTION_EXISTS);
        }
        // 定位强制开关：false（测试阶段默认）时坐标可空，不做范围拦截；有坐标仍记录距离供审计
        boolean locRequired = locationRequired();
        Integer distanceMeters = null;
        Integer inRange = null;
        int validRange = systemConfigService.getInt("attendance.valid_range_meters", 1000);
        DistanceResult distance = null;
        if (locRequired && (req.getLongitude() == null || req.getLatitude() == null)) {
            throw new BizException(ErrorCode.COORDINATE_INVALID, "签到定位经纬度必填");
        }
        if (req.getLongitude() != null && req.getLatitude() != null) {
            distance = measureDistance(req.getLongitude(), req.getLatitude(), order);
            distanceMeters = distance.meters();
            inRange = distance.inRange(validRange) ? 1 : 0;
            // 签到为强制范围校验：开关开启且范围外必须拒绝
            if (locRequired && !distance.inRange(validRange)) {
                throw new BizException(ErrorCode.OUT_OF_CHECKIN_RANGE);
            }
        }
        if (photoRequired() && (req.getPhotoUrls() == null || req.getPhotoUrls().isEmpty())) {
            throw new BizException(ErrorCode.PHOTO_REQUIRED, "现场照片至少 1 张");
        }

        LocalDateTime now = LocalDateTime.now();
        Attendance att = new Attendance();
        att.setOrderId(order.getId());
        att.setStaffId(order.getStaffId());
        att.setType(TYPE_CHECK_IN);
        att.setCheckedAt(now);
        att.setCapturedAt(req.getCapturedAt());
        att.setLongitude(req.getLongitude());
        att.setLatitude(req.getLatitude());
        att.setDistanceMeters(distanceMeters);
        att.setIsInRange(inRange);
        att.setIsCompliant(inRange);
        att.setPhotoUrls(toJson(req.getPhotoUrls()));
        att.setIsValid(1);
        att.setUploadMode(Boolean.TRUE.equals(req.getOfflineSync()) ? UPLOAD_MODE_OFFLINE_RETRY : UPLOAD_MODE_ONLINE);
        attendanceMapper.insert(att);

        workOrderStateMachine.transition(order, STATUS_IN_PROGRESS, ctx.getStaffId(), "司机签到");
        markVehicleBusy(order.getVehicleId());

        // 施工点位自动沉淀为常用地点：名称=工单地址，经纬度=签到坐标（无签到坐标时回退工单坐标），按名称去重
        commonPlaceService.upsertFromSite(order.getAddress(),
                req.getLongitude() != null ? req.getLongitude() : order.getLongitude(),
                req.getLatitude() != null ? req.getLatitude() : order.getLatitude());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("attendanceId", att.getId());
        data.put("workOrderStatus", STATUS_IN_PROGRESS);
        data.put("distanceMeters", distanceMeters);
        data.put("vehicleStatus", VEHICLE_STATUS_BUSY);
        return data;
    }

    /**
     * 当日签退（API-ATT-002，司机本人）。
     * 弱校验：超范围不拒绝，is_compliant 标注 0；跨过正常下班时间且当日晚间在岗则生成加班记录待管理员审核。
     * 不改变工单状态（跨天任务次日无需再签到，工时由任务起止时间覆盖）。
     */
    @Transactional
    public Map<String, Object> dailyCheckout(DailyCheckoutRequest req, Long operatorId) {
        AuthContext ctx = requireDriver();
        if (req.getOrderId() == null) {
            throw new BizException(ErrorCode.PARAM_MISSING, "工单 id 必填");
        }
        WorkOrder order = requireOrderOwnedBy(req.getOrderId(), ctx);
        if (!STATUS_IN_PROGRESS.equals(order.getStatus()) && !STATUS_SUSPENDED.equals(order.getStatus())) {
            throw new BizException(ErrorCode.ORDER_STATUS_NOT_ALLOWED, "工单当前状态不允许签退");
        }
        LocalDate today = LocalDate.now();
        Long dup = dailyCheckoutMapper.selectCount(new LambdaQueryWrapper<DailyCheckout>()
                .eq(DailyCheckout::getOrderId, order.getId())
                .eq(DailyCheckout::getWorkDate, today));
        if (dup != null && dup > 0) {
            throw new BizException(ErrorCode.RESOURCE_STATUS_NOT_ALLOWED, "今日已完成签退");
        }
        if (hasPendingException(order.getId())) {
            if (STATUS_SUSPENDED.equals(order.getStatus())) {
                // 暂停态签退：未批复异常按「继续施工」自动关闭留痕，不阻断签退
                exceptionService.autoClosePendingForOrder(order.getId(), operatorId, "司机暂停态签退，异常自动关闭", STATUS_SUSPENDED);
            } else {
                throw new BizException(ErrorCode.PENDING_EXCEPTION_EXISTS);
            }
        }
        // 定位强制开关：false（测试阶段默认）时坐标可空，弱校验本就不拦截；有坐标仍记录距离
        if (locationRequired() && (req.getLongitude() == null || req.getLatitude() == null)) {
            throw new BizException(ErrorCode.COORDINATE_INVALID, "签退定位经纬度必填");
        }

        int validRange = systemConfigService.getInt("attendance.valid_range_meters", 1000);
        int overtimeRange = systemConfigService.getInt("attendance.overtime_range_meters", 1000);
        Integer distanceMeters = null;
        Integer inRange = null;
        DistanceResult distance = null;
        if (req.getLongitude() != null && req.getLatitude() != null) {
            distance = measureDistance(req.getLongitude(), req.getLatitude(), order);
            distanceMeters = distance.meters();
            inRange = distance.inRange(validRange) ? 1 : 0;
        }
        LocalDateTime now = LocalDateTime.now();

        Attendance att = new Attendance();
        att.setOrderId(order.getId());
        att.setStaffId(order.getStaffId());
        att.setType(TYPE_DAILY_CHECKOUT);
        att.setCheckedAt(now);
        att.setCapturedAt(req.getCapturedAt());
        att.setLongitude(req.getLongitude());
        att.setLatitude(req.getLatitude());
        att.setDistanceMeters(distanceMeters);
        att.setIsInRange(inRange);
        att.setIsCompliant(inRange);
        att.setIsValid(1);
        att.setPhotoUrls(toJson(req.getPhotoUrls()));
        att.setUploadMode(Boolean.TRUE.equals(req.getOfflineSync()) ? UPLOAD_MODE_OFFLINE_RETRY : UPLOAD_MODE_ONLINE);
        attendanceMapper.insert(att);

        DailyCheckout checkout = new DailyCheckout();
        checkout.setOrderId(order.getId());
        checkout.setStaffId(order.getStaffId());
        checkout.setAttendanceId(att.getId());
        checkout.setWorkDate(today);
        checkout.setDayStartAt(order.getStartedAt());
        checkout.setDayEndAt(now);
        checkout.setLongitude(req.getLongitude());
        checkout.setLatitude(req.getLatitude());
        checkout.setDistanceMeters(distanceMeters);
        checkout.setIsInRange(inRange);
        checkout.setPhotoUrls(toJson(req.getPhotoUrls()));
        checkout.setIsLeftSite(Boolean.TRUE.equals(req.getIsLeftSite()) ? 1 : 0);
        checkout.setIsReturned(Boolean.TRUE.equals(req.getIsReturned()) ? 1 : 0);
        dailyCheckoutMapper.insert(checkout);

        // 加班判定：实际签退晚于当日正常下班时间即生成待审核加班记录；位置不合规或无定位仅标注不阻断
        int overtimeMinutes = 0;
        boolean locationCompliant = distance != null;
        LocalTime normalOffTime = LocalTime.parse(systemConfigService.getOrDefault("overtime.normal_off_time", "18:00"));
        if (now.toLocalTime().isAfter(normalOffTime)) {
            LocalDateTime normalOffAt = LocalDateTime.of(today, normalOffTime);
            overtimeMinutes = (int) Duration.between(normalOffAt, now).toMinutes();
            locationCompliant = distance != null && distance.meters() <= overtimeRange;
            OvertimeRecord overtime = new OvertimeRecord();
            overtime.setOrderId(order.getId());
            overtime.setStaffId(order.getStaffId());
            overtime.setDailyCheckoutId(checkout.getId());
            overtime.setWorkDate(today);
            overtime.setNormalOffAt(normalOffAt);
            overtime.setActualCheckoutAt(now);
            overtime.setCheckoutLongitude(req.getLongitude());
            overtime.setCheckoutLatitude(req.getLatitude());
            overtime.setCheckoutDistanceMeters(distance.meters());
            overtime.setIsLocationCompliant(locationCompliant ? 1 : 0);
            overtime.setOvertimeMinutes(overtimeMinutes);
            overtime.setAuditStatus("PENDING");
            overtime.setIsEffective(0);
            overtimeRecordMapper.insert(overtime);
        }

        String vehicleStatus = applyVehicleAfterDailyCheckout(order, req);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("attendanceId", att.getId());
        data.put("workOrderStatus", order.getStatus());
        data.put("vehicleStatus", vehicleStatus);
        data.put("locationCompliant", locationCompliant);
        data.put("overtimeMinutes", overtimeMinutes);
        data.put("needsManualConfirm", overtimeMinutes > 0 && !locationCompliant);
        if (overtimeMinutes > 0 && !locationCompliant) {
            data.put("warning", "签退位置不合规，加班需人工确认");
        }
        return data;
    }

    /**
     * 任务结束（API-ATT-003，司机本人）。
     * 写 TASK_END 打卡后经状态机转 COMPLETED，联动车辆释放（IDLE、清空当前工单、写 RELEASE 状态日志），
     * 并按「启动 -> 结束」口径重算 biz_work_hour_stat（按 orderId upsert，重算 calc_version 递增）。
     */
    @Transactional
    public Map<String, Object> taskEnd(TaskEndRequest req, Long operatorId) {
        AuthContext ctx = requireDriver();
        if (req.getOrderId() == null) {
            throw new BizException(ErrorCode.PARAM_MISSING, "工单 id 必填");
        }
        WorkOrder order = requireOrderOwnedBy(req.getOrderId(), ctx);
        if (!STATUS_IN_PROGRESS.equals(order.getStatus()) && !STATUS_SUSPENDED.equals(order.getStatus())) {
            throw new BizException(ErrorCode.ORDER_STATUS_NOT_ALLOWED, "工单当前状态不允许结单");
        }
        if (hasPendingException(order.getId())) {
            if (STATUS_SUSPENDED.equals(order.getStatus())) {
                // 暂停态结单：未批复异常按「继续施工」自动关闭留痕，随后工单 SUSPENDED → COMPLETED
                exceptionService.autoClosePendingForOrder(order.getId(), operatorId, "司机暂停态结单，异常自动关闭", STATUS_COMPLETED);
            } else {
                throw new BizException(ErrorCode.PENDING_EXCEPTION_EXISTS);
            }
        }
        // 定位/照片强制开关：false（测试阶段默认）时坐标与照片均可空；有坐标仍记录距离
        if (locationRequired() && (req.getLongitude() == null || req.getLatitude() == null)) {
            throw new BizException(ErrorCode.COORDINATE_INVALID, "签退定位经纬度必填");
        }
        if (photoRequired() && (req.getPhotoUrls() == null || req.getPhotoUrls().isEmpty())) {
            throw new BizException(ErrorCode.PHOTO_REQUIRED, "现场照片至少 1 张");
        }

        int validRange = systemConfigService.getInt("attendance.valid_range_meters", 1000);
        Integer distanceMeters = null;
        Integer inRange = null;
        if (req.getLongitude() != null && req.getLatitude() != null) {
            DistanceResult distance = measureDistance(req.getLongitude(), req.getLatitude(), order);
            distanceMeters = distance.meters();
            inRange = distance.inRange(validRange) ? 1 : 0;
        }
        LocalDateTime now = LocalDateTime.now();

        Attendance att = new Attendance();
        att.setOrderId(order.getId());
        att.setStaffId(order.getStaffId());
        att.setType(TYPE_TASK_END);
        att.setCheckedAt(now);
        att.setCapturedAt(req.getCapturedAt());
        att.setLongitude(req.getLongitude());
        att.setLatitude(req.getLatitude());
        att.setDistanceMeters(distanceMeters);
        att.setIsInRange(inRange);
        att.setIsCompliant(inRange);
        att.setIsValid(1);
        att.setPhotoUrls(toJson(req.getPhotoUrls()));
        att.setUploadMode(Boolean.TRUE.equals(req.getOfflineSync()) ? UPLOAD_MODE_OFFLINE_RETRY : UPLOAD_MODE_ONLINE);
        attendanceMapper.insert(att);

        workOrderStateMachine.transition(order, STATUS_COMPLETED, ctx.getStaffId(), "任务结束");
        // 状态机未回填完工时间时兜底补齐，保证工时统计终点可靠
        if (order.getCompletedAt() == null) {
            order.setCompletedAt(now);
            workOrderMapper.updateById(order);
        }
        releaseVehicle(order, operatorId, now);
        Map<String, Object> stat = upsertWorkHourStat(order, now);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("attendanceId", att.getId());
        data.put("workOrderStatus", STATUS_COMPLETED);
        data.put("vehicleStatus", VEHICLE_STATUS_IDLE);
        data.put("totalWorkMinutes", stat.get("totalMinutes"));
        data.put("overtimeMinutesTotal", stat.get("overtimeMinutes"));
        return data;
    }

    /**
     * 打卡记录查询（API-ATT-004）。管理员全量、司机强制本人、客户禁止访问。
     */
    @Transactional(readOnly = true)
    public PageResult<Attendance> records(Long orderId, Long staffId, String type,
                                          LocalDate startDate, LocalDate endDate,
                                          int pageNum, int pageSize) {
        ensurePage(pageNum, pageSize);
        AuthContext ctx = requireContext();
        Long effectiveStaffId = staffId;
        if (ctx.isDriver()) {
            effectiveStaffId = ctx.getStaffId();
        } else if (!ctx.isAdmin()) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        if (type != null && !List.of(TYPE_CHECK_IN, TYPE_DAILY_CHECKOUT, TYPE_TASK_END).contains(type)) {
            throw new BizException(ErrorCode.ENUM_INVALID, "打卡类型取值非法");
        }
        LambdaQueryWrapper<Attendance> wrapper = new LambdaQueryWrapper<Attendance>()
                .eq(orderId != null, Attendance::getOrderId, orderId)
                .eq(effectiveStaffId != null, Attendance::getStaffId, effectiveStaffId)
                .eq(type != null, Attendance::getType, type)
                .ge(startDate != null, Attendance::getCheckedAt, startDate == null ? null : startDate.atStartOfDay())
                .le(endDate != null, Attendance::getCheckedAt, endDate == null ? null : endDate.atTime(LocalTime.MAX));
        return pagedQuery(attendanceMapper, wrapper, Attendance::getCheckedAt, pageNum, pageSize);
    }

    /**
     * 工时查询（API-ATT-005）。数据来源 biz_work_hour_stat，权限同打卡记录查询。
     */
    @Transactional(readOnly = true)
    public PageResult<WorkHourStat> workHours(Long staffId, Long orderId,
                                              LocalDate startDate, LocalDate endDate,
                                              int pageNum, int pageSize) {
        ensurePage(pageNum, pageSize);
        AuthContext ctx = requireContext();
        Long effectiveStaffId = staffId;
        if (ctx.isDriver()) {
            effectiveStaffId = ctx.getStaffId();
        } else if (!ctx.isAdmin()) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        LambdaQueryWrapper<WorkHourStat> wrapper = new LambdaQueryWrapper<WorkHourStat>()
                .eq(effectiveStaffId != null, WorkHourStat::getStaffId, effectiveStaffId)
                .eq(orderId != null, WorkHourStat::getOrderId, orderId)
                .ge(startDate != null, WorkHourStat::getStartedAt, startDate == null ? null : startDate.atStartOfDay())
                .le(endDate != null, WorkHourStat::getEndedAt, endDate == null ? null : endDate.atTime(LocalTime.MAX));
        return pagedQuery(workHourStatMapper, wrapper, WorkHourStat::getComputedAt, pageNum, pageSize);
    }

    /**
     * 加班记录查询（API-ATT-007）。管理员全量、司机强制本人、其他角色禁止。
     */
    @Transactional(readOnly = true)
    public PageResult<OvertimeRecord> overtimeList(String auditStatus, Long staffId, int pageNum, int pageSize) {
        ensurePage(pageNum, pageSize);
        AuthContext ctx = requireContext();
        Long effectiveStaffId = staffId;
        if (ctx.isDriver()) {
            effectiveStaffId = ctx.getStaffId();
        } else if (!ctx.isAdmin()) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        LambdaQueryWrapper<OvertimeRecord> wrapper = new LambdaQueryWrapper<OvertimeRecord>()
                .eq(auditStatus != null, OvertimeRecord::getAuditStatus, auditStatus)
                .eq(effectiveStaffId != null, OvertimeRecord::getStaffId, effectiveStaffId);
        return pagedQuery(overtimeRecordMapper, wrapper, OvertimeRecord::getActualCheckoutAt, pageNum, pageSize);
    }

    /**
     * 加班管理员确认（API-ATT-008，仅管理员）。已审核记录不可重复审核。
     */
    @Transactional
    public Map<String, Object> confirmOvertime(Long id, Boolean approve, Long operatorId) {
        requireAdmin();
        if (id == null || approve == null) {
            throw new BizException(ErrorCode.PARAM_MISSING, "加班记录 id 与审核结论必填");
        }
        OvertimeRecord overtime = overtimeRecordMapper.selectById(id);
        if (overtime == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "加班记录不存在");
        }
        if (!"PENDING".equals(overtime.getAuditStatus())) {
            throw new BizException(ErrorCode.RESOURCE_STATUS_NOT_ALLOWED, "该加班记录已审核，不可重复审核");
        }
        overtime.setAuditStatus(approve ? "APPROVED" : "REJECTED");
        overtime.setAuditedBy(operatorId);
        overtime.setAuditedAt(LocalDateTime.now());
        overtime.setIsEffective(approve ? 1 : 0);
        overtimeRecordMapper.updateById(overtime);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("overtimeId", overtime.getId());
        data.put("confirmStatus", overtime.getAuditStatus());
        data.put("overtimeMinutes", overtime.getOvertimeMinutes());
        return data;
    }

    // ==================== 私有方法 ====================

    private AuthContext requireContext() {
        AuthContext ctx = AuthContext.get();
        if (ctx == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return ctx;
    }

    private AuthContext requireDriver() {
        AuthContext ctx = requireContext();
        if (!ctx.isDriver()) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅司机本人可执行该操作");
        }
        return ctx;
    }

    private void requireAdmin() {
        AuthContext ctx = requireContext();
        if (!ctx.isAdmin()) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅管理员可执行该操作");
        }
    }

    private WorkOrder requireOrderOwnedBy(Long orderId, AuthContext ctx) {
        WorkOrder order = workOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "工单不存在");
        }
        if (!Objects.equals(order.getStaffId(), ctx.getStaffId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅可操作本人执行工单");
        }
        return order;
    }

    /** 该工单是否存在未批复异常：存在则不可签到 / 签退（30008）。 */
    private boolean hasPendingException(Long orderId) {
        Long count = exceptionRecordMapper.selectCount(new LambdaQueryWrapper<ExceptionRecord>()
                .eq(ExceptionRecord::getOrderId, orderId)
                .eq(ExceptionRecord::getStatus, "PENDING"));
        return count != null && count > 0;
    }

    /** 签到/签退是否强制定位（后台开关，测试阶段默认关闭；false 时坐标可空且不做范围拦截） */
    private boolean locationRequired() {
        return "true".equalsIgnoreCase(systemConfigService.getOrDefault("attendance.location_required", "false"));
    }

    /** 打卡是否强制现场照片（后台开关，测试阶段默认关闭；false 时照片可空） */
    private boolean photoRequired() {
        return "true".equalsIgnoreCase(systemConfigService.getOrDefault("attendance.photo_required", "false"));
    }

    /**
     * 计算打卡点与工单预设点位的直线距离；工单点位缺失按 0 处理不拦截。
     */
    private DistanceResult measureDistance(BigDecimal longitude, BigDecimal latitude, WorkOrder order) {
        if (order.getLongitude() == null || order.getLatitude() == null) {
            return new DistanceResult(0);
        }
        double d = geoService.haversine(longitude, latitude, order.getLongitude(), order.getLatitude());
        return new DistanceResult(d < 0 ? 0 : (int) Math.round(d));
    }

    private void markVehicleBusy(Long vehicleId) {
        if (vehicleId == null) {
            return;
        }
        Vehicle vehicle = vehicleMapper.selectById(vehicleId);
        if (vehicle == null || VEHICLE_STATUS_BUSY.equals(vehicle.getStatus())) {
            return;
        }
        vehicle.setStatus(VEHICLE_STATUS_BUSY);
        vehicleMapper.updateById(vehicle);
    }

    /**
     * 每日签退的车辆联动：回场则状态回 IDLE 且清除在外标记；离场未回场则标记在外。
     */
    private String applyVehicleAfterDailyCheckout(WorkOrder order, DailyCheckoutRequest req) {
        boolean returned = Boolean.TRUE.equals(req.getIsReturned());
        boolean leftSite = Boolean.TRUE.equals(req.getIsLeftSite());
        if (order.getVehicleId() == null || (!returned && !leftSite)) {
            return null;
        }
        Vehicle vehicle = vehicleMapper.selectById(order.getVehicleId());
        if (vehicle == null) {
            return null;
        }
        if (returned) {
            vehicle.setIsAway(0);
            if (!VEHICLE_STATUS_IDLE.equals(vehicle.getStatus())) {
                vehicle.setStatus(VEHICLE_STATUS_IDLE);
            }
        } else {
            vehicle.setIsAway(1);
        }
        vehicleMapper.updateById(vehicle);
        return vehicle.getStatus();
    }

    /**
     * 结单车辆释放：状态回 IDLE、清空当前工单（显式置 null）、写 RELEASE 状态日志。
     * currentOrderId 需置空，updateById 默认忽略 null 字段，故使用 UpdateWrapper 显式 set。
     */
    private void releaseVehicle(WorkOrder order, Long operatorId, LocalDateTime now) {
        if (order.getVehicleId() == null) {
            return;
        }
        Vehicle vehicle = vehicleMapper.selectById(order.getVehicleId());
        if (vehicle == null) {
            return;
        }
        String beforeStatus = vehicle.getStatus();
        // 使用字符串列名 UpdateWrapper：避免单测环境初始化 MyBatis-Plus lambda 缓存，且支持显式置 null
        vehicleMapper.update(null, new UpdateWrapper<Vehicle>()
                .eq("id", vehicle.getId())
                .set("status", VEHICLE_STATUS_IDLE)
                .set("current_order_id", null)
                .set("is_away", 0));
        VehicleStatusLog statusLog = new VehicleStatusLog();
        statusLog.setVehicleId(vehicle.getId());
        statusLog.setBeforeStatus(beforeStatus);
        statusLog.setAfterStatus(VEHICLE_STATUS_IDLE);
        statusLog.setChangeType("RELEASE");
        statusLog.setOrderId(order.getId());
        statusLog.setStaffId(order.getStaffId());
        statusLog.setOperatorId(operatorId);
        statusLog.setOccurAt(now);
        statusLog.setRemark("任务结束释放车辆");
        vehicleStatusLogMapper.insert(statusLog);
    }

    /**
     * 工时统计 upsert：按 orderId 查，存在则覆盖更新并递增 calc_version。
     * totalMinutes = 完工时间 - 启动时间；overtimeMinutes 仅含已生效（is_effective = 1）加班。
     */
    private Map<String, Object> upsertWorkHourStat(WorkOrder order, LocalDateTime now) {
        Long checkInCount = attendanceMapper.selectCount(new LambdaQueryWrapper<Attendance>()
                .eq(Attendance::getOrderId, order.getId())
                .eq(Attendance::getType, TYPE_CHECK_IN));
        Long checkoutCount = attendanceMapper.selectCount(new LambdaQueryWrapper<Attendance>()
                .eq(Attendance::getOrderId, order.getId())
                .in(Attendance::getType, List.of(TYPE_DAILY_CHECKOUT, TYPE_TASK_END)));
        List<OvertimeRecord> effectiveOvertime = overtimeRecordMapper.selectList(new LambdaQueryWrapper<OvertimeRecord>()
                .eq(OvertimeRecord::getOrderId, order.getId())
                .eq(OvertimeRecord::getIsEffective, 1));
        int overtimeMinutesTotal = effectiveOvertime.stream()
                .mapToInt(o -> o.getOvertimeMinutes() == null ? 0 : o.getOvertimeMinutes())
                .sum();
        long totalMinutes = 0;
        if (order.getStartedAt() != null && order.getCompletedAt() != null) {
            totalMinutes = Duration.between(order.getStartedAt(), order.getCompletedAt()).toMinutes();
        }

        WorkHourStat stat = workHourStatMapper.selectOne(new LambdaQueryWrapper<WorkHourStat>()
                .eq(WorkHourStat::getOrderId, order.getId()));
        boolean isNew = stat == null;
        if (isNew) {
            stat = new WorkHourStat();
            stat.setCalcVersion(1);
        } else {
            stat.setCalcVersion(stat.getCalcVersion() == null ? 2 : stat.getCalcVersion() + 1);
        }
        stat.setOrderId(order.getId());
        stat.setStaffId(order.getStaffId());
        stat.setVehicleId(order.getVehicleId());
        stat.setStartedAt(order.getStartedAt());
        stat.setEndedAt(order.getCompletedAt());
        stat.setTotalMinutes((int) Math.max(totalMinutes, 0));
        stat.setOvertimeMinutes(overtimeMinutesTotal);
        stat.setCheckInCount(checkInCount == null ? 0 : checkInCount.intValue());
        stat.setCheckoutCount(checkoutCount == null ? 0 : checkoutCount.intValue());
        stat.setComputedAt(now);
        if (isNew) {
            workHourStatMapper.insert(stat);
        } else {
            workHourStatMapper.updateById(stat);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalMinutes", stat.getTotalMinutes());
        data.put("overtimeMinutes", overtimeMinutesTotal);
        return data;
    }

    private void ensurePage(int pageNum, int pageSize) {
        if (pageNum < 1 || pageSize < 1 || pageSize > 100) {
            throw new BizException(ErrorCode.PAGE_INVALID);
        }
    }

    /**
     * 手动分页查询：项目未注册 MyBatis-Plus 分页拦截器，用 count + LIMIT 方式保证物理分页可靠。
     */
    private <T> PageResult<T> pagedQuery(com.baomidou.mybatisplus.core.mapper.BaseMapper<T> mapper,
                                         LambdaQueryWrapper<T> wrapper,
                                         SFunction<T, ?> orderField,
                                         int pageNum, int pageSize) {
        long total = mapper.selectCount(wrapper);
        int offset = (pageNum - 1) * pageSize;
        wrapper.orderByDesc(orderField).last("LIMIT " + offset + ", " + pageSize);
        List<T> list = mapper.selectList(wrapper);
        return PageResult.of(total, list);
    }

    private String toJson(List<String> urls) {
        if (urls == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(urls);
        } catch (JsonProcessingException e) {
            log.warn("照片路径序列化失败: {}", urls, e);
            throw new BizException(ErrorCode.SYSTEM_ERROR, "照片路径序列化失败");
        }
    }

    /**
     * 距离计算结果（米），配合有效范围判断是否在范围内。
     */
    private record DistanceResult(int meters) {
        private boolean inRange(int validRangeMeters) {
            return meters <= validRangeMeters;
        }
    }
}
