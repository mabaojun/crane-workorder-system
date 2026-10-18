package com.crane.modules.dispatch.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * API-DISP-001 派单前置校验请求体。
 */
@Data
public class PrecheckRequest {

    @NotNull(message = "staffId 必填")
    private Long staffId;

    @NotNull(message = "vehicleId 必填")
    private Long vehicleId;

    /** 预计开始时间，为空视为通过时间校验 */
    private LocalDateTime planStartAt;
}
