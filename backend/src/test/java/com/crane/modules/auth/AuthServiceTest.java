package com.crane.modules.auth;

import com.crane.common.exception.BizException;
import com.crane.common.auth.JwtUtil;
import com.crane.common.auth.TokenUserChecker;
import com.crane.modules.auth.dto.PasswordLoginRequest;
import com.crane.modules.auth.dto.SmsCodeRequest;
import com.crane.modules.auth.dto.SmsLoginRequest;
import com.crane.modules.auth.entity.SmsCode;
import com.crane.modules.auth.mapper.PermissionMapper;
import com.crane.modules.auth.mapper.SmsCodeMapper;
import com.crane.modules.auth.service.AuthService;
import com.crane.modules.system.entity.SysUser;
import com.crane.modules.system.mapper.SysUserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 认证服务单元测试（不连库）：覆盖密码错误、验证码过期、账号未开通、账号冻结与发送频控。
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String PHONE = "13800000001";

    @Mock
    private SysUserMapper sysUserMapper;

    @Mock
    private SmsCodeMapper smsCodeMapper;

    @Mock
    private PermissionMapper permissionMapper;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private TokenUserChecker tokenUserChecker;

    @InjectMocks
    private AuthService authService;

    /** 账号不存在或密码错误均返回 10010 */
    @Test
    void passwordLoginWrongPasswordOrMissingUserThrows10010() {
        SysUser user = user("ACTIVE");
        when(sysUserMapper.selectOne(any())).thenReturn(user, (SysUser) null);
        when(passwordEncoder.matches("bad-password", "bcrypt-hash")).thenReturn(false);

        BizException wrongPassword = assertThrows(BizException.class,
                () -> authService.passwordLogin(passwordRequest("bad-password")));
        assertEquals(10010, wrongPassword.getCode());

        BizException missingUser = assertThrows(BizException.class,
                () -> authService.passwordLogin(passwordRequest("bad-password")));
        assertEquals(10010, missingUser.getCode());
    }

    /** 使用过期验证码登录返回 10009 */
    @Test
    void smsLoginExpiredCodeThrows10009() {
        when(smsCodeMapper.selectOne(any())).thenReturn(smsCode("LOGIN", LocalDateTime.now().minusSeconds(1)));

        BizException e = assertThrows(BizException.class, () -> authService.smsLogin(smsLoginRequest()));
        assertEquals(10009, e.getCode());
    }

    /** 手机号无账号或账号未激活（PENDING_BIND）均返回 10007 */
    @Test
    void smsLoginUnopenedAccountThrows10007() {
        // 每次调用返回全新验证码对象，避免首次校验置 usedAt 后污染后续断言
        when(smsCodeMapper.selectOne(any())).thenAnswer(inv -> validSmsCode());
        when(sysUserMapper.selectOne(any())).thenReturn(null, user("PENDING_BIND"));

        BizException missing = assertThrows(BizException.class, () -> authService.smsLogin(smsLoginRequest()));
        assertEquals(10007, missing.getCode());

        BizException pending = assertThrows(BizException.class, () -> authService.smsLogin(smsLoginRequest()));
        assertEquals(10007, pending.getCode());
    }

    /** 冻结账号登录返回 10005 */
    @Test
    void smsLoginFrozenAccountThrows10005() {
        when(smsCodeMapper.selectOne(any())).thenReturn(validSmsCode());
        when(sysUserMapper.selectOne(any())).thenReturn(user("FROZEN"));

        BizException e = assertThrows(BizException.class, () -> authService.smsLogin(smsLoginRequest()));
        assertEquals(10005, e.getCode());
    }

    /** 60 秒内同手机号同场景重复发送返回 20002 */
    @Test
    void sendSmsCodeTooFrequentThrows20002() {
        SmsCode recent = validSmsCode();
        recent.setCreatedAt(LocalDateTime.now());
        when(smsCodeMapper.selectOne(any())).thenReturn(recent);

        BizException e = assertThrows(BizException.class, () -> authService.sendSmsCode(smsCodeRequest()));
        assertEquals(20002, e.getCode());
        assertEquals("发送过于频繁", e.getMessage());
    }

    private PasswordLoginRequest passwordRequest(String password) {
        PasswordLoginRequest req = new PasswordLoginRequest();
        req.setUsername(PHONE);
        req.setPassword(password);
        return req;
    }

    private SmsLoginRequest smsLoginRequest() {
        SmsLoginRequest req = new SmsLoginRequest();
        req.setPhone(PHONE);
        req.setSmsCode("123456");
        return req;
    }

    private SmsCodeRequest smsCodeRequest() {
        SmsCodeRequest req = new SmsCodeRequest();
        req.setPhone(PHONE);
        req.setScene("LOGIN");
        return req;
    }

    private SysUser user(String status) {
        SysUser user = new SysUser();
        user.setId(1L);
        user.setPhone(PHONE);
        user.setPassword("bcrypt-hash");
        user.setRoleId(2L);
        user.setStatus(status);
        return user;
    }

    private SmsCode validSmsCode() {
        return smsCode("LOGIN", LocalDateTime.now().plusMinutes(5));
    }

    private SmsCode smsCode(String scene, LocalDateTime expiresAt) {
        SmsCode smsCode = new SmsCode();
        smsCode.setId(10L);
        smsCode.setPhone(PHONE);
        smsCode.setScene(scene);
        smsCode.setCode("123456");
        smsCode.setExpiresAt(expiresAt);
        smsCode.setUsedAt(null);
        smsCode.setCreatedAt(LocalDateTime.now().minusMinutes(10));
        return smsCode;
    }
}
