package com.crane.modules.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * AI 简报实体（ai_briefing，02 §3.4.2）。
 * 手动 / 定时生成的 AI 报告落库，含生成状态与失败原因，供工作台与大屏查询展示。
 */
@Data
@TableName("ai_briefing")
public class AiBriefing {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 简报编号：AI + yyyyMMdd + 4 位序号 */
    private String briefingNo;

    /** 简报类型：DAILY 日报 / WEEKLY 周报 / MONTHLY 月报 / PERSONNEL 等 */
    private String type;

    /** 统计周期起始日期 */
    private LocalDate periodStart;

    /** 统计周期结束日期 */
    private LocalDate periodEnd;

    /** 简报正文，Markdown 格式 */
    private String contentMarkdown;

    /** 结构化简报数据（JSON），预留 */
    private String contentJson;

    /** 使用的 AI 服务配置 */
    private Long aiConfigId;

    /** 生成状态：SUCCESS 成功 / FAILED 失败 / SKIPPED 跳过 */
    private String callStatus;

    /** 重试次数 */
    private Integer retryCount;

    /** 失败原因 */
    private String failReason;

    /** 生成时间 */
    private LocalDateTime generatedAt;

    /** 过期时间 = 生成时间 + 保留天数 */
    private LocalDateTime expiresAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Long createdBy;

    private Long updatedBy;

    private Integer deleted;
}
