package com.crane.modules.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 一句话下工单：自然语言解析入参与结构化结果。
 */
public final class AiOrderParseDtos {

    private AiOrderParseDtos() {
    }

    @Data
    public static class ParseRequest {
        @NotBlank(message = "text 不能为空")
        @Size(max = 500, message = "text 最长 500")
        private String text;
    }

    @Data
    public static class ParseResultVO {
        private String rawText;
        /** CREATE_ORDER 新建工单 / DEMAND_CONVERT 需求转工单 */
        private String intent;
        /** LLM 大模型解析 / RULE 本地规则解析 */
        private String parseMode;

        /** 命中的待转需求单 */
        private Long demandId;
        private String demandNo;

        private Long customerId;
        private String customerName;
        private Long staffId;
        private String staffName;
        private Long vehicleId;
        private String plateNo;

        private String address;
        private BigDecimal longitude;
        private BigDecimal latitude;
        private LocalDateTime planStartAt;
        private BigDecimal craneTonnage;
        private BigDecimal cargoWeight;
        private String cargoName;
        private String remark;

        /** 仍缺失、需要在表单中补填的字段 */
        private List<String> missingFields = new ArrayList<>();
        /** 匹配说明与风险提示，前端直接展示 */
        private List<String> warnings = new ArrayList<>();
    }
}
