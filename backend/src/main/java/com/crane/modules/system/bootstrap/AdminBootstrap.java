package com.crane.modules.system.bootstrap;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crane.modules.system.entity.SysUser;
import com.crane.modules.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 管理员引导：首次启动且系统无任何账号时，创建 ADMIN 账号（密码登录，ACTIVE）。
 * 手机号与密码由环境变量注入（crane.init.admin-phone / admin-password），不硬编码进镜像。
 * 仅在 sys_user 为空时执行，幂等。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;

    @Value("${crane.init.admin-phone}")
    private String adminPhone;

    @Value("${crane.init.admin-password}")
    private String adminPassword;

    @Override
    public void run(ApplicationArguments args) {
        Long count = sysUserMapper.selectCount(new LambdaQueryWrapper<>());
        if (count != null && count > 0) {
            return;
        }
        SysUser admin = new SysUser();
        admin.setPhone(adminPhone);
        admin.setPassword(passwordEncoder.encode(adminPassword));
        admin.setNickName("系统管理员");
        admin.setRoleId(1L);
        admin.setStatus("ACTIVE");
        admin.setBoundAt(LocalDateTime.now());
        sysUserMapper.insert(admin);
        log.info("已创建初始管理员账号，手机号={}（密码来自环境变量）", adminPhone);
    }
}
