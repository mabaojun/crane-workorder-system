package com.crane.modules.attendance.controller;

import com.crane.common.auth.AuthContext;
import com.crane.common.exception.BizException;
import com.crane.common.exception.ErrorCode;
import com.crane.common.result.ApiResult;
import com.crane.common.result.PageResult;
import com.crane.modules.attendance.dto.CheckInRequest;
import com.crane.modules.attendance.dto.DailyCheckoutRequest;
import com.crane.modules.attendance.dto.TaskEndRequest;
import com.crane.modules.attendance.entity.Attendance;
import com.crane.modules.attendance.entity.OvertimeRecord;
import com.crane.modules.attendance.service.AttendanceService;
import com.crane.modules.workorder.entity.WorkHourStat;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

/**
 * 打卡与工时接口（API-ATT-001 ~ API-ATT-008），路径与 03 §2 清单严格一致。
 * 权限码由鉴权拦截器按角色注入后校验，业务层数据范围校验在服务内完成。
 */
@RestController
@RequestMapping("/api/v1/attendance")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;

    /** API-ATT-001 到场签到 */
    @PostMapping("/check-in")
    public ApiResult<Map<String, Object>> checkIn(@RequestBody CheckInRequest req) {
        return ApiResult.ok(attendanceService.checkIn(req, currentUserId()));
    }

    /** API-ATT-002 当日签退（今日任务完成） */
    @PostMapping("/daily-checkout")
    public ApiResult<Map<String, Object>> dailyCheckout(@RequestBody DailyCheckoutRequest req) {
        return ApiResult.ok(attendanceService.dailyCheckout(req, currentUserId()));
    }

    /** API-ATT-003 任务结束（最终完工签退） */
    @PostMapping("/task-end")
    public ApiResult<Map<String, Object>> taskEnd(@RequestBody TaskEndRequest req) {
        return ApiResult.ok(attendanceService.taskEnd(req, currentUserId()));
    }

    /** API-ATT-004 打卡记录查询 */
    @GetMapping("/records")
    public ApiResult<PageResult<Attendance>> records(
            @RequestParam(required = false) Long orderId,
            @RequestParam(required = false) Long staffId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResult.ok(attendanceService.records(orderId, staffId, type, startDate, endDate, pageNum, pageSize));
    }

    /** API-ATT-005 工时查询 */
    @GetMapping("/work-hours")
    public ApiResult<PageResult<WorkHourStat>> workHours(
            @RequestParam(required = false) Long staffId,
            @RequestParam(required = false) Long orderId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResult.ok(attendanceService.workHours(staffId, orderId, startDate, endDate, pageNum, pageSize));
    }

    /**
     * API-ATT-006 工时导出。
     * 本期留空实现：导出能力（签名链接与文件生成）二期开放，当前直接返回提示错误码。
     */
    @GetMapping("/work-hours/export")
    public ApiResult<Void> workHoursExport() {
        throw new BizException(50001, "导出功能二期开放");
    }

    /** API-ATT-007 加班记录查询 */
    @GetMapping("/overtime")
    public ApiResult<PageResult<OvertimeRecord>> overtimeList(
            @RequestParam(name = "confirmStatus", required = false) String auditStatus,
            @RequestParam(required = false) Long staffId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResult.ok(attendanceService.overtimeList(auditStatus, staffId, pageNum, pageSize));
    }

    /** API-ATT-008 加班管理员确认 */
    @PostMapping("/overtime/{id}/confirm")
    public ApiResult<Map<String, Object>> confirmOvertime(@PathVariable Long id,
                                                          @RequestBody Map<String, Boolean> body) {
        Boolean approve = body == null ? null : body.get("approved");
        return ApiResult.ok(attendanceService.confirmOvertime(id, approve, currentUserId()));
    }

    private Long currentUserId() {
        AuthContext ctx = AuthContext.get();
        if (ctx == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return ctx.getUserId();
    }
}
