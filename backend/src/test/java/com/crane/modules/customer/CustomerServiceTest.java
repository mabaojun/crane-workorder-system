package com.crane.modules.customer;

import com.crane.common.exception.BizException;
import com.crane.modules.customer.dto.CustomerContactUpsertRequest;
import com.crane.modules.customer.entity.Customer;
import com.crane.modules.customer.entity.CustomerContact;
import com.crane.modules.customer.mapper.CustomerContactMapper;
import com.crane.modules.customer.mapper.CustomerMapper;
import com.crane.modules.customer.service.CustomerService;
import com.crane.modules.system.mapper.SysUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 客户档案服务单元测试：默认联系人唯一切换、联系人手机号全局唯一。
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
        customerService = new CustomerService(customerMapper, contactMapper, sysUserMapper);
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
}
