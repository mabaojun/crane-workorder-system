package com.crane.modules.staff;

import com.crane.common.exception.BizException;
import com.crane.modules.staff.dto.StaffUpsertRequest;
import com.crane.modules.staff.entity.Staff;
import com.crane.modules.staff.mapper.StaffMapper;
import com.crane.modules.staff.service.StaffService;
import com.crane.modules.system.mapper.SysUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 员工档案服务单元测试：重复手机号、非法岗位、编号生成。
 */
class StaffServiceTest {

    private StaffMapper staffMapper;
    private SysUserMapper sysUserMapper;
    private StaffService staffService;

    @BeforeEach
    void setUp() {
        staffMapper = mock(StaffMapper.class);
        sysUserMapper = mock(SysUserMapper.class);
        staffService = new StaffService(staffMapper, sysUserMapper);
    }

    private StaffUpsertRequest validRequest() {
        StaffUpsertRequest req = new StaffUpsertRequest();
        req.setName("张三");
        req.setPhone("13800000001");
        req.setPosition("DRIVER");
        return req;
    }

    @Test
    void 重复手机号应抛出40004() {
        when(staffMapper.selectCount(any())).thenReturn(1L);
        BizException e = assertThrows(BizException.class, () -> staffService.create(validRequest(), 1L));
        assertEquals(40004, e.getCode());
        assertEquals("手机号已存在", e.getMessage());
    }

    @Test
    void 非法岗位应抛出20008() {
        StaffUpsertRequest req = validRequest();
        req.setPosition("CHEF");
        BizException e = assertThrows(BizException.class, () -> staffService.create(req, 1L));
        assertEquals(20008, e.getCode());
    }

    @Test
    void 员工编号生成非空且符合规则() {
        when(staffMapper.selectCount(any())).thenReturn(0L);
        when(staffMapper.selectOne(any())).thenReturn(null);
        when(staffMapper.insert(any(Staff.class))).thenAnswer(inv -> {
            inv.getArgument(0, Staff.class).setId(1L);
            return 1;
        });

        Staff staff = staffService.create(validRequest(), 1L);

        assertNotNull(staff.getStaffNo());
        assertTrue(staff.getStaffNo().startsWith("YG"));
        // YG + yyyyMMdd(8) + 4 位序号 = 14 位
        assertEquals(14, staff.getStaffNo().length());
        assertEquals("IDLE", staff.getStatus());
    }
}
