package com.crane.modules.dispatch.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * API-DISP-002 派单 / API-DISP-005 重新派单请求体。staffId、vehicleId 必填；
 * planStartAt 提供时同步工单约定时间；早于当前时间需 confirmPast 二次确认。
 */
@Data
public class DispatchRequest {

    @NotNull(message = "staffId 必填")
    private Long staffId;

    @NotNull(message = "vehicleId 必填")
    private Long vehicleId;

    /** 用车种类，选填；提供时须与车辆种类一致 */
    private String vehicleType;

    /** 约定服务时间，提供时回写工单预计开始时间 */
    private LocalDateTime planStartAt;

    /** 预计开始时间早于当前时间时的二次确认标记，默认 false */
    private Boolean confirmPast;

    /** 备注，选填 */
    private String remark;
}
