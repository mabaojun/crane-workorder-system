package com.crane.modules.notify.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通知记录表 biz_notification_record。
 * 管理端事件通知（如司机退单）落库留痕，并同步经 SSE 实时推送。
 */
@Data
@TableName("biz_notification_record")
public class NotificationRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 通知流水号 */
    private String notifyNo;

    /** 去重键：业务+节点+接收人+轮次 */
    private String dedupKey;

    /** 业务类型：ORDER/DEMAND/EXCEPTION/CONFLICT */
    private String bizType;

    /** 关联业务记录 id */
    private Long bizId;

    /** 关联工单 */
    private Long orderId;

    /** 接收人类型：CUSTOMER/DRIVER/ADMIN */
    private String receiverType;

    /** 接收人 id（管理员为 sys_user.id） */
    private Long receiverId;

    /** 发送目标手机号快照 */
    private String receiverPhone;

    /** 通知节点：DISPATCHED/ACCEPTED/WITHDRAW/COMPLETED/CANCELLED 等 */
    private String notifyNode;

    /** 通道：WECHAT/SMS/INAPP */
    private String channel;

    private String templateCode;

    /** 通知内容快照 */
    private String content;

    /** 发送状态：PENDING/SUCCESS/FAILED/UNKNOWN/DEGRADED */
    private String sendStatus;

    private Integer retryCount;

    private LocalDateTime sentAt;

    private String failReason;

    private String degradeFromChannel;

    /** 是否计费（短信为 1） */
    private Integer isBillable;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Long createdBy;

    private Long updatedBy;

    @TableLogic
    private Integer deleted;
}
