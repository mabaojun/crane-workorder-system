package com.crane.modules.staff.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crane.common.exception.BizException;
import com.crane.common.exception.ErrorCode;
import com.crane.common.result.PageResult;
import com.crane.modules.staff.dto.StaffUpsertRequest;
import com.crane.modules.staff.entity.Staff;
import com.crane.modules.staff.mapper.StaffMapper;
import com.crane.modules.system.entity.SysUser;
import com.crane.modules.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 员工档案服务：档案增删改查、编号生成、可选顺带创建登录账号。
 * 离职员工以 status = RESIGNED 表达，逻辑删除保留档案。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StaffService {

    /** 员工岗位合法取值（02 §3.3.1） */
    private static final Set<String> POSITIONS = Set.of("DRIVER", "EMPLOYEE");

    /** 员工状态合法取值（02 §3.3.1） */
    private static final Set<String> STATUSES = Set.of("IDLE", "BUSY", "AWAY", "RESIGNED");

    /** 管理员 / 司机角色 id（sys_role 种子数据） */
    private static final long ADMIN_ROLE_ID = 1L;
    private static final long DRIVER_ROLE_ID = 2L;

    private static final DateTimeFormatter NO_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final StaffMapper staffMapper;
    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;

    /**
     * 分页查询（name 模糊 / phone 精确 / status 过滤）。
     * 分页参数校验见 03 §1.4：pageNum 从 1 开始，pageSize 默认 20 上限 100，越界或非正数返回 20003。
     */
    public PageResult<Staff> page(int pageNum, int pageSize, String name, String phone, String status) {
        checkPage(pageNum, pageSize);
        LambdaQueryWrapper<Staff> wrapper = new LambdaQueryWrapper<Staff>()
                .like(name != null && !name.isBlank(), Staff::getName, name)
                .eq(phone != null && !phone.isBlank(), Staff::getPhone, phone)
                .eq(status != null && !status.isBlank(), Staff::getStatus, status)
                .orderByDesc(Staff::getId);
        long total = staffMapper.selectCount(wrapper);
        int offset = (pageNum - 1) * pageSize;
        List<Staff> list = staffMapper.selectList(wrapper.last("LIMIT " + offset + "," + pageSize));
        fillAdminRole(list);
        return PageResult.of(total, list);
    }

    /**
     * 创建员工档案；createAccount=true 或 adminRole=true 时顺带创建 / 关联登录账号。
     * 司机可同时拥有管理员附加角色（一人多角色），登录后在小程序选择身份。
     */
    @Transactional
    public Staff create(StaffUpsertRequest req, Long operatorId) {
        checkPosition(req.getPosition());
        checkStatus(req.getStatus());
        checkPhoneUnique(req.getPhone(), null);
        checkIdCardUnique(req.getIdCard(), null);

        Staff staff = new Staff();
        staff.setStaffNo(generateStaffNo());
        applyUpsert(staff, req);
        staff.setStatus(req.getStatus() == null || req.getStatus().isBlank() ? "IDLE" : req.getStatus());
        staff.setCreatedBy(operatorId);
        staff.setUpdatedBy(operatorId);
        insertStaff(staff);

        boolean admin = Boolean.TRUE.equals(req.getAdminRole());
        if (Boolean.TRUE.equals(req.getCreateAccount()) || admin) {
            bindAccount(staff, req.getName(), req.getPhone(),
                    normalizeInitialPassword(req.getInitialPassword()), operatorId, admin);
        }
        staff.setAdminRole(admin);
        return staff;
    }

    /**
     * 更新员工档案（全量字段）。更新登录账号侧姓名时同步昵称，保持双向关联一致。
     */
    @Transactional
    public Staff update(Long id, StaffUpsertRequest req, Long operatorId) {
        Staff staff = staffMapper.selectById(id);
        if (staff == null) {
            throw new BizException(ErrorCode.STAFF_NOT_FOUND);
        }
        checkPosition(req.getPosition());
        checkStatus(req.getStatus());
        checkPhoneUnique(req.getPhone(), id);
        checkIdCardUnique(req.getIdCard(), id);
        applyUpsert(staff, req);
        staff.setUpdatedBy(operatorId);
        staffMapper.updateById(staff);

        // 同步登录账号昵称与预留手机号，保证绑定手机号与档案一致
        SysUser user = staff.getUserId() == null ? null : sysUserMapper.selectById(staff.getUserId());
        if (user != null) {
            user.setNickName(staff.getName());
            user.setPhone(staff.getPhone());
            user.setUpdatedBy(operatorId);
            sysUserMapper.updateById(user);
            // 管理员角色授予 / 回收（司机可一人多角色，主角色保持司机）
            if (Boolean.TRUE.equals(req.getAdminRole())) {
                grantAdminRole(user, operatorId);
            } else if (req.getAdminRole() != null) {
                revokeAdminRole(user, operatorId);
            }
        } else if (Boolean.TRUE.equals(req.getAdminRole())) {
            // 尚未开通账号的员工直接设为管理员时，顺带创建账号
            bindAccount(staff, staff.getName(), staff.getPhone(),
                    normalizeInitialPassword(req.getInitialPassword()), operatorId, true);
        }
        staff.setAdminRole(Boolean.TRUE.equals(req.getAdminRole()));
        return staff;
    }

    /**
     * 逻辑删除员工档案（不物理删，离职档案保留）。
     */
    @Transactional
    public void delete(Long id, Long operatorId) {
        Staff staff = staffMapper.selectById(id);
        if (staff == null) {
            throw new BizException(ErrorCode.STAFF_NOT_FOUND);
        }
        staffMapper.deleteById(id);
    }

    /**
     * 员工编号生成：YG + yyyyMMdd + 4 位序号；并发冲突时以时间戳兜底重试一次。
     */
    private String generateStaffNo() {
        String prefix = "YG" + LocalDate.now().format(NO_DATE);
        Staff max = staffMapper.selectOne(new LambdaQueryWrapper<Staff>()
                .likeRight(Staff::getStaffNo, prefix)
                .orderByDesc(Staff::getStaffNo)
                .last("LIMIT 1"));
        int seq = 1;
        if (max != null && max.getStaffNo().length() == prefix.length() + 4) {
            seq = Integer.parseInt(max.getStaffNo().substring(prefix.length())) + 1;
        }
        if (seq <= 9999) {
            return prefix + String.format("%04d", seq);
        }
        // 序号超限兜底：取毫秒后四位，仍有唯一索引兜底
        return prefix + String.format("%04d", (int) (System.currentTimeMillis() % 10000));
    }

    private void insertStaff(Staff staff) {
        try {
            staffMapper.insert(staff);
        } catch (DuplicateKeyException e) {
            // 唯一键冲突兜底：时间戳后四位重新生成一次
            staff.setStaffNo("YG" + LocalDate.now().format(NO_DATE)
                    + String.format("%04d", (int) (System.currentTimeMillis() % 10000)));
            staffMapper.insert(staff);
        }
    }

    /**
     * 创建或关联登录账号：已有同手机号账号直接关联，否则新建（司机主角色、PENDING_BIND），
     * 并双向回写 staff.userId 与 sys_user.staffId。初始密码仅在新建账号时写入（BCrypt 编码）。
     * admin=true 时赋予管理员角色：司机保留司机主角色并追加管理员附加角色；其他岗位以管理员为主角色。
     */
    private void bindAccount(Staff staff, String name, String phone, String initialPassword,
                             Long operatorId, boolean admin) {
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getPhone, phone));
        if (user == null) {
            user = new SysUser();
            user.setPhone(phone);
            user.setNickName(name);
            if (initialPassword != null) {
                user.setPassword(passwordEncoder.encode(initialPassword));
            }
            if (admin && !"DRIVER".equals(staff.getPosition())) {
                user.setRoleId(ADMIN_ROLE_ID);
            } else {
                user.setRoleId(DRIVER_ROLE_ID);
                if (admin) {
                    user.setAdditionalRoleIds(String.valueOf(ADMIN_ROLE_ID));
                }
            }
            user.setStatus("PENDING_BIND");
            user.setCreatedBy(operatorId);
            user.setUpdatedBy(operatorId);
            sysUserMapper.insert(user);
        } else if (admin) {
            grantAdminRole(user, operatorId);
        }
        if (user.getStaffId() == null || !user.getStaffId().equals(staff.getId())) {
            user.setStaffId(staff.getId());
            user.setUpdatedBy(operatorId);
            sysUserMapper.updateById(user);
        }
        staff.setUserId(user.getId());
        staffMapper.updateById(staff);
    }

    /** 批量回填管理员标记（列表展示「管理员」角色） */
    private void fillAdminRole(List<Staff> list) {
        List<Long> userIds = list.stream()
                .map(Staff::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (userIds.isEmpty()) {
            return;
        }
        Map<Long, SysUser> users = sysUserMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity()));
        for (Staff staff : list) {
            SysUser user = staff.getUserId() == null ? null : users.get(staff.getUserId());
            staff.setAdminRole(user != null && hasAdminRole(user));
        }
    }

    /** 是否拥有管理员角色（主角色或附加角色） */
    private boolean hasAdminRole(SysUser user) {
        return Long.valueOf(ADMIN_ROLE_ID).equals(user.getRoleId())
                || parseAdditionalRoles(user).contains(ADMIN_ROLE_ID);
    }

    /** 追加管理员附加角色；已是管理员则幂等跳过 */
    private void grantAdminRole(SysUser user, Long operatorId) {
        if (hasAdminRole(user)) {
            return;
        }
        List<Long> extras = parseAdditionalRoles(user);
        extras.add(ADMIN_ROLE_ID);
        user.setAdditionalRoleIds(joinRoleIds(extras));
        user.setUpdatedBy(operatorId);
        sysUserMapper.updateById(user);
    }

    /** 回收管理员角色；若管理员为主角色则降级为司机，避免账号失去可用身份 */
    private void revokeAdminRole(SysUser user, Long operatorId) {
        List<Long> extras = parseAdditionalRoles(user);
        boolean changed = extras.remove(ADMIN_ROLE_ID);
        if (Long.valueOf(ADMIN_ROLE_ID).equals(user.getRoleId())) {
            user.setRoleId(DRIVER_ROLE_ID);
            changed = true;
        }
        if (changed) {
            user.setAdditionalRoleIds(extras.isEmpty() ? null : joinRoleIds(extras));
            user.setUpdatedBy(operatorId);
            sysUserMapper.updateById(user);
        }
    }

    private List<Long> parseAdditionalRoles(SysUser user) {
        List<Long> ids = new ArrayList<>();
        if (user.getAdditionalRoleIds() == null || user.getAdditionalRoleIds().isBlank()) {
            return ids;
        }
        for (String part : user.getAdditionalRoleIds().split(",")) {
            if (part.isBlank()) {
                continue;
            }
            try {
                ids.add(Long.valueOf(part.trim()));
            } catch (NumberFormatException ignored) {
                // 脏数据忽略，避免影响角色授予
            }
        }
        return ids;
    }

    private String joinRoleIds(List<Long> ids) {
        return ids.stream().distinct().map(String::valueOf).collect(Collectors.joining(","));
    }

    /** 初始密码校验：提供时须不少于 6 位，否则返回 20002；空值视为未设置 */
    private String normalizeInitialPassword(String initialPassword) {
        if (initialPassword == null || initialPassword.isBlank()) {
            return null;
        }
        if (initialPassword.length() < 6) {
            throw new BizException(ErrorCode.PARAM_INVALID, "初始密码至少6位");
        }
        return initialPassword;
    }

    private void applyUpsert(Staff staff, StaffUpsertRequest req) {
        staff.setName(req.getName());
        staff.setPhone(req.getPhone());
        staff.setIdCard(req.getIdCard());
        staff.setPosition(req.getPosition());
        if (req.getStatus() != null && !req.getStatus().isBlank()) {
            staff.setStatus(req.getStatus());
        }
        staff.setLicenseType(req.getLicenseType());
        staff.setSpecialCertNo(req.getSpecialCertNo());
        staff.setSpecialCertPhotoUrl(req.getSpecialCertPhotoUrl());
        staff.setEmergencyName(req.getEmergencyName());
        staff.setEmergencyPhone(req.getEmergencyPhone());
        staff.setHireDate(req.getHireDate());
        staff.setRemark(req.getRemark());
    }

    private void checkPosition(String position) {
        if (!POSITIONS.contains(position)) {
            throw new BizException(ErrorCode.ENUM_INVALID, "岗位取值非法：仅允许 DRIVER、EMPLOYEE");
        }
    }

    private void checkStatus(String status) {
        if (status != null && !status.isBlank() && !STATUSES.contains(status)) {
            throw new BizException(ErrorCode.ENUM_INVALID, "员工状态取值非法");
        }
    }

    private void checkPhoneUnique(String phone, Long excludeId) {
        Long count = staffMapper.selectCount(new LambdaQueryWrapper<Staff>()
                .eq(Staff::getPhone, phone)
                .ne(excludeId != null, Staff::getId, excludeId));
        if (count != null && count > 0) {
            throw new BizException(ErrorCode.RESOURCE_STATUS_NOT_ALLOWED, "手机号已存在");
        }
    }

    private void checkIdCardUnique(String idCard, Long excludeId) {
        if (idCard == null || idCard.isBlank()) {
            return;
        }
        Long count = staffMapper.selectCount(new LambdaQueryWrapper<Staff>()
                .eq(Staff::getIdCard, idCard)
                .ne(excludeId != null, Staff::getId, excludeId));
        if (count != null && count > 0) {
            throw new BizException(ErrorCode.RESOURCE_STATUS_NOT_ALLOWED, "身份证号已存在");
        }
    }

    private void checkPage(int pageNum, int pageSize) {
        if (pageNum < 1 || pageSize < 1 || pageSize > 100) {
            throw new BizException(ErrorCode.PAGE_INVALID);
        }
    }
}
