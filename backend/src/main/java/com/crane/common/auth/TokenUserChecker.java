package com.crane.common.auth;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 强制下线校验钩子：由 auth 模块实现（查询 sys_user.force_logout_at），
 * common 层不直接依赖业务 Mapper。无实现时跳过该校验（T-00 阶段）。
 */
public interface TokenUserChecker {

    /**
     * @return 用户当前强制下线时间戳（epoch millis）；账号不存在或已停用返回 null 并由调用方处理
     * @throws com.crane.common.exception.BizException 账号停用等业务异常
     */
    Long forceLogoutAt(Long userId);
}
