package com.crane.modules.dispatch.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 接单拒单记录表 biz_order_accept_log（02 §3.3.7）。
 * 派单 / 重新派单本身不落日志，司机接单或拒单时写一条，deliver_round 区分派单轮次。
 */
@Data
@TableName("biz_order_accept_log")
public class OrderAcceptLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联工单 */
    private Long orderId;

    /** 操作司机 */
    private Long staffId;

    /** 动作：ACCEPT 接单、REJECT 拒单 */
    private String action;

    /** 拒单原因类型：VEHICLE_FAULT、TIME_CONFLICT、ABILITY_MISMATCH、OTHER */
    private String reasonType;

    /** 拒单原因明细，接单时为空 */
    private String reason;

    /** 操作时间 */
    private LocalDateTime actedAt;

    /** 派单轮次，首次派单为 1，重新派单后递增 */
    private Integer deliverRound;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Long createdBy;

    private Long updatedBy;

    @TableLogic
    private Integer deleted;
}
