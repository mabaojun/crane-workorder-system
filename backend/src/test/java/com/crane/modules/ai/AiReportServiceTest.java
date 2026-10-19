package com.crane.modules.ai;

import com.crane.modules.ai.client.AiChatClient;
import com.crane.modules.ai.dto.AiDtos.ReportVO;
import com.crane.modules.ai.entity.AiBriefing;
import com.crane.modules.ai.mapper.AiBriefingMapper;
import com.crane.modules.ai.service.AiReportService;
import com.crane.modules.stat.dto.StatDtos.AlarmItem;
import com.crane.modules.stat.dto.StatDtos.AlarmListVO;
import com.crane.modules.stat.dto.StatDtos.DashboardVO;
import com.crane.modules.stat.service.StatService;
import com.crane.modules.system.service.SystemConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AI 报告服务单元测试：提示词占位符替换、mock 通道生成落库、失败落 FAILED。
 */
@ExtendWith(MockitoExtension.class)
class AiReportServiceTest {

    @Mock
    private AiBriefingMapper briefingMapper;
    @Mock
    private AiChatClient aiChatClient;
    @Mock
    private StatService statService;
    @Mock
    private SystemConfigService systemConfigService;

    private AiReportService service;

    @BeforeEach
    void setUp() {
        service = new AiReportService(briefingMapper, aiChatClient, statService, systemConfigService);
    }

    private DashboardVO dash(int orderToday, int completed, int inProgress, int pendingAccept) {
        DashboardVO dash = new DashboardVO();
        com.crane.modules.stat.dto.StatDtos.OrderStat order = new com.crane.modules.stat.dto.StatDtos.OrderStat();
        order.setToday(orderToday);
        order.setCompleted(completed);
        order.setInProgress(inProgress);
        order.setPendingAccept(pendingAccept);
        order.setSuspended(1);
        order.setCancelled(2);
        dash.setOrder(order);
        com.crane.modules.stat.dto.StatDtos.VehicleStat vehicle = new com.crane.modules.stat.dto.StatDtos.VehicleStat();
        vehicle.setBusy(2);
        vehicle.setBusyThird(1);
        vehicle.setMaintenance(1);
        vehicle.setIdle(5);
        dash.setVehicle(vehicle);
        com.crane.modules.stat.dto.StatDtos.CustomerStat customer = new com.crane.modules.stat.dto.StatDtos.CustomerStat();
        customer.setDemandPending(3);
        dash.setCustomer(customer);
        com.crane.modules.stat.dto.StatDtos.ExceptionTypeCount exc = new com.crane.modules.stat.dto.StatDtos.ExceptionTypeCount();
        exc.setType("RAIN");
        exc.setCount(2);
        dash.setExceptionDistribution(List.of(exc));
        return dash;
    }

    @Test
    void 提示词占位符全部替换() {
        when(systemConfigService.getOrDefault(eq("ai.report_prompt"), any())).thenAnswer(inv -> inv.getArgument(1));

        AlarmItem alarm = new AlarmItem();
        alarm.setType("NOT_ACCEPTED");
        alarm.setLevel("HIGH");
        alarm.setDurationMinutes(70);
        AlarmListVO alarmVO = new AlarmListVO();
        alarmVO.setCount(1);
        alarmVO.setList(List.of(alarm));

        String prompt = service.buildPrompt(LocalDate.of(2026, 10, 19),
                dash(6, 4, 3, 2), alarmVO.getList());

        assertThat(prompt).doesNotContain("{").doesNotContain("}");
        assertThat(prompt).contains("2026年10月19日")
                .contains("新增工单：6").contains("完成 4").contains("进行中 3")
                .contains("待接单 2").contains("报警：1 条").contains("超时未接单(HIGH,70分钟)")
                .contains("异常上报：2 条").contains("需求单：3 条")
                .contains("作业中 2 台").contains("维保 1 台");
    }

    @Test
    void 自定义提示词模板生效() {
        when(systemConfigService.getOrDefault(eq("ai.report_prompt"), any())).thenReturn("自定义模板 {orderToday}");

        String prompt = service.buildPrompt(LocalDate.now(), dash(9, 0, 0, 0), List.of());

        assertThat(prompt).isEqualTo("自定义模板 9");
    }

    @Test
    void 无报警时分布显示无() {
        when(systemConfigService.getOrDefault(eq("ai.report_prompt"), any())).thenAnswer(inv -> inv.getArgument(1));

        String prompt = service.buildPrompt(LocalDate.now(), dash(0, 0, 0, 0), List.of());

        assertThat(prompt).contains("报警类型分布：无");
    }

    @Test
    void mock通道生成成功并落库() {
        stubDashboardAndAlarms();
        when(systemConfigService.getOrDefault(eq("ai.report_prompt"), any())).thenAnswer(inv -> inv.getArgument(1));
        when(systemConfigService.getInt("ai.briefing_retention_days", 10)).thenReturn(10);
        when(aiChatClient.chat(any(), any())).thenReturn("# 简报正文");
        when(briefingMapper.insert(any(AiBriefing.class))).thenAnswer(inv -> {
            inv.getArgument(0, AiBriefing.class).setId(1L);
            return 1;
        });

        var result = service.generateDailyReport(1L);

        assertThat(result.getReport().getCallStatus()).isEqualTo("SUCCESS");
        assertThat(result.getReport().getContentMarkdown()).isEqualTo("# 简报正文");
        assertThat(result.getReport().getBriefingNo()).startsWith("AI");
        assertThat(result.getPromptDigest()).contains("今日新增工单");
        verify(briefingMapper).insert(any(AiBriefing.class));
    }

    @Test
    void 调用失败落FAILED并抛出() {
        stubDashboardAndAlarms();
        when(systemConfigService.getOrDefault(eq("ai.report_prompt"), any())).thenAnswer(inv -> inv.getArgument(1));
        when(systemConfigService.getInt("ai.briefing_retention_days", 10)).thenReturn(10);
        when(aiChatClient.chat(any(), any()))
                .thenThrow(new com.crane.common.exception.BizException(com.crane.common.exception.ErrorCode.AI_CALL_FAILED, "超时"));
        when(briefingMapper.insert(any(AiBriefing.class))).thenReturn(1);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.generateDailyReport(1L))
                .isInstanceOf(com.crane.common.exception.BizException.class)
                .hasMessageContaining("超时");

        org.mockito.ArgumentCaptor<AiBriefing> captor = org.mockito.ArgumentCaptor.forClass(AiBriefing.class);
        verify(briefingMapper).insert(captor.capture());
        assertThat(captor.getValue().getCallStatus()).isEqualTo("FAILED");
        assertThat(captor.getValue().getFailReason()).contains("超时");
    }

    @Test
    void latest返回最近一条() {
        AiBriefing entity = new AiBriefing();
        entity.setId(9L);
        entity.setBriefingNo("AI202610190001");
        entity.setType("DAILY");
        entity.setCallStatus("SUCCESS");
        entity.setContentMarkdown("内容");
        entity.setGeneratedAt(LocalDateTime.now());
        when(briefingMapper.selectOne(any())).thenReturn(entity);

        ReportVO vo = service.latest();

        assertThat(vo).isNotNull();
        assertThat(vo.getBriefingNo()).isEqualTo("AI202610190001");
    }

    private void stubDashboardAndAlarms() {
        lenient().when(statService.dashboard()).thenReturn(dash(6, 4, 3, 2));
        AlarmListVO alarmVO = new AlarmListVO();
        alarmVO.setCount(0);
        alarmVO.setList(List.of());
        lenient().when(statService.alarms()).thenReturn(alarmVO);
    }
}
