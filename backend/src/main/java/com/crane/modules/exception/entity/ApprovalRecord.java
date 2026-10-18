package com.crane.modules.exception.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审批记录表 biz_approval_record（02 §3.3.15）。
 * 异常与变更审批合并一张表，以 biz_type 区分：EXCEPTION 异常批复、ORDER_CHANGE 变更申请审批。
 * 表内不冗余业务明细，明细仍留在各自业务表。
 */
@Data
@TableName("biz_approval_record")
public class ApprovalRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务类型：EXCEPTION、ORDER_CHANGE */
    private String bizType;

    /** 关联业务记录 id */
    private Long bizId;

    /** 关联工单，供工单详情页直接聚合审批流 */
    private Long orderId;

    /** 审批人账号 */
    private Long approverId;

    /** 审批动作：APPROVE 通过、REJECT 驳回 */
    private String action;

    /** 下发指令，如继续施工、暂停、终止 */
    private String instruction;

    /** 审批意见 */
    private String opinion;

    /** 审批时间 */
    private LocalDateTime approvedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Long createdBy;

    private Long updatedBy;

    @TableLogic
    private Integer deleted;
}
