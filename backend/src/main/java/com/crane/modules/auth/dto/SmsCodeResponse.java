package com.crane.modules.auth.dto;

import lombok.Data;

/**
 * 发送短信验证码响应（API-AUTH-003）。
 */
@Data
public class SmsCodeResponse {

    /** 验证码有效期（秒） */
    private int expiresIn;

    /** 再次发送间隔（秒） */
    private int interval;

    /** 调试验证码：仅 crane.sms.mock=true（未接短信通道）时返回，供开发联调使用 */
    private String debugCode;
}
