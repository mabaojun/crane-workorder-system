package com.crane.modules.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crane.common.auth.AuthContext;
import com.crane.common.exception.BizException;
import com.crane.common.exception.ErrorCode;
import com.crane.common.result.ApiResult;
import com.crane.common.result.PageResult;
import com.crane.modules.system.dto.PasswordResetRequest;
import com.crane.modules.system.dto.UserStatusRequest;
import com.crane.modules.system.entity.SysUser;
import com.crane.modules.system.mapper.SysUserMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 账号管理接口（iam）。全部仅管理员可用，非 ADMIN 返回 10003。
 * 角色 id 固定映射（sys_role 种子数据）：1=ADMIN、2=DRIVER、3=CUSTOMER。
 * 分页采用 COUNT + LIMIT 手工实现，不依赖分页拦截器配置。
 */
@RestController
@RequestMapping("/api/v1/iam/users")
@RequiredArgsConstructor
public class IamUserController {

    /** roleCode 到角色 id 的固定映射 */
    private static final Map<String, Long> ROLE_ID_BY_CODE = Map.of("ADMIN", 1L, "DRIVER", 2L, "CUSTOMER", 3L);

    /** 账号状态字面（01 §6.3） */
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_FROZEN = "FROZEN";
    private static final String STATUS_DISABLED = "DISABLED";

    /** 分页上限（03 §1.4） */
    private static final int MAX_PAGE_SIZE = 100;

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;

    /** 分页列出账号，支持 roleCode 过滤（按 roleId 定位）；password 不出参 */
    @GetMapping
    public ApiResult<PageResult<SysUser>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) String roleCode) {
        requireAdmin();
        if (pageNum == null || pageNum < 1 || pageSize == null || pageSize < 1) {
            throw new BizException(ErrorCode.PAGE_INVALID);
        }
        int size = Math.min(pageSize, MAX_PAGE_SIZE);
        long offset = (long) (pageNum - 1) * size;
        long total = sysUserMapper.selectCount(buildFilter(roleCode));
        if (total <= 0) {
            return ApiResult.ok(PageResult.of(0, List.of()));
        }
        List<SysUser> records = sysUserMapper.selectList(buildFilter(roleCode)
                .orderByDesc(SysUser::getId)
                .last("LIMIT " + offset + ", " + size));
        records.forEach(u -> u.setPassword(null));
        return ApiResult.ok(PageResult.of(total, records));
    }

    /** 管理员重置任意账号密码（BCrypt 加密存储），用于密码登录账号 */
    @PutMapping("/{id}/password")
    public ApiResult<Void> resetPassword(@PathVariable Long id,
                                         @Valid @RequestBody PasswordResetRequest req) {
        requireAdmin();
        requireUserExists(id);
        SysUser update = new SysUser();
        update.setId(id);
        update.setPassword(passwordEncoder.encode(req.getPassword()));
        sysUserMapper.updateById(update);
        return ApiResult.ok();
    }

    /** 变更账号状态：冻结 / 停用同时更新强制下线时间戳踢下线 */
    @PutMapping("/{id}/status")
    public ApiResult<Void> updateStatus(@PathVariable Long id,
                                        @Valid @RequestBody UserStatusRequest req) {
        requireAdmin();
        String status = req.getStatus();
        if (!STATUS_ACTIVE.equals(status) && !STATUS_FROZEN.equals(status) && !STATUS_DISABLED.equals(status)) {
            throw new BizException(ErrorCode.ENUM_INVALID, "status 仅支持 ACTIVE、FROZEN、DISABLED");
        }
        requireUserExists(id);
        SysUser update = new SysUser();
        update.setId(id);
        update.setStatus(status);
        if (STATUS_FROZEN.equals(status) || STATUS_DISABLED.equals(status)) {
            update.setForceLogoutAt(LocalDateTime.now());
        }
        sysUserMapper.updateById(update);
        return ApiResult.ok();
    }

    /** 构造过滤条件：roleCode 合法性校验并映射为 roleId */
    private LambdaQueryWrapper<SysUser> buildFilter(String roleCode) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(roleCode)) {
            Long roleId = ROLE_ID_BY_CODE.get(roleCode);
            if (roleId == null) {
                throw new BizException(ErrorCode.ENUM_INVALID, "roleCode 取值非法，仅支持 ADMIN、DRIVER、CUSTOMER");
            }
            wrapper.eq(SysUser::getRoleId, roleId);
        }
        return wrapper;
    }

    private void requireUserExists(Long id) {
        if (sysUserMapper.selectById(id) == null) {
            throw new BizException(ErrorCode.STAFF_NOT_FOUND);
        }
    }

    /** 管理员校验：上下文缺失视为未登录，非 ADMIN 返回 10003 */
    private void requireAdmin() {
        AuthContext ctx = AuthContext.get();
        if (ctx == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        if (!ctx.isAdmin()) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
    }
}
