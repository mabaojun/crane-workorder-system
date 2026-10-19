package com.crane.modules.demand.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crane.common.auth.AuthContext;
import com.crane.common.exception.BizException;
import com.crane.common.exception.ErrorCode;
import com.crane.common.result.PageResult;
import com.crane.modules.customer.entity.Customer;
import com.crane.modules.customer.mapper.CustomerMapper;
import com.crane.modules.demand.dto.DemandConvertRequest;
import com.crane.modules.demand.dto.DemandCreateRequest;
import com.crane.modules.demand.dto.DemandReviewRequest;
import com.crane.modules.demand.entity.DemandOrder;
import com.crane.modules.demand.mapper.DemandOrderMapper;
import com.crane.modules.system.entity.SysUser;
import com.crane.modules.system.mapper.SysUserMapper;
import com.crane.modules.system.service.SystemConfigService;
import com.crane.modules.workorder.dto.WorkOrderCreateCommand;
import com.crane.modules.workorder.entity.WorkOrder;
import com.crane.modules.workorder.service.WorkOrderService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 需求单服务：提报（客户小程序 / 管理员代录）、数据范围查询、复核、转工单。
 * 暂存时限读取配置 demand.retention_days（默认 10 天）；状态字面量见 01 §6.3。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DemandService {

    /** 需求单状态合法取值（01 §6.3 锁定） */
    private static final Set<String> STATUSES = Set.of("PENDING", "REVIEWING", "CONVERTED", "REJECTED", "CLOSED");

    /** 需求单来源合法取值（02 §3.3.5） */
    private static final Set<String> SOURCES = Set.of("CUSTOMER_MP", "PHONE", "ADMIN");

    private static final DateTimeFormatter NO_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private static final ObjectMapper JSON = new ObjectMapper();

    /** 任务名称最大长度（超出截断） */
    private static final int TASK_NAME_MAX_LEN = 100;

    private final DemandOrderMapper demandOrderMapper;
    private final SystemConfigService systemConfigService;
    private final SysUserMapper sysUserMapper;
    private final CustomerMapper customerMapper;
    private final WorkOrderService workOrderService;

    /**
     * 提报需求单：demandNo = XQ + yyyyMMdd + 4 位序号；expires_at = now + demand.retention_days；
     * 客户提报时 customerId 强制取登录上下文，联系人姓名取当前用户昵称兜底；status = PENDING。
     */
    @Transactional
    public DemandOrder create(DemandCreateRequest req, Long operatorId) {
        AuthContext ctx = requireLogin();
        String source = resolveSource(req.getSource(), ctx);

        Long customerId;
        if (ctx.isCustomer()) {
            // 客户提报：归属强制取登录上下文，不信任入参
            if (ctx.getCustomerId() == null) {
                throw new BizException(ErrorCode.FORBIDDEN);
            }
            customerId = ctx.getCustomerId();
        } else {
            // 管理员代录：客户可暂缺，填写时校验存在
            customerId = req.getCustomerId();
            if (customerId != null) {
                Customer customer = customerMapper.selectById(customerId);
                if (customer == null) {
                    throw new BizException(ErrorCode.CUSTOMER_NOT_FOUND);
                }
            }
        }

        // 驳回后重提链路：原需求单必须存在且处于 REJECTED
        if (req.getParentDemandId() != null) {
            DemandOrder parent = demandOrderMapper.selectById(req.getParentDemandId());
            if (parent == null) {
                throw new BizException(ErrorCode.DEMAND_NOT_FOUND, "原需求单不存在");
            }
            if (!"REJECTED".equals(parent.getStatus())) {
                throw new BizException(ErrorCode.RESOURCE_STATUS_NOT_ALLOWED, "仅已驳回的需求单可重提");
            }
        }

        DemandOrder demand = new DemandOrder();
        demand.setDemandNo(generateDemandNo());
        demand.setSource(source);
        demand.setCustomerId(customerId);
        demand.setContactId(req.getContactId());
        demand.setContactName(resolveContactName(req.getContactName(), ctx));
        demand.setContactPhone(req.getContactPhone());
        demand.setAddress(req.getAddress());
        demand.setLongitude(req.getLongitude());
        demand.setLatitude(req.getLatitude());
        demand.setDescription(req.getDescription());
        demand.setImageUrls(toJson(req.getImageUrls()));
        demand.setIntendedVehicleId(req.getIntendedVehicleId());
        demand.setCraneTonnage(req.getCraneTonnage());
        demand.setCargoWeight(req.getCargoWeight());
        demand.setEstimatedDays(req.getEstimatedDays());
        demand.setExpectedStartAt(req.getExpectedStartAt());
        demand.setStatus("PENDING");
        demand.setRejectCount(0);
        demand.setParentDemandId(req.getParentDemandId());
        int retentionDays = systemConfigService.getInt("demand.retention_days", 10);
        demand.setExpiresAt(LocalDateTime.now().plusDays(retentionDays));
        demand.setCreatedBy(operatorId);
        demand.setUpdatedBy(operatorId);
        demandOrderMapper.insert(demand);
        return demand;
    }

    /**
     * 分页查询（status 过滤）。数据范围：ADMIN 全部、CUSTOMER 仅本人需求单、DRIVER 不可访问（10003）。
     */
    public PageResult<DemandOrder> page(int pageNum, int pageSize, String status) {
        checkPage(pageNum, pageSize);
        AuthContext ctx = requireLogin();
        if (ctx.isDriver()) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        LambdaQueryWrapper<DemandOrder> wrapper = new LambdaQueryWrapper<DemandOrder>()
                .eq(ctx.isCustomer(), DemandOrder::getCustomerId, ctx.getCustomerId())
                .eq(status != null && !status.isBlank(), DemandOrder::getStatus, status)
                .orderByDesc(DemandOrder::getId);
        long total = demandOrderMapper.selectCount(wrapper);
        int offset = (pageNum - 1) * pageSize;
        List<DemandOrder> list = demandOrderMapper.selectList(wrapper.last("LIMIT " + offset + "," + pageSize));
        return PageResult.of(total, list);
    }

    /**
     * 需求单详情。CUSTOMER 仅可查看自己的需求单，非本人返回 40001，不暴露存在性。
     */
    public DemandOrder getDetail(Long id) {
        AuthContext ctx = requireLogin();
        if (ctx.isDriver()) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        DemandOrder demand = demandOrderMapper.selectById(id);
        if (demand == null) {
            throw new BizException(ErrorCode.DEMAND_NOT_FOUND);
        }
        if (ctx.isCustomer() && !Objects.equals(demand.getCustomerId(), ctx.getCustomerId())) {
            // 不暴露存在性：非本人需求单一律按不存在处理
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return demand;
    }

    /**
     * 复核（仅 ADMIN）：approve=true 状态置 REVIEWING 并记录复核意见；approve=false 驳回并累加 rejectCount。
     */
    @Transactional
    public DemandOrder review(Long id, DemandReviewRequest req, Long operatorId) {
        requireAdmin();
        DemandOrder demand = requireDemand(id);
        if (!"PENDING".equals(demand.getStatus()) && !"REVIEWING".equals(demand.getStatus())) {
            throw new BizException(ErrorCode.RESOURCE_STATUS_NOT_ALLOWED, "当前状态不允许复核");
        }
        if (Boolean.TRUE.equals(req.getApprove())) {
            demand.setStatus("REVIEWING");
        } else {
            demand.setStatus("REJECTED");
            demand.setRejectCount(demand.getRejectCount() == null ? 1 : demand.getRejectCount() + 1);
        }
        if (req.getReviewRemark() != null) {
            demand.setReviewRemark(req.getReviewRemark());
        }
        demand.setUpdatedBy(operatorId);
        demandOrderMapper.updateById(demand);
        return demand;
    }

    /**
     * 转工单（仅 ADMIN）：需求单须处于 PENDING / REVIEWING（否则 40009）；
     * 调 workorder 域 WorkOrderService.create 落工单，成功后需求单置 CONVERTED 并回写 convertedOrderId。
     */
    @Transactional
    public DemandOrder convert(Long id, DemandConvertRequest req, Long operatorId) {
        requireAdmin();
        DemandOrder demand = requireDemand(id);
        if (!"PENDING".equals(demand.getStatus()) && !"REVIEWING".equals(demand.getStatus())) {
            throw new BizException(ErrorCode.DEMAND_NOT_FOUND, "需求单不存在或已关闭");
        }

        WorkOrderCreateCommand cmd = new WorkOrderCreateCommand();
        cmd.setTaskName(resolveTaskName(demand));
        cmd.setSource("DEMAND");
        cmd.setDemandOrderId(demand.getId());
        cmd.setCustomerId(demand.getCustomerId());
        cmd.setContactId(demand.getContactId());
        cmd.setStaffId(req.getStaffId());
        cmd.setVehicleId(req.getVehicleId());
        cmd.setAddress(demand.getAddress());
        cmd.setLongitude(demand.getLongitude());
        cmd.setLatitude(demand.getLatitude());
        cmd.setCargoWeightTon(req.getCargoWeightTon());
        cmd.setCraneTonnage(req.getCraneTonnage());
        // 预计使用时长：转单未填时回落需求单客户自估值
        cmd.setEstimatedDays(req.getEstimatedDays() != null ? req.getEstimatedDays() : demand.getEstimatedDays());
        cmd.setPlanStartAt(req.getPlanStartAt());
        cmd.setRemark(req.getRemark());
        WorkOrder order = workOrderService.create(cmd, operatorId);

        demand.setStatus("CONVERTED");
        demand.setConvertedOrderId(order.getId());
        demand.setUpdatedBy(operatorId);
        demandOrderMapper.updateById(demand);
        return demand;
    }

    /** 需求单编号生成：XQ + yyyyMMdd + 4 位序号，超限时毫秒兜底 */
    private String generateDemandNo() {
        String prefix = "XQ" + LocalDate.now().format(NO_DATE);
        DemandOrder max = demandOrderMapper.selectOne(new LambdaQueryWrapper<DemandOrder>()
                .likeRight(DemandOrder::getDemandNo, prefix)
                .orderByDesc(DemandOrder::getDemandNo)
                .last("LIMIT 1"));
        int seq = 1;
        if (max != null && max.getDemandNo().length() == prefix.length() + 4) {
            seq = Integer.parseInt(max.getDemandNo().substring(prefix.length())) + 1;
        }
        if (seq <= 9999) {
            return prefix + String.format("%04d", seq);
        }
        return prefix + String.format("%04d", (int) (System.currentTimeMillis() % 10000));
    }

    /** 来源解析：客户强制 CUSTOMER_MP；管理员缺省 ADMIN（可显式代录 PHONE） */
    private String resolveSource(String source, AuthContext ctx) {
        if (ctx.isCustomer()) {
            return "CUSTOMER_MP";
        }
        if (source == null || source.isBlank()) {
            return "ADMIN";
        }
        if (!SOURCES.contains(source)) {
            throw new BizException(ErrorCode.ENUM_INVALID, "需求单来源取值非法");
        }
        return source;
    }

    /** 提报人姓名：入参优先，客户提报时取当前账号昵称兜底 */
    private String resolveContactName(String contactName, AuthContext ctx) {
        if (contactName != null && !contactName.isBlank()) {
            return contactName;
        }
        if (ctx.getUserId() != null) {
            SysUser user = sysUserMapper.selectById(ctx.getUserId());
            if (user != null && user.getNickName() != null && !user.getNickName().isBlank()) {
                return user.getNickName();
            }
        }
        return "客户用户";
    }

    /** 任务名称：需求描述截断 100 字符，缺省“吊装任务-需求单编号” */
    private String resolveTaskName(DemandOrder demand) {
        String description = demand.getDescription();
        if (description != null && !description.isBlank()) {
            String trimmed = description.trim();
            return trimmed.length() <= TASK_NAME_MAX_LEN ? trimmed : trimmed.substring(0, TASK_NAME_MAX_LEN);
        }
        return "吊装任务-" + demand.getDemandNo();
    }

    /** 图片列表转 JSON 字符串落库 */
    private String toJson(List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            return null;
        }
        try {
            return JSON.writeValueAsString(imageUrls);
        } catch (JsonProcessingException e) {
            log.error("需求图片序列化失败", e);
            throw new BizException(ErrorCode.SYSTEM_ERROR, "需求图片序列化失败");
        }
    }

    private DemandOrder requireDemand(Long id) {
        DemandOrder demand = demandOrderMapper.selectById(id);
        if (demand == null) {
            throw new BizException(ErrorCode.DEMAND_NOT_FOUND);
        }
        return demand;
    }

    private AuthContext requireLogin() {
        AuthContext ctx = AuthContext.get();
        if (ctx == null) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        return ctx;
    }

    private void requireAdmin() {
        AuthContext ctx = requireLogin();
        if (!ctx.isAdmin()) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
    }

    private void checkPage(int pageNum, int pageSize) {
        if (pageNum < 1 || pageSize < 1 || pageSize > 100) {
            throw new BizException(ErrorCode.PAGE_INVALID);
        }
    }
}
