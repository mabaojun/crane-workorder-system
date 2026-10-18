package com.crane.modules.demand.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 需求单复核入参（管理端）。
 * approve=true 仅将状态置为 REVIEWING 并记录复核意见；驳回时记录驳回原因并累加 rejectCount。
 */
@Data
public class DemandReviewRequest {

    /** true 进入待复核（REVIEWING）；false 驳回（REJECTED） */
    @NotNull(message = "复核结论不能为空")
    private Boolean approve;

    /** 复核意见或驳回原因，对客户可见 */
    @Size(max = 500, message = "复核意见长度不能超过 500")
    private String reviewRemark;
}
