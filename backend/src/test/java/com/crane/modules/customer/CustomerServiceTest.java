package com.crane.modules.customer;

import com.crane.common.exception.BizException;
import com.crane.modules.customer.dto.CustomerContactUpsertRequest;
import com.crane.modules.customer.dto.CustomerUpsertRequest;
import com.crane.modules.customer.entity.Customer;
import com.crane.modules.customer.entity.CustomerContact;
import com.crane.modules.customer.mapper.CustomerContactMapper;
import com.crane.modules.customer.mapper.CustomerMapper;
import com.crane.modules.customer.service.CustomerService;
import com.crane.modules.system.entity.SysUser;
import com.crane.modules.system.mapper.SysUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 客户档案服务单元测试：默认联系人唯一切换、联系人手机号全局唯一、创建账号初始密码。
 */
class CustomerServiceTest {

    private CustomerMapper customerMapper;
    private CustomerContactMapper contactMapper;
    private SysUserMapper sysUserMapper;
    private CustomerService customerService;

    @BeforeEach
    void setUp() {
        customerMapper = mock(CustomerMapper.class);
        contactMapper = mock(CustomerContactMapper.class);
        sysUserMapper = mock(SysUserMapper.class);
        customerService = new CustomerService(customerMapper, contactMapper, sysUserMapper,
                new BCryptPasswordEncoder());
    }

    private CustomerContactUpsertRequest contactRequest() {
        CustomerContactUpsertRequest req = new CustomerContactUpsertRequest();
        req.setCustomerId(5L);
        req.setName("李四");
        req.setPhone("13900000002");
        req.setIsDefault(1);
        return req;
    }

    @Test
    void 设为默认联系人时应将同客户其他默认联系人置零() {
        Customer customer = new Customer();
        customer.setId(5L);
        customer.setName("测试客户");
        when(customerMapper.selectById(5L)).thenReturn(customer);

        CustomerContact oldDefault = new CustomerContact();
        oldDefault.setId(10L);
        oldDefault.setCustomerId(5L);
        oldDefault.setName("王五");
        oldDefault.setIsDefault(1);

        // 手机号查重与客户联系人计数均返回 0（新客户首个联系人）
        when(contactMapper.selectCount(any())).thenReturn(0L);
        when(contactMapper.insert(any(CustomerContact.class))).thenAnswer(inv -> {
            inv.getArgument(0, CustomerContact.class).setId(11L);
            return 1;
        });
        when(contactMapper.selectList(any())).thenReturn(List.of(oldDefault));

        CustomerContact created = customerService.createContact(contactRequest(), 1L);

        // 新联系人被置为默认
        ArgumentCaptor<CustomerContact> insertCaptor = ArgumentCaptor.forClass(CustomerContact.class);
        verify(contactMapper).insert(insertCaptor.capture());
        assertEquals(1, insertCaptor.getValue().getIsDefault());
        assertEquals(11L, created.getId());

        // 同事务内旧默认联系人被置 0
        ArgumentCaptor<CustomerContact> updateCaptor = ArgumentCaptor.forClass(CustomerContact.class);
        verify(contactMapper).updateById(updateCaptor.capture());
        assertEquals(10L, updateCaptor.getValue().getId());
        assertEquals(0, updateCaptor.getValue().getIsDefault());
    }

    @Test
    void 联系人手机号重复应抛出40004() {
        Customer customer = new Customer();
        customer.setId(5L);
        when(customerMapper.selectById(5L)).thenReturn(customer);
        when(contactMapper.selectCount(any())).thenReturn(1L);

        BizException e = assertThrows(BizException.class, () -> customerService.createContact(contactRequest(), 1L));
        assertEquals(40004, e.getCode());
        assertEquals("该手机号已登记", e.getMessage());
    }

    /** 创建客户账号并携带合法初始密码时，新账号写入 BCrypt 编码密码 */
    @Test
    void 创建客户账号携带初始密码时应写入BCrypt编码密码() {
        when(customerMapper.selectOne(any())).thenReturn(null);
        when(customerMapper.insert(any(Customer.class))).thenAnswer(inv -> {
            inv.getArgument(0, Customer.class).setId(5L);
            return 1;
        });
        when(sysUserMapper.selectOne(any())).thenReturn(null);

        CustomerUpsertRequest req = new CustomerUpsertRequest();
        req.setName("测试客户");
        req.setType("PERSON");
        req.setPhone("13900000001");
        req.setCreateAccount(true);
        req.setInitialPassword("123456");
        customerService.create(req, 1L);

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(sysUserMapper).insert(captor.capture());
        String encoded = captor.getValue().getPassword();
        assertNotNull(encoded);
        assertTrue(encoded.startsWith("$2"));
    }

