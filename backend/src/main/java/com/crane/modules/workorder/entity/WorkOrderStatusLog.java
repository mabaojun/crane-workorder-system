package com.crane.modules.workorder.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工单状态变更记录表 biz_work_order_status_log。
 * 每次状态流转（含管理员修改工单）写一条，与工单更新同事务落库。
 */
@Data
@TableName("biz_work_order_status_log")
public class WorkOrderStatusLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联工单 */
    private Long orderId;

    /** 流转前状态，修改工单等无状态变化场景与 to_status 相同 */
    private String fromStatus;

    /** 流转后状态 */
    private String toStatus;

    /** 操作账号（sys_user.id） */
    private Long operatorId;

    /** 操作说明 */
    private String remark;

    private LocalDateTime createdAt;

    @TableLogic
    private Integer deleted;
}
