package com.crane.modules.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crane.common.auth.AuthContext;
import com.crane.common.exception.BizException;
import com.crane.common.exception.ErrorCode;
import com.crane.common.result.ApiResult;
import com.crane.modules.system.dto.ConfigUpdateRequest;
import com.crane.modules.system.entity.SysConfig;
import com.crane.modules.system.entity.SysDict;
import com.crane.modules.system.mapper.SysConfigMapper;
import com.crane.modules.system.mapper.SysDictMapper;
import com.crane.modules.system.service.SystemConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 系统配置与字典接口（API-SYS-001/002/003）。
 * 配置查询与更新仅管理员（10003）；字典查询登录即可读。
 */
@RestController
@RequestMapping("/api/v1/system")
@RequiredArgsConstructor
public class SystemConfigController {

    private final SysConfigMapper sysConfigMapper;
    private final SysDictMapper sysDictMapper;
    private final SystemConfigService systemConfigService;

    /** API-SYS-001 配置项查询：group 缺省返回全部 */
    @GetMapping("/configs")
    public ApiResult<List<SysConfig>> configs(@RequestParam(required = false) String group) {
        requireAdmin();
        LambdaQueryWrapper<SysConfig> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(group)) {
            wrapper.eq(SysConfig::getGroupName, group);
        }
        wrapper.orderByAsc(SysConfig::getId);
        return ApiResult.ok(sysConfigMapper.selectList(wrapper));
    }

    /** API-SYS-002 配置项更新（写穿透进程内缓存） */
    @PutMapping("/configs/{key}")
    public ApiResult<Void> updateConfig(@PathVariable String key,
                                        @Valid @RequestBody ConfigUpdateRequest req) {
        requireAdmin();
        systemConfigService.update(key, req.getConfigValue());
        return ApiResult.ok();
    }

    /** API-SYS-003 字典查询：dictType 缺省返回全部，按 sort 升序 */
    @GetMapping("/dictionaries")
    public ApiResult<List<SysDict>> dictionaries(@RequestParam(required = false) String dictType) {
        LambdaQueryWrapper<SysDict> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(dictType)) {
            wrapper.eq(SysDict::getDictType, dictType);
        }
        wrapper.orderByAsc(SysDict::getSort);
        return ApiResult.ok(sysDictMapper.selectList(wrapper));
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
