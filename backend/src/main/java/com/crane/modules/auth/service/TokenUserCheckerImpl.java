package com.crane.modules.auth.service;

import com.crane.common.auth.TokenUserChecker;
import com.crane.common.exception.BizException;
import com.crane.common.exception.ErrorCode;
import com.crane.modules.system.entity.SysUser;
import com.crane.modules.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 强制下线校验实现：查询 sys_user.force_logout_at 供 AuthInterceptor 比对令牌签发时间。
 * 账号不存在抛 40001；冻结 / 停用账号直接抛 10005 拒绝访问。
 */
@Component
@RequiredArgsConstructor
public class TokenUserCheckerImpl implements TokenUserChecker {

    private static final String STATUS_FROZEN = "FROZEN";
    private static final String STATUS_DISABLED = "DISABLED";

    private final SysUserMapper sysUserMapper;

    @Override
    public Long forceLogoutAt(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (STATUS_FROZEN.equals(user.getStatus()) || STATUS_DISABLED.equals(user.getStatus())) {
            throw new BizException(ErrorCode.ACCOUNT_DISABLED);
        }
        return AuthService.toEpochMilli(user.getForceLogoutAt());
    }
}
