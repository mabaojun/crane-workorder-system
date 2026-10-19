package com.crane.modules.ai.controller;

import com.crane.common.auth.AuthContext;
import com.crane.common.exception.BizException;
import com.crane.common.exception.ErrorCode;
import com.crane.common.result.ApiResult;
import com.crane.modules.ai.dto.AiDtos.DiagnoseVO;
import com.crane.modules.ai.dto.AiDtos.GenerateReportVO;
import com.crane.modules.ai.dto.AiDtos.LatestReportVO;
import com.crane.modules.ai.service.AiReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 报告接口（管理端 / 大屏共用，均要求管理员登录）。
 * 手动触发日报生成、查询最近报告与连通性诊断；提示词在后台「系统配置-AI 服务配置」的
 * ai.report_prompt 键中自定义编辑。
 */
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiReportService aiReportService;

    /** 手动生成今日 AI 日报（工作台 / 大屏触发按钮）；失败落 FAILED 记录并返回失败原因 */
    @PostMapping("/reports/generate")
    public ApiResult<GenerateReportVO> generate() {
        requireAdmin();
        return ApiResult.ok(aiReportService.generateDailyReport(currentUserId()));
    }

    /** 最近一条 AI 报告（latest 为 null 表示尚未生成过） */
    @GetMapping("/reports/latest")
    public ApiResult<LatestReportVO> latest() {
        requireAdmin();
        LatestReportVO vo = new LatestReportVO();
        vo.setLatest(aiReportService.latest());
        return ApiResult.ok(vo);
    }

    /** AI 连通性诊断：配置检查 + 真实连通测试 + 近 5 次生成稳定性（接口校验报告数据源） */
    @GetMapping("/diagnose")
    public ApiResult<DiagnoseVO> diagnose() {
        requireAdmin();
        return ApiResult.ok(aiReportService.diagnose());
    }

    private void requireAdmin() {
        AuthContext ctx = AuthContext.get();
        if (ctx == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        if (!ctx.isAdmin()) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
    }

    private Long currentUserId() {
        AuthContext ctx = AuthContext.get();
        return ctx == null ? null : ctx.getUserId();
    }
}
