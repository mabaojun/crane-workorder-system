package com.crane.modules.workorder.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * API-WO-005 工单状态变更请求体。
 * action 取值：CANCEL 取消、SUSPEND 暂停、RESUME 恢复。
 */
@Data
public class WorkOrderStatusChangeRequest {

    @NotBlank(message = "action 必填")
    private String action;

    /** 取消原因，CANCEL 时必填；其余动作可作说明 */
    private String reason;
}
