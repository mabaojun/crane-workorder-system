package com.crane.modules.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 短信验证码表（02 §3.2.7）。承载登录 / 绑定的验证码发送与校验记录，
 * 按手机号 + 场景留存，支持发送频次控制与验证码追溯。
 */
@Data
@TableName("sys_sms_code")
public class SmsCode {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 接收手机号 */
    private String phone;

    /** 场景：LOGIN 登录 / BIND_PHONE 绑定手机号 */
    private String scene;

    /** 验证码 */
    private String code;

    /** 过期时间 */
    private LocalDateTime expiresAt;

    /** 校验成功使用时间，未使用为空 */
    private LocalDateTime usedAt;

    /** 该手机号当日同场景发送次数，用于频次控制 */
    private Integer sendCount;

    /** 短信发送结果（第三方返回或降级标记），MVP 未接通道记 MOCK */
    private String sendResult;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Long createdBy;

    private Long updatedBy;

    @TableLogic
    private Integer deleted;
}
