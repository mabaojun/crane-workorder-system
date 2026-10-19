package com.crane.modules.stat.controller;

import com.crane.common.auth.AuthContext;
import com.crane.common.exception.BizException;
import com.crane.common.exception.ErrorCode;
import com.crane.common.result.ApiResult;
import com.crane.modules.stat.dto.StatDtos.AlarmListVO;
import com.crane.modules.stat.dto.StatDtos.DashboardVO;
import com.crane.modules.stat.dto.StatDtos.StaffPerformance;
import com.crane.modules.stat.dto.StatDtos.VehicleMapVO;
import com.crane.modules.stat.dto.StatusBoardDtos.BoardVO;
import com.crane.modules.stat.service.StatService;
import com.crane.modules.stat.service.StatusBoardService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 统计域接口：数据驾驶舱专用，路径前缀 /api/v1/stat。
 * 需登录且仅后台管理员（ADMIN）可访问，数据范围为全部。
 */
@RestController
@RequestMapping("/api/v1/stat")
@RequiredArgsConstructor
public class StatController {

    private final StatService statService;
    private final StatusBoardService statusBoardService;

    /** 驾驶舱总览：工单、趋势、异常、车辆、员工、客户需求与排名聚合 */
    @GetMapping("/dashboard")
    public ApiResult<DashboardVO> dashboard() {
        requireAdmin();
        return ApiResult.ok(statService.dashboard());
    }

    /** 报警中心：冲突、未到场、未签到、车辆持续占用四类报警 */
    @GetMapping("/alarms")
    public ApiResult<AlarmListVO> alarms() {
        requireAdmin();
        return ApiResult.ok(statService.alarms());
    }

    /** 车辆地图：进行中工单的施工点位与报警标记 */
    @GetMapping("/vehicle-map")
    public ApiResult<VehicleMapVO> vehicleMap() {
        requireAdmin();
        return ApiResult.ok(statService.vehicleMap());
    }

    /** 员工绩效：按 MONTH / WEEK / DAY 时间窗统计各司机工单数与完工率 */
    @GetMapping("/performance/staff")
    public ApiResult<List<StaffPerformance>> staffPerformance(@RequestParam(required = false) String period) {
        requireAdmin();
        return ApiResult.ok(statService.staffPerformance(period));
    }

    /** 工作台状态看板：按日期聚合人员 / 车辆 / 工单三种视角 */
    @GetMapping("/status-board")
    public ApiResult<BoardVO> statusBoard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        requireAdmin();
        return ApiResult.ok(statusBoardService.board(date));
    }

    /** 统计域访问控制：未登录按 10001，非管理员按 10003 拒绝 */
    private void requireAdmin() {
        AuthContext ctx = AuthContext.get();
        if (ctx == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        if (!ctx.isAdmin()) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
    }
}
