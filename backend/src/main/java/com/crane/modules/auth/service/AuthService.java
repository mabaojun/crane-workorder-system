package com.crane.modules.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crane.common.auth.JwtUtil;
import com.crane.common.auth.TokenUserChecker;
import com.crane.common.exception.BizException;
import com.crane.common.exception.ErrorCode;
import com.crane.modules.auth.dto.BindPhoneRequest;
import com.crane.modules.auth.dto.LoginResponse;
import com.crane.modules.auth.dto.MeResponse;
import com.crane.modules.auth.dto.PasswordLoginRequest;
import com.crane.modules.auth.dto.SmsCodeRequest;
import com.crane.modules.auth.dto.SmsCodeResponse;
import com.crane.modules.auth.dto.SmsLoginRequest;
import com.crane.modules.auth.dto.WechatLoginRequest;
import com.crane.modules.auth.entity.SmsCode;
import com.crane.modules.auth.mapper.PermissionMapper;
import com.crane.modules.auth.mapper.SmsCodeMapper;
import com.crane.modules.system.entity.SysUser;
import com.crane.modules.system.mapper.SysUserMapper;
import com.crane.modules.system.service.SystemConfigService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 认证域服务（03 §3.1 auth 域）：
 * 密码登录、短信验证码发送与直登、微信登录骨架、手机号绑定、登出、当前用户信息。
 *
 * 说明：/api/v1/auth/** 整体在鉴权拦截器白名单内，其中需登录的接口
 * （bind-phone / logout / me）在本服务内自行解析 Bearer 令牌并做强制下线校验。
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    /** 短信验证码场景：登录 */
    public static final String SCENE_LOGIN = "LOGIN";

    /** 短信验证码场景：绑定手机号 */
    public static final String SCENE_BIND_PHONE = "BIND_PHONE";

    /** 角色 id 固定映射（sys_role 种子数据）：1=ADMIN、2=DRIVER、3=CUSTOMER */
    private static final Map<Long, String> ROLE_CODE_BY_ID = Map.of(1L, "ADMIN", 2L, "DRIVER", 3L, "CUSTOMER");

    /** 角色对应的数据范围 */
    private static final Map<String, String> DATA_SCOPE_BY_ROLE =
            Map.of("ADMIN", "ALL", "DRIVER", "SELF_STAFF", "CUSTOMER", "SELF_CUSTOMER");

    /** 账号状态字面（01 §6.3） */
    private static final String STATUS_PENDING_BIND = "PENDING_BIND";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_FROZEN = "FROZEN";
    private static final String STATUS_DISABLED = "DISABLED";

    private static final int SMS_EXPIRE_SECONDS = 300;
    private static final int SMS_SEND_INTERVAL_SECONDS = 60;

    /** 密码登录开关豁免端：管理后台与大屏不受 auth.password_login_enabled 控制 */
    private static final Set<String> PASSWORD_LOGIN_EXEMPT_CLIENTS = Set.of("admin", "screen");

    /** 密码登录开关配置键 */
    private static final String KEY_PASSWORD_LOGIN_ENABLED = "auth.password_login_enabled";

    private final SysUserMapper sysUserMapper;
    private final SmsCodeMapper smsCodeMapper;
    private final PermissionMapper permissionMapper;
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final TokenUserChecker tokenUserChecker;
    private final SystemConfigService systemConfigService;

    /** 是否为模拟短信通道（MVP 未接真实通道），为 true 时响应中返回 debugCode 供联调 */
    @Value("${crane.sms.mock:true}")
    private boolean smsMock;

    /** 令牌有效期（小时），与 JwtUtil 读取同一配置键 */
    @Value("${crane.jwt.expire-hours:720}")
    private long expireHours;

    /** 账号密码登录（API-AUTH-004）：username 即手机号，未携带客户端标识时按管理后台处理 */
    public LoginResponse passwordLogin(PasswordLoginRequest req) {
        return passwordLogin(req, "admin");
    }

    /**
     * 账号密码登录（含客户端标识）：管理后台（admin）与大屏（screen）不受开关控制；
     * 其余端在 auth.password_login_enabled=false 时拒绝（30015），提示改用验证码登录。
     */
    public LoginResponse passwordLogin(PasswordLoginRequest req, String clientType) {
        checkPasswordLoginEnabled(clientType);
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getPhone, req.getUsername()));
        if (user == null || user.getPassword() == null
                || !passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new BizException(ErrorCode.BAD_CREDENTIALS);
        }
        checkStatusEnabled(user);
        SysUser update = new SysUser();
        update.setId(user.getId());
        update.setLastLoginAt(LocalDateTime.now());
        sysUserMapper.updateById(update);
        return issueToken(user);
    }

    /** 密码登录开关校验：仅豁免 admin / screen 两端 */
    private void checkPasswordLoginEnabled(String clientType) {
        String client = clientType == null || clientType.isBlank() ? "admin" : clientType.trim().toLowerCase();
        if (PASSWORD_LOGIN_EXEMPT_CLIENTS.contains(client)) {
            return;
        }
        if ("false".equalsIgnoreCase(systemConfigService
                .getOrDefault(KEY_PASSWORD_LOGIN_ENABLED, "true").trim())) {
            throw new BizException(ErrorCode.PASSWORD_LOGIN_DISABLED);
        }
    }

    /** 发送短信验证码（API-AUTH-003）：60 秒内同手机号同场景限发一次，MVP 记 MOCK 发送结果 */
    public SmsCodeResponse sendSmsCode(SmsCodeRequest req) {
        if (!SCENE_LOGIN.equals(req.getScene()) && !SCENE_BIND_PHONE.equals(req.getScene())) {
            throw new BizException(ErrorCode.ENUM_INVALID, "scene 取值非法，仅支持 LOGIN 或 BIND_PHONE");
        }
        SmsCode latest = selectLatestSmsCode(req.getPhone(), req.getScene());
        if (latest != null && latest.getCreatedAt() != null
                && latest.getCreatedAt().isAfter(LocalDateTime.now().minusSeconds(SMS_SEND_INTERVAL_SECONDS))) {
            throw new BizException(ErrorCode.PARAM_INVALID, "发送过于频繁");
        }
        String code = String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
        SmsCode smsCode = new SmsCode();
        smsCode.setPhone(req.getPhone());
        smsCode.setScene(req.getScene());
        smsCode.setCode(code);
        smsCode.setExpiresAt(LocalDateTime.now().plusSeconds(SMS_EXPIRE_SECONDS));
        smsCode.setSendCount(1);
        smsCode.setSendResult("MOCK");
        smsCode.setCreatedAt(LocalDateTime.now());
        smsCodeMapper.insert(smsCode);
        SmsCodeResponse resp = new SmsCodeResponse();
        resp.setExpiresIn(SMS_EXPIRE_SECONDS);
        resp.setInterval(SMS_SEND_INTERVAL_SECONDS);
        if (smsMock) {
            // 仅开发联调用：模拟短信通道开启时回显验证码，生产环境配置关闭后不返回
            resp.setDebugCode(code);
        }
        return resp;
    }

    /** 短信验证码直登：校验通过后按手机号匹配账号签发令牌 */
    public LoginResponse smsLogin(SmsLoginRequest req) {
        verifySmsCode(req.getPhone(), SCENE_LOGIN, req.getSmsCode());
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getPhone, req.getPhone()));
        if (user == null || STATUS_PENDING_BIND.equals(user.getStatus())) {
            throw new BizException(ErrorCode.ACCOUNT_NOT_OPENED);
        }
        checkStatusEnabled(user);
        SysUser update = new SysUser();
        update.setId(user.getId());
        update.setLastLoginAt(LocalDateTime.now());
        sysUserMapper.updateById(update);
        return issueToken(user);
    }

    /** 微信授权登录（API-AUTH-001）：MVP 未配置微信凭据，通道暂不开放，结构预留 */
    public LoginResponse wechatLogin(WechatLoginRequest req) {
        throw new BizException(ErrorCode.WECHAT_CODE_INVALID, "微信登录通道未配置");
    }

    /** 绑定手机号并激活账号（API-AUTH-002）：须匹配员工档案或客户预留手机号 */
    public LoginResponse bindPhone(Long userId, BindPhoneRequest req) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        checkStatusEnabled(user);
        verifySmsCode(req.getPhone(), SCENE_BIND_PHONE, req.getSmsCode());
        // 目标手机号已是其他账号则拒绝（sys_user.phone 全局唯一）
        SysUser occupant = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getPhone, req.getPhone()));
        if (occupant != null && !occupant.getId().equals(userId)) {
            throw new BizException(ErrorCode.PHONE_ALREADY_BOUND);
        }
        // 匹配员工档案 / 客户预留手机号，均不匹配视为账号未开通，不允许自助注册
        Long staffId = queryBizId("biz_staff", req.getPhone());
        Long customerId = queryBizId("biz_customer", req.getPhone());
        if (staffId == null && customerId == null) {
            throw new BizException(ErrorCode.ACCOUNT_NOT_OPENED);
        }
        SysUser update = new SysUser();
        update.setId(userId);
        update.setPhone(req.getPhone());
        update.setStaffId(staffId);
        update.setCustomerId(customerId);
        update.setStatus(STATUS_ACTIVE);
        update.setBoundAt(LocalDateTime.now());
        try {
            sysUserMapper.updateById(update);
        } catch (DuplicateKeyException e) {
            // 并发绑定同手机号时由唯一键兜底
            throw new BizException(ErrorCode.PHONE_ALREADY_BOUND);
        }
        user.setStatus(STATUS_ACTIVE);
        return issueToken(user);
    }

    /** 登出：更新强制下线时间戳使旧令牌失效；重复登出幂等成功 */
    public void logout(Long userId) {
        SysUser update = new SysUser();
        update.setId(userId);
        update.setForceLogoutAt(LocalDateTime.now());
        sysUserMapper.updateById(update);
    }

    /** 当前用户信息（API-AUTH-006） */
    public MeResponse me(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        String role = roleCode(user.getRoleId());
        MeResponse resp = new MeResponse();
        resp.setUserId(user.getId());
        resp.setRole(role);
        resp.setName(user.getNickName());
        resp.setPhone(user.getPhone());
        resp.setStaffId(user.getStaffId());
        resp.setCustomerId(user.getCustomerId());
        resp.setDataScope(DATA_SCOPE_BY_ROLE.get(role));
        resp.setPermissions(permissionMapper.selectPermissionCodes(user.getRoleId()));
        return resp;
    }

    /**
     * 从请求中解析当前用户 id（本域白名单接口专用）。
     * 校验顺序：令牌存在性 → 签名与过期 → 强制下线比对（与 AuthInterceptor 口径一致）。
     *
     * @param checkForceLogout 是否校验强制下线；logout 场景传 false 以保证重复登出幂等成功
     */
    public Long resolveUserId(HttpServletRequest request, boolean checkForceLogout) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        Claims claims;
        try {
            claims = jwtUtil.parse(header.substring(7).trim());
        } catch (Exception e) {
            throw new BizException(ErrorCode.TOKEN_INVALID);
        }
        Long userId = Long.valueOf(claims.getSubject());
        if (checkForceLogout) {
            Long forceLogoutAt = tokenUserChecker.forceLogoutAt(userId);
            if (forceLogoutAt != null && claims.getIssuedAt() != null
                    && claims.getIssuedAt().getTime() < forceLogoutAt) {
                throw new BizException(ErrorCode.TOKEN_REVOKED);
            }
        }
        return userId;
    }

    /** 校验最新未使用验证码，通过则置 used_at 防重复使用 */
    private void verifySmsCode(String phone, String scene, String smsCode) {
        SmsCode latest = selectLatestSmsCode(phone, scene);
        if (latest == null || latest.getUsedAt() != null
                || latest.getExpiresAt().isBefore(LocalDateTime.now())
                || !latest.getCode().equals(smsCode)) {
            throw new BizException(ErrorCode.SMS_CODE_INVALID);
        }
        latest.setUsedAt(LocalDateTime.now());
        smsCodeMapper.updateById(latest);
    }

    /** 取该手机号 + 场景最新一条记录（02 §3.2.7 校验规则） */
    private SmsCode selectLatestSmsCode(String phone, String scene) {
        return smsCodeMapper.selectOne(new LambdaQueryWrapper<SmsCode>()
                .eq(SmsCode::getPhone, phone)
                .eq(SmsCode::getScene, scene)
                .orderByDesc(SmsCode::getId)
                .last("LIMIT 1"));
    }

    /** 冻结 / 停用账号拒绝登录与绑定 */
    private void checkStatusEnabled(SysUser user) {
        if (STATUS_FROZEN.equals(user.getStatus()) || STATUS_DISABLED.equals(user.getStatus())) {
            throw new BizException(ErrorCode.ACCOUNT_DISABLED);
        }
    }

    /** 组装登录响应：签发 JWT 并附带角色、权限码 */
    private LoginResponse issueToken(SysUser user) {
        String role = roleCode(user.getRoleId());
        LoginResponse resp = new LoginResponse();
        resp.setTokenType("Bearer");
        resp.setAccessToken(jwtUtil.createToken(user.getId(), role));
        resp.setExpiresIn(expireHours * 3600);
        resp.setRole(role);
        resp.setPermissions(permissionMapper.selectPermissionCodes(user.getRoleId()));
        resp.setBound(STATUS_ACTIVE.equals(user.getStatus()));
        return resp;
    }

    /** 经 JdbcTemplate 查员工 / 客户档案主键，避免跨模块依赖业务实体 */
    private Long queryBizId(String table, String phone) {
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT id FROM " + table + " WHERE phone = ? AND deleted = 0 LIMIT 1",
                Long.class, phone);
        return ids.isEmpty() ? null : ids.get(0);
    }

    /** 角色 id 转角色码，种子角色缺失视为数据异常 */
    private String roleCode(Long roleId) {
        String role = roleId == null ? null : ROLE_CODE_BY_ID.get(roleId);
        if (role == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "角色配置异常：roleId=" + roleId);
        }
        return role;
    }

    /** LocalDateTime 转 epoch millis（强制下线比对用，与拦截器口径一致） */
    static Long toEpochMilli(LocalDateTime dateTime) {
        return dateTime == null ? null : Timestamp.valueOf(dateTime).getTime();
    }
}
