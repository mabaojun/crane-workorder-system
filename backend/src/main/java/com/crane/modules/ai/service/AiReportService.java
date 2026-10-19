package com.crane.modules.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crane.common.exception.BizException;
import com.crane.common.exception.ErrorCode;
import com.crane.modules.ai.client.AiChatClient;
import com.crane.modules.ai.dto.AiDtos.DiagnoseVO;
import com.crane.modules.ai.dto.AiDtos.GenerateReportVO;
import com.crane.modules.ai.dto.AiDtos.ReportVO;
import com.crane.modules.ai.entity.AiBriefing;
import com.crane.modules.ai.mapper.AiBriefingMapper;
import com.crane.modules.stat.dto.StatDtos.AlarmItem;
import com.crane.modules.stat.dto.StatDtos.DashboardVO;
import com.crane.modules.stat.service.StatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * AI 报告服务：手动触发生成日报（聚合当日业务数据 → 提示词模板 → 大模型 → 落库）。
 * 提示词模板存 sys_config（ai.report_prompt），后台可自定义编辑；占位符运行时替换。
 * 生成失败同样落 FAILED 记录（fail_reason），保证可追溯；mock 模式走固定演示文本。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiReportService {

    private static final DateTimeFormatter NO_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter CN_DATE = DateTimeFormatter.ofPattern("yyyy年M月d日");
    private static final String TYPE_DAILY = "DAILY";

    /** 默认提示词模板（sys_config 键 ai.report_prompt 的种子同文，后台可改） */
    static final String DEFAULT_PROMPT_TEMPLATE = """
            你是吊装公司的运营分析助手。请根据以下当日运营数据，输出一份 Markdown 格式的「今日工作简报」。
            要求：
            1. 使用二级标题分节：今日概览、工单执行情况、报警与风险、异常与需求、明日关注建议。
            2. 数据必须严格引用输入，不得编造；数值为 0 时也如实说明。
            3. 语言简洁专业，总长不超过 400 字，结尾给 2-3 条具体可执行的明日建议。
            4. 不要输出 JSON，不要使用代码块包裹全文。

            日期：{date}
            今日新增工单：{orderToday} 单，其中完成 {completedToday} 单、进行中 {inProgress} 单、待接单 {pendingAccept} 单、暂停 {suspended} 单、已取消 {cancelled} 单。
            当前未处理报警：{alarmCount} 条。报警类型分布：{alarmTop}。
            车辆证照临期：车险临期 {insuranceExpiring} 台，审车临期 {inspectionExpiring} 台（临期/过期明细：{certDetail}）。
            今日异常上报：{exceptionToday} 条。待处理需求单：{demandPending} 条。
            车辆状态：作业中 {vehicleBusy} 台，第三方派单外出 {vehicleThird} 台，维保 {vehicleMaintenance} 台，空闲 {vehicleIdle} 台。
            """;

    private final AiBriefingMapper briefingMapper;
    private final AiChatClient aiChatClient;
    private final StatService statService;
    private final com.crane.modules.system.service.SystemConfigService systemConfigService;

    /**
     * 手动生成今日日报：聚合 → 提示词 → 调用 → 落库。
     * 任何调用失败都落 FAILED 简报并抛出带原因的异常，前端可直接提示。
     */
    public GenerateReportVO generateDailyReport(Long operatorId) {
        DashboardVO dash = statService.dashboard();
        List<AlarmItem> alarms = statService.alarms().getList();
        String userPrompt = buildPrompt(LocalDate.now(), dash, alarms);

        AiBriefing briefing = new AiBriefing();
        briefing.setBriefingNo(generateBriefingNo());
        briefing.setType(TYPE_DAILY);
        briefing.setPeriodStart(LocalDate.now());
        briefing.setPeriodEnd(LocalDate.now());
        briefing.setRetryCount(0);
        briefing.setGeneratedAt(LocalDateTime.now());
        briefing.setExpiresAt(LocalDateTime.now().plusDays(retentionDays()));
        briefing.setCreatedBy(operatorId);
        briefing.setUpdatedBy(operatorId);

        String content;
        try {
            content = aiChatClient.chat(systemPrompt(), userPrompt);
            briefing.setCallStatus("SUCCESS");
            briefing.setContentMarkdown(content);
        } catch (BizException e) {
            log.warn("AI 日报生成失败: {}", e.getMessage());
            briefing.setCallStatus("FAILED");
            briefing.setContentMarkdown("");
            briefing.setFailReason(abbreviate(e.getMessage(), 255));
            briefingMapper.insert(briefing);
            throw e;
        }
        briefingMapper.insert(briefing);

        GenerateReportVO vo = new GenerateReportVO();
        vo.setReport(toVO(briefing));
        vo.setPromptDigest(abbreviate(userPrompt, 800));
        return vo;
    }

    /** 最近一条报告（无论成败），供工作台 / 大屏展示 */
    public ReportVO latest() {
        AiBriefing briefing = briefingMapper.selectOne(new LambdaQueryWrapper<AiBriefing>()
                .orderByDesc(AiBriefing::getId)
                .last("LIMIT 1"));
        return briefing == null ? null : toVO(briefing);
    }

    /** AI 连通性诊断：配置检查 + 真实连通测试 + 近期稳定性观察 */
    public DiagnoseVO diagnose() {
        AiChatClient.AiConfig config = aiChatClient.loadConfig();
        java.util.Map<String, Object> probe = aiChatClient.diagnose();

        DiagnoseVO vo = new DiagnoseVO();
        vo.setEnabled(config.enabled());
        vo.setMock(config.mock());
        vo.setBaseUrlConfigured(notBlank(config.baseUrl()));
        vo.setApiKeyConfigured(notBlank(config.apiKey()));
        vo.setModelConfigured(notBlank(config.model()));
        vo.setModel(config.model());
        vo.setOk(Boolean.TRUE.equals(probe.get("ok")));
        vo.setLatencyMs(((Number) probe.getOrDefault("latencyMs", 0)).longValue());
        vo.setReason(String.valueOf(probe.get("reason")));
        vo.setRecentStatuses(briefingMapper.selectList(new LambdaQueryWrapper<AiBriefing>()
                        .orderByDesc(AiBriefing::getId)
                        .last("LIMIT 5"))
                .stream().map(AiBriefing::getCallStatus).collect(Collectors.toList()));
        return vo;
    }

    /** 组装用户提示词：默认模板可被 sys_config ai.report_prompt 覆盖，占位符运行时替换（测试可见） */
    public String buildPrompt(LocalDate date, DashboardVO dash, List<AlarmItem> alarms) {
        String template = systemPromptTemplate();
        String alarmTop = alarms.isEmpty() ? "无" : alarms.stream()
                .limit(5)
                .map(a -> String.format("%s(%s,%d分钟)", labelOf(a.getType()), a.getLevel(), a.getDurationMinutes()))
                .collect(Collectors.joining("、"));
        // 证照临期：从报警集合中按类型拆分计数与明细（已纳入 AI 分析的车辆管理数据）
        List<AlarmItem> insurance = alarms.stream()
                .filter(a -> "VEHICLE_INSURANCE_EXPIRING".equals(a.getType())).toList();
        List<AlarmItem> inspection = alarms.stream()
                .filter(a -> "VEHICLE_INSPECTION_EXPIRING".equals(a.getType())).toList();
        String certDetail = java.util.stream.Stream.concat(insurance.stream(), inspection.stream())
                .limit(6)
                .map(a -> a.getPlateNo() + " " + a.getSummary())
                .collect(Collectors.joining("；"));
        return template
                .replace("{date}", date.format(CN_DATE))
                .replace("{orderToday}", String.valueOf(dash.getOrder().getToday()))
                .replace("{completedToday}", String.valueOf(dash.getOrder().getCompleted()))
                .replace("{inProgress}", String.valueOf(dash.getOrder().getInProgress()))
                .replace("{pendingAccept}", String.valueOf(dash.getOrder().getPendingAccept()))
                .replace("{suspended}", String.valueOf(dash.getOrder().getSuspended()))
                .replace("{cancelled}", String.valueOf(dash.getOrder().getCancelled()))
                .replace("{alarmCount}", String.valueOf(alarms.size()))
                .replace("{alarmTop}", alarmTop)
                .replace("{insuranceExpiring}", String.valueOf(insurance.size()))
                .replace("{inspectionExpiring}", String.valueOf(inspection.size()))
                .replace("{certDetail}", certDetail.isBlank() ? "无" : certDetail)
                .replace("{exceptionToday}", String.valueOf(dash.getExceptionDistribution().stream()
                        .mapToInt(e -> e.getCount()).sum()))
                .replace("{demandPending}", String.valueOf(dash.getCustomer().getDemandPending()))
                .replace("{vehicleBusy}", String.valueOf(dash.getVehicle().getBusy()))
                .replace("{vehicleThird}", String.valueOf(dash.getVehicle().getBusyThird()))
                .replace("{vehicleMaintenance}", String.valueOf(dash.getVehicle().getMaintenance()))
                .replace("{vehicleIdle}", String.valueOf(dash.getVehicle().getIdle()));
    }

    /** 当前生效的提示词模板（测试可见） */
    public String systemPromptTemplate() {
        String configured = systemConfigService.getOrDefault("ai.report_prompt", DEFAULT_PROMPT_TEMPLATE);
        return configured == null || configured.isBlank() ? DEFAULT_PROMPT_TEMPLATE : configured;
    }

    private String systemPrompt() {
        return "你是吊装公司的运营分析助手，输出严谨、简洁的中文 Markdown 简报。";
    }

    private int retentionDays() {
        return systemConfigService.getInt("ai.briefing_retention_days", 10);
    }

    /** 报警类型中文名（与前端标签一致，供提示词数据引用） */
    private String labelOf(String type) {
        return switch (type) {
            case "CONFLICT" -> "司机工时冲突";
            case "NOT_ACCEPTED" -> "超时未接单";
            case "NOT_ARRIVED" -> "未按时到场";
            case "NOT_CHECKED_IN" -> "未签到";
            case "VEHICLE_OCCUPIED" -> "车辆长时占用";
            case "VEHICLE_INSURANCE_EXPIRING" -> "车险临期";
            case "VEHICLE_INSPECTION_EXPIRING" -> "审车临期";
            default -> type;
        };
    }

    /** 简报编号：AI + yyyyMMdd + 4 位当日序号，冲突时毫秒兜底 */
    private String generateBriefingNo() {
        String prefix = "AI" + LocalDate.now().format(NO_DATE);
        AiBriefing max = briefingMapper.selectOne(new LambdaQueryWrapper<AiBriefing>()
                .likeRight(AiBriefing::getBriefingNo, prefix)
                .orderByDesc(AiBriefing::getBriefingNo)
                .last("LIMIT 1"));
        int seq = 1;
        if (max != null && max.getBriefingNo().length() == prefix.length() + 4) {
            seq = Integer.parseInt(max.getBriefingNo().substring(prefix.length())) + 1;
        }
        String no = prefix + String.format("%04d", seq);
        return no.length() > 32 ? prefix + String.valueOf(System.currentTimeMillis()) : no;
    }

    private ReportVO toVO(AiBriefing briefing) {
        ReportVO vo = new ReportVO();
        vo.setId(briefing.getId());
        vo.setBriefingNo(briefing.getBriefingNo());
        vo.setType(briefing.getType());
        vo.setPeriodStart(briefing.getPeriodStart());
        vo.setPeriodEnd(briefing.getPeriodEnd());
        vo.setContentMarkdown(briefing.getContentMarkdown());
        vo.setCallStatus(briefing.getCallStatus());
        vo.setFailReason(briefing.getFailReason());
        vo.setGeneratedAt(briefing.getGeneratedAt());
        vo.setExpiresAt(briefing.getExpiresAt());
        return vo;
    }

    private String abbreviate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
