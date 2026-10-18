package com.crane.modules.exception.dto;

import lombok.Data;

/**
 * 异常批复请求体（API-EXC-004，管理员）。
 */
@Data
public class ExceptionReplyRequest {

    /** 是否同意：true 按指令执行、false 仅记录驳回且工单状态不变 */
    private Boolean approve;

    /** 批复指令：CONTINUE 继续施工、SUSPEND 暂停、TERMINATE 终止 */
    private String instruction;

    /** 批复意见，可选 */
    private String opinion;
}