    /** 创建企业联系人并同步建号：新账号绑定 customer_id + contact_id，联系人与账号双向回写 */
    @Test
    void 创建联系人可同步创建独立登录账号() {
        Customer customer = new Customer();
        customer.setId(5L);
        when(customerMapper.selectById(5L)).thenReturn(customer);
        when(contactMapper.selectCount(any())).thenReturn(0L);
        when(contactMapper.insert(any(CustomerContact.class))).thenAnswer(inv -> {
            inv.getArgument(0, CustomerContact.class).setId(11L);
            return 1;
        });
        when(contactMapper.selectList(any())).thenReturn(List.of());
        when(sysUserMapper.selectOne(any())).thenReturn(null);
        when(sysUserMapper.insert(any(SysUser.class))).thenAnswer(inv -> {
            inv.getArgument(0, SysUser.class).setId(30L);
            return 1;
        });

        CustomerContactUpsertRequest req = contactRequest();
        req.setCreateAccount(true);
        req.setInitialPassword("abc123");
        customerService.createContact(req, 1L);

        ArgumentCaptor<SysUser> userCaptor = ArgumentCaptor.forClass(SysUser.class);
        verify(sysUserMapper).insert(userCaptor.capture());
        SysUser created = userCaptor.getValue();
        assertEquals("13900000002", created.getPhone());
        assertEquals(3L, created.getRoleId());
        assertEquals("PENDING_BIND", created.getStatus());
        assertEquals(5L, created.getCustomerId());
        assertEquals(11L, created.getContactId());
        assertTrue(created.getPassword().startsWith("$2"));

        // 联系人与账号双向绑定
        ArgumentCaptor<CustomerContact> contactCaptor = ArgumentCaptor.forClass(CustomerContact.class);
        verify(contactMapper).updateById(contactCaptor.capture());
        assertEquals(30L, contactCaptor.getValue().getUserId());
    }

    /** 联系人已有同手机号账号时补建入口只补绑、不新建账号 */
    @Test
    void 已有同手机号账号时创建联系人账号只补绑() {
        CustomerContact contact = new CustomerContact();
        contact.setId(11L);
        contact.setCustomerId(5L);
        contact.setName("李四");
        contact.setPhone("13900000002");
        when(contactMapper.selectById(11L)).thenReturn(contact);
        SysUser existing = new SysUser();
        existing.setId(30L);
        existing.setPhone("13900000002");
        when(sysUserMapper.selectOne(any())).thenReturn(existing);

        customerService.createContactAccount(11L, null, 1L);

        verify(sysUserMapper, never()).insert(any(SysUser.class));
        ArgumentCaptor<SysUser> userCaptor = ArgumentCaptor.forClass(SysUser.class);
        verify(sysUserMapper).updateById(userCaptor.capture());
        assertEquals(11L, userCaptor.getValue().getContactId());
        assertEquals(5L, userCaptor.getValue().getCustomerId());
        ArgumentCaptor<CustomerContact> contactCaptor = ArgumentCaptor.forClass(CustomerContact.class);
        verify(contactMapper).updateById(contactCaptor.capture());
        assertEquals(30L, contactCaptor.getValue().getUserId());
    }

    /** 客户初始密码不足 6 位返回 20002 */
    @Test
    void 客户初始密码过短应抛出20002() {
        when(customerMapper.selectOne(any())).thenReturn(null);
        when(customerMapper.insert(any(Customer.class))).thenAnswer(inv -> {
            inv.getArgument(0, Customer.class).setId(5L);
            return 1;
        });

        CustomerUpsertRequest req = new CustomerUpsertRequest();
        req.setName("测试客户");
        req.setType("PERSON");
        req.setPhone("13900000001");
        req.setCreateAccount(true);
        req.setInitialPassword("123");
        BizException e = assertThrows(BizException.class, () -> customerService.create(req, 1L));

        assertEquals(20002, e.getCode());
        assertEquals("初始密码至少6位", e.getMessage());
    }
}
