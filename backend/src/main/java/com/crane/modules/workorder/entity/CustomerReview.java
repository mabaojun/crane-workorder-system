package com.crane.modules.workorder.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客户评价表 biz_customer_review（02 §3.3.20）。一单一评价（order_id 唯一），评分 1~5。
 */
@Data
@TableName("biz_customer_review")
public class CustomerReview {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联工单，一单一评价 */
    private Long orderId;

    /** 评价客户 */
    private Long customerId;

    /** 评价联系人 */
    private Long contactId;

    /** 评分，1~5 */
    private Integer score;

    /** 文字评价 */
    private String content;

    /** 是否默认好评：0 否、1 是 */
    private Integer isDefault;

    /** 评价时间 */
    private LocalDateTime reviewedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Long createdBy;

    private Long updatedBy;

    @TableLogic
    private Integer deleted;
}
