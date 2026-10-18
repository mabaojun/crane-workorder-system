package com.crane.modules.customer.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crane.common.exception.BizException;
import com.crane.common.exception.ErrorCode;
import com.crane.common.result.PageResult;
import com.crane.modules.customer.dto.CustomerContactUpsertRequest;
import com.crane.modules.customer.dto.CustomerUpsertRequest;
import com.crane.modules.customer.entity.Customer;
import com.crane.modules.customer.entity.CustomerContact;
import com.crane.modules.customer.mapper.CustomerContactMapper;
import com.crane.modules.customer.mapper.CustomerMapper;
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
import java.util.List;
import java.util.Set;

/**
 * 客户档案服务：客户与联系人增删改查、编号生成、默认联系人唯一性保证、可选顺带创建客户登录账号。
 * 每客户至少一个联系人、默认联系人唯一为跨行约束，由本服务在保存事务内校验保证（02 §3.3.3）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerService {

    /** 客户类型合法取值（02 §3.3.2） */
    private static final Set<String> TYPES = Set.of("COMPANY", "PERSON");

    private static final DateTimeFormatter NO_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final CustomerMapper customerMapper;
    private final CustomerContactMapper customerContactMapper;
    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;

    /**
     * 分页查询（name 模糊 / phone 精确 / status 过滤），分页参数校验见 03 §1.4。
     */
    public PageResult<Customer> page(int pageNum, int pageSize, String name, String phone, String status) {
        checkPage(pageNum, pageSize);
        LambdaQueryWrapper<Customer> wrapper = new LambdaQueryWrapper<Customer>()
                .like(name != null && !name.isBlank(), Customer::getName, name)
                .eq(phone != null && !phone.isBlank(), Customer::getPhone, phone)
                .eq(status != null && !status.isBlank(), Customer::getStatus, status)
                .orderByDesc(Customer::getId);
        long total = customerMapper.selectCount(wrapper);
        int offset = (pageNum - 1) * pageSize;
        List<Customer> list = customerMapper.selectList(wrapper.last("LIMIT " + offset + "," + pageSize));
        return PageResult.of(total, list);
    }

    /**
     * 创建客户档案：编号 KH + yyyyMMdd + 4 位序号；COMPANY 必须提供统一社会信用代码；
     * 可顺带写入初始联系人与创建客户登录账号。
     */
    @Transactional
    public Customer create(CustomerUpsertRequest req, Long operatorId) {
        checkType(req.getType());
        checkCreditCode(req.getType(), req.getCreditCode());

        Customer customer = new Customer();
        customer.setCustomerNo(generateCustomerNo());
        applyUpsert(customer, req);
        customer.setAuditStatus("PENDING");
        customer.setStatus("ACTIVE");
        customer.setCreatedBy(operatorId);
        customer.setUpdatedBy(operatorId);
        insertCustomer(customer);

        // 初始联系人：首位强制置为默认；后续标记默认的联系人同事务清零其他默认
        if (req.getContacts() != null && !req.getContacts().isEmpty()) {
            for (CustomerContactUpsertRequest contactReq : req.getContacts()) {
                createContactInternal(customer.getId(), contactReq, operatorId);
            }
        }
        if (Boolean.TRUE.equals(req.getCreateAccount())) {
            bindAccount(customer, normalizeInitialPassword(req.getInitialPassword()), operatorId);
        }
        return customer;
    }

    /**
     * 更新客户档案（全量字段，不含审核状态与绑定账号）。
     */
    @Transactional
    public Customer update(Long id, CustomerUpsertRequest req, Long operatorId) {
        Customer customer = customerMapper.selectById(id);
        if (customer == null) {
            throw new BizException(ErrorCode.CUSTOMER_NOT_FOUND);
        }
        checkType(req.getType());
        checkCreditCode(req.getType(), req.getCreditCode());
        applyUpsert(customer, req);
        customer.setUpdatedBy(operatorId);
        customerMapper.updateById(customer);
        return customer;
    }

    /**
     * 逻辑删除客户档案。
     */
    @Transactional
    public void delete(Long id, Long operatorId) {
        Customer customer = customerMapper.selectById(id);
        if (customer == null) {
            throw new BizException(ErrorCode.CUSTOMER_NOT_FOUND);
        }
        customerMapper.deleteById(id);
    }

    /**
     * 联系人列表（默认联系人在前）。
     */
    public List<CustomerContact> listContacts(Long customerId) {
        requireCustomer(customerId);
        return customerContactMapper.selectList(new LambdaQueryWrapper<CustomerContact>()
                .eq(CustomerContact::getCustomerId, customerId)
                .orderByDesc(CustomerContact::getIsDefault)
                .orderByAsc(CustomerContact::getId));
    }

    /**
     * 创建联系人：手机号全局唯一；每客户默认联系人唯一（设默认时同事务清零其他）。
     */
    @Transactional
    public CustomerContact createContact(CustomerContactUpsertRequest req, Long operatorId) {
        requireCustomer(req.getCustomerId());
        return createContactInternal(req.getCustomerId(), req, operatorId);
    }

    /**
     * 更新联系人：手机号全局唯一（排除自身）；默认标记只可移动不可移除，保证每客户恒有默认。
     */
    @Transactional
    public CustomerContact updateContact(Long contactId, CustomerContactUpsertRequest req, Long operatorId) {
        CustomerContact contact = customerContactMapper.selectById(contactId);
        if (contact == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "联系人不存在");
        }
        checkContactPhoneUnique(req.getPhone(), contactId);
        contact.setName(req.getName());
        contact.setGender(req.getGender());
        contact.setPosition(req.getPosition());
        contact.setPhone(req.getPhone());
        contact.setWechat(req.getWechat());
        contact.setEmail(req.getEmail());
        contact.setRemark(req.getRemark());
        if (req.getIsDefault() != null && req.getIsDefault() == 1) {
            contact.setIsDefault(1);
        }
        contact.setUpdatedBy(operatorId);
        customerContactMapper.updateById(contact);
        if (contact.getIsDefault() != null && contact.getIsDefault() == 1 && req.getIsDefault() != null && req.getIsDefault() == 1) {
            clearOtherDefaults(contact.getCustomerId(), contactId);
        }
        return contact;
    }

    /**
     * 逻辑删除联系人；若删除的是默认联系人且同客户仍有其他联系人，则将最早一位补位为默认。
     */
    @Transactional
    public void deleteContact(Long contactId, Long operatorId) {
        CustomerContact contact = customerContactMapper.selectById(contactId);
        if (contact == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "联系人不存在");
        }
        boolean wasDefault = contact.getIsDefault() != null && contact.getIsDefault() == 1;
        customerContactMapper.deleteById(contactId);
        if (wasDefault) {
            List<CustomerContact> remain = customerContactMapper.selectList(new LambdaQueryWrapper<CustomerContact>()
                    .eq(CustomerContact::getCustomerId, contact.getCustomerId())
                    .orderByAsc(CustomerContact::getId)
                    .last("LIMIT 1"));
            if (!remain.isEmpty()) {
                markDefault(remain.get(0).getId());
            }
        }
    }

    private CustomerContact createContactInternal(Long customerId, CustomerContactUpsertRequest req, Long operatorId) {
        checkContactPhoneUnique(req.getPhone(), null);
        CustomerContact contact = new CustomerContact();
        contact.setCustomerId(customerId);
        contact.setName(req.getName());
        contact.setGender(req.getGender());
        contact.setPosition(req.getPosition());
        contact.setPhone(req.getPhone());
        contact.setWechat(req.getWechat());
        contact.setEmail(req.getEmail());
        contact.setRemark(req.getRemark());
        boolean first = countContacts(customerId) == 0;
        contact.setIsDefault(first || (req.getIsDefault() != null && req.getIsDefault() == 1) ? 1 : 0);
        contact.setCreatedBy(operatorId);
        contact.setUpdatedBy(operatorId);
        try {
            customerContactMapper.insert(contact);
        } catch (DuplicateKeyException e) {
            // 手机号唯一索引兜底，与先查语义一致
            throw new BizException(ErrorCode.RESOURCE_STATUS_NOT_ALLOWED, "该手机号已登记");
        }
        if (contact.getIsDefault() == 1) {
            clearOtherDefaults(customerId, contact.getId());
        }
        return contact;
    }

    /** 将同客户其他默认联系人置 0，保证默认联系人唯一（同事务） */
    private void clearOtherDefaults(Long customerId, Long excludeContactId) {
        List<CustomerContact> others = customerContactMapper.selectList(new LambdaQueryWrapper<CustomerContact>()
                .eq(CustomerContact::getCustomerId, customerId)
                .eq(CustomerContact::getIsDefault, 1)
                .ne(CustomerContact::getId, excludeContactId));
        for (CustomerContact other : others) {
            other.setIsDefault(0);
            customerContactMapper.updateById(other);
        }
    }

    private void markDefault(Long contactId) {
        CustomerContact contact = customerContactMapper.selectById(contactId);
        if (contact != null) {
            contact.setIsDefault(1);
            customerContactMapper.updateById(contact);
            clearOtherDefaults(contact.getCustomerId(), contactId);
        }
    }

    /**
     * 创建或关联客户登录账号：手机号取默认联系人手机号（缺省取客户主号码）；
     * 已有同手机号账号直接关联，否则新建（roleId=3、PENDING_BIND），并双向回写关联。
     * 初始密码仅在新建账号时写入（BCrypt 编码）。
     */
    private void bindAccount(Customer customer, String initialPassword, Long operatorId) {
        String phone = customer.getPhone();
        CustomerContact defaultContact = customerContactMapper.selectOne(new LambdaQueryWrapper<CustomerContact>()
                .eq(CustomerContact::getCustomerId, customer.getId())
                .eq(CustomerContact::getIsDefault, 1)
                .last("LIMIT 1"));
        if (defaultContact != null && defaultContact.getPhone() != null && !defaultContact.getPhone().isBlank()) {
            phone = defaultContact.getPhone();
        }
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getPhone, phone));
        if (user == null) {
            user = new SysUser();
            user.setPhone(phone);
            user.setNickName(customer.getName());
            if (initialPassword != null) {
                user.setPassword(passwordEncoder.encode(initialPassword));
            }
            user.setRoleId(3L);
            user.setStatus("PENDING_BIND");
            user.setCreatedBy(operatorId);
            user.setUpdatedBy(operatorId);
            sysUserMapper.insert(user);
        }
        if (user.getCustomerId() == null || !user.getCustomerId().equals(customer.getId())) {
            user.setCustomerId(customer.getId());
            user.setUpdatedBy(operatorId);
            sysUserMapper.updateById(user);
        }
        customer.setUserId(user.getId());
        customerMapper.updateById(customer);
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

    private String generateCustomerNo() {
        String prefix = "KH" + LocalDate.now().format(NO_DATE);
        Customer max = customerMapper.selectOne(new LambdaQueryWrapper<Customer>()
                .likeRight(Customer::getCustomerNo, prefix)
                .orderByDesc(Customer::getCustomerNo)
                .last("LIMIT 1"));
        int seq = 1;
        if (max != null && max.getCustomerNo().length() == prefix.length() + 4) {
            seq = Integer.parseInt(max.getCustomerNo().substring(prefix.length())) + 1;
        }
        if (seq <= 9999) {
            return prefix + String.format("%04d", seq);
        }
        return prefix + String.format("%04d", (int) (System.currentTimeMillis() % 10000));
    }

    private void insertCustomer(Customer customer) {
        try {
            customerMapper.insert(customer);
        } catch (DuplicateKeyException e) {
            customer.setCustomerNo("KH" + LocalDate.now().format(NO_DATE)
                    + String.format("%04d", (int) (System.currentTimeMillis() % 10000)));
            customerMapper.insert(customer);
        }
    }

    private void applyUpsert(Customer customer, CustomerUpsertRequest req) {
        customer.setName(req.getName());
        customer.setType(req.getType());
        customer.setCreditCode(req.getCreditCode());
        customer.setIndustry(req.getIndustry());
        customer.setAddress(req.getAddress());
        customer.setPhone(req.getPhone());
        customer.setRemark(req.getRemark());
    }

    private void requireCustomer(Long customerId) {
        if (customerId == null || customerMapper.selectById(customerId) == null) {
            throw new BizException(ErrorCode.CUSTOMER_NOT_FOUND);
        }
    }

    private void checkType(String type) {
        if (!TYPES.contains(type)) {
            throw new BizException(ErrorCode.ENUM_INVALID, "客户类型取值非法：仅允许 COMPANY、PERSON");
        }
    }

    private void checkCreditCode(String type, String creditCode) {
        if ("COMPANY".equals(type) && (creditCode == null || creditCode.isBlank())) {
            throw new BizException(ErrorCode.PARAM_MISSING, "公司客户必须提供统一社会信用代码");
        }
    }

    private void checkContactPhoneUnique(String phone, Long excludeId) {
        Long count = customerContactMapper.selectCount(new LambdaQueryWrapper<CustomerContact>()
                .eq(CustomerContact::getPhone, phone)
                .ne(excludeId != null, CustomerContact::getId, excludeId));
        if (count != null && count > 0) {
            throw new BizException(ErrorCode.RESOURCE_STATUS_NOT_ALLOWED, "该手机号已登记");
        }
    }

    private long countContacts(Long customerId) {
        Long count = customerContactMapper.selectCount(new LambdaQueryWrapper<CustomerContact>()
                .eq(CustomerContact::getCustomerId, customerId));
        return count == null ? 0 : count;
    }

    private void checkPage(int pageNum, int pageSize) {
        if (pageNum < 1 || pageSize < 1 || pageSize > 100) {
            throw new BizException(ErrorCode.PAGE_INVALID);
        }
    }
}
