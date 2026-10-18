package com.crane.modules.dispatch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * API-DISP-004 拒单请求体。reasonType 枚举 VEHICLE_FAULT / TIME_CONFLICT / ABILITY_MISMATCH / OTHER，
 * OTHER 时 reason 必填。
 */
@Data
public class RejectRequest {

    @NotBlank(message = "reasonType 必填")
    private String reasonType;

    @Size(max = 200, message = "reason 最长 200")
    private String reason;
}
