package com.crane.modules.ai.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 报告域 DTO。
 */
public final class AiDtos {

    private AiDtos() {
    }

    /** AI 报告视图：列表与详情共用 */
    @Data
    public static class ReportVO {
        private Long id;
        private String briefingNo;
        /** DAILY / WEEKLY / MONTHLY */
        private String type;
        private LocalDate periodStart;
        private LocalDate periodEnd;
        /** 报告正文 Markdown */
        private String contentMarkdown;
        /** SUCCESS / FAILED / SKIPPED */
        private String callStatus;
        private String failReason;
        private LocalDateTime generatedAt;
        private LocalDateTime expiresAt;
    }

    /** 最近报告包装：latest 为空表示从未生成 */
    @Data
    public static class LatestReportVO {
        private ReportVO latest;
    }

    /** 手动生成结果：生成成功返回报告正文；失败落 FAILED 记录并返回失败原因 */
    @Data
    public static class GenerateReportVO {
        private ReportVO report;
        /** 生成期间的输入快照（聚合的业务数据摘要），供核对参数传递正确性 */
        private String promptDigest;
    }

    /** AI 连通性诊断出参（接口校验报告数据源） */
    @Data
    public static class DiagnoseVO {
        private boolean enabled;
        private boolean mock;
        private boolean baseUrlConfigured;
        private boolean apiKeyConfigured;
        private boolean modelConfigured;
        private String model;
        private boolean ok;
        private long latencyMs;
        private String reason;
        /** 近 5 次生成记录的状态序列，用于响应稳定性观察 */
        private List<String> recentStatuses;
    }
}
