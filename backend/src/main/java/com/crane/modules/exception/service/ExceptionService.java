package com.crane.modules.exception.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.crane.common.auth.AuthContext;
import com.crane.common.exception.BizException;
import com.crane.common.exception.ErrorCode;
import com.crane.common.result.PageResult;
import com.crane.modules.exception.dto.ExceptionReplyRequest;
import com.crane.modules.exception.dto.ExceptionReportRequest;
import com.crane.modules.exception.entity.ApprovalRecord;
import com.crane.modules.exception.entity.ExceptionRecord;
import com.crane.modules.exception.mapper.ApprovalRecordMapper;
import com.crane.modules.exception.mapper.ExceptionRecordMapper;
import com.crane.modules.workorder.entity.WorkOrder;
import com.crane.modules.workorder.mapper.WorkOrderMapper;
import com.crane.modules.workorder.service.WorkOrderStateMachine;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 异常上报与批复服务（API-EXC-001 ~ API-EXC-004）。
 * 上报即经状态机将工单转 SUSPENDED；未批复期间司机不可签到 / 签退（打卡域按 status = PENDING 判定阻断）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExceptionService {

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_APPROVED = "APPROVED";

    private static final String ORDER_STATUS_IN_PROGRESS = "IN_PROGRESS";

    private static final Set<String> EXCEPTION_TYPES = Set.of("RAIN", "PARTY_STOP", "VEHICLE_FAULT", "CUSTOM");
    private static final Set<String> INSTRUCTIONS = Set.of("CONTINUE", "SUSPEND", "TERMINATE");

    private final ExceptionRecordMapper exceptionRecordMapper;
    private final ApprovalRecordMapper approvalRecordMapper;
    private final WorkOrderMapper workOrderMapper;
    private final WorkOrderStateMachine workOrderStateMachine;
    private final ObjectMapper objectMapper;

    /**
     * 异常上报（API-EXC-001，司机本人）。
     * 校验链：参数 -> 工单存在且属本人 -> 状态 IN_PROGRESS -> 无未批复异常（避免重复暂停）。
     * 成功后工单经状态机转 SUSPENDED。
     */
    @Transactional
    public Map<String, Object> report(ExceptionReportRequest req, Long operatorId) {
        AuthContext ctx = requireDriver();
        if (req.getOrderId() == null) {
            throw new BizException(ErrorCode.PARAM_MISSING, "工单 id 必填");
        }
        if (req.getType() == null || !EXCEPTION_TYPES.contains(req.getType())) {
            throw new BizException(ErrorCode.ENUM_INVALID, "异常类型取值非法");
        }
        if ("CUSTOM".equals(req.getType()) && (req.getTypeLabel() == null || req.getTypeLabel().isBlank())) {
            throw new BizException(ErrorCode.PARAM_MISSING, "自定义异常必须填写原因标签");
        }
        if (req.getDescription() == null || req.getDescription().isBlank()) {
            throw new BizException(ErrorCode.PARAM_MISSING, "异常描述必填");
        }
        WorkOrder order = workOrderMapper.selectById(req.getOrderId());
        if (order == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "工单不存在");
        }
        if (!Objects.equals(order.getStaffId(), ctx.getStaffId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅可对本人执行工单上报异常");
        }
        if (!ORDER_STATUS_IN_PROGRESS.equals(order.getStatus())) {
            throw new BizException(ErrorCode.ORDER_STATUS_NOT_ALLOWED, "仅进行中的工单可上报异常");
        }
        if (hasPendingException(order.getId())) {
            throw new BizException(ErrorCode.PENDING_EXCEPTION_EXISTS, "该工单已存在未批复异常");
        }

        LocalDateTime now = LocalDateTime.now();
        ExceptionRecord record = new ExceptionRecord();
        record.setOrderId(order.getId());
        record.setStaffId(order.getStaffId());
        record.setType(req.getType());
        record.setTypeLabel("CUSTOM".equals(req.getType()) ? req.getTypeLabel() : null);
        record.setDescription(req.getDescription());
        record.setImageUrls(toJson(req.getImageUrls()));
        record.setStatus(STATUS_PENDING);
        record.setOrderStatusBefore(order.getStatus());
        record.setIsPublicToCustomer(1);
        record.setReportedAt(now);
        exceptionRecordMapper.insert(record);

        workOrderStateMachine.transition(order, "SUSPENDED", ctx.getStaffId(), "异常上报: " + req.getType());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("exceptionId", record.getId());
        data.put("workOrderStatus", "SUSPENDED");
        data.put("createdAt", record.getReportedAt());
        return data;
    }

    /**
     * 异常列表（API-EXC-002）。管理员全量、司机本人、客户仅本人工单且对客户公开的记录。
     */
    @Transactional(readOnly = true)
    public PageResult<ExceptionRecord> list(String status, Long orderId, Long staffId,
                                            int pageNum, int pageSize) {
        ensurePage(pageNum, pageSize);
        AuthContext ctx = requireContext();
        LambdaQueryWrapper<ExceptionRecord> wrapper = new LambdaQueryWrapper<>();
        Long effectiveStaffId = staffId;
        if (ctx.isDriver()) {
            effectiveStaffId = ctx.getStaffId();
        } else if (ctx.isCustomer()) {
            List<Long> ownedOrderIds = workOrderMapper.selectList(new LambdaQueryWrapper<WorkOrder>()
                            .select(WorkOrder::getId)
                            .eq(WorkOrder::getCustomerId, ctx.getCustomerId()))
                    .stream().map(WorkOrder::getId).toList();
            if (ownedOrderIds.isEmpty()) {
                return PageResult.of(0, List.of());
            }
            wrapper.in(ExceptionRecord::getOrderId, ownedOrderIds)
                    .eq(ExceptionRecord::getIsPublicToCustomer, 1);
        } else if (!ctx.isAdmin()) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        wrapper.eq(effectiveStaffId != null, ExceptionRecord::getStaffId, effectiveStaffId)
                .eq(status != null, ExceptionRecord::getStatus, status)
                .eq(orderId != null, ExceptionRecord::getOrderId, orderId);
        return pagedQuery(exceptionRecordMapper, wrapper, ExceptionRecord::getReportedAt, pageNum, pageSize);
    }

    /**
     * 异常详情（API-EXC-003）。数据范围：管理员全量、司机本人、客户本人公开工单。
     */
    @Transactional(readOnly = true)
    public ExceptionRecord detail(Long id) {
        AuthContext ctx = requireContext();
        ExceptionRecord record = exceptionRecordMapper.selectById(id);
        if (record == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "异常记录不存在");
        }
        if (ctx.isAdmin()) {
            return record;
        }
        if (ctx.isDriver()) {
            if (!Objects.equals(record.getStaffId(), ctx.getStaffId())) {
                throw new BizException(ErrorCode.FORBIDDEN, "仅可查看本人上报的异常");
            }
            return record;
        }
        if (ctx.isCustomer()) {
            if (!Integer.valueOf(1).equals(record.getIsPublicToCustomer())) {
                throw new BizException(ErrorCode.FORBIDDEN, "该异常不对客户公开");
            }
            WorkOrder order = workOrderMapper.selectById(record.getOrderId());
            if (order == null || !Objects.equals(order.getCustomerId(), ctx.getCustomerId())) {
                throw new BizException(ErrorCode.FORBIDDEN, "仅可查看本人关联工单的异常");
            }
            return record;
        }
        throw new BizException(ErrorCode.FORBIDDEN);
    }

    /**
     * 异常批复（API-EXC-004，仅管理员）。
     * CONTINUE 恢复执行（互斥校验由状态机内部处理，命中 30001 时本事务整体回滚，为契约预期行为）；
     * SUSPEND 保持暂停仅记录批复；TERMINATE 经状态机取消工单并写取消原因；驳回仅留痕不改状态。
     */
    @Transactional
    public Map<String, Object> reply(Long id, ExceptionReplyRequest req, Long operatorId) {
        requireAdmin();
        if (id == null) {
            throw new BizException(ErrorCode.PARAM_MISSING, "异常记录 id 必填");
        }
        if (req.getApprove() == null) {
            throw new BizException(ErrorCode.PARAM_MISSING, "批复结论必填");
        }
        if (req.getInstruction() == null || !INSTRUCTIONS.contains(req.getInstruction())) {
            throw new BizException(ErrorCode.ENUM_INVALID, "批复指令取值非法");
        }
        ExceptionRecord record = exceptionRecordMapper.selectById(id);
        if (record == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "异常记录不存在");
        }
        if (!STATUS_PENDING.equals(record.getStatus())) {
            throw new BizException(ErrorCode.RESOURCE_STATUS_NOT_ALLOWED, "该异常已批复，不可重复批复");
        }
        WorkOrder order = workOrderMapper.selectById(record.getOrderId());
        if (order == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "异常关联工单不存在");
        }
        LocalDateTime now = LocalDateTime.now();

        ApprovalRecord approval = new ApprovalRecord();
        approval.setBizType("EXCEPTION");
        approval.setBizId(record.getId());
        approval.setOrderId(record.getOrderId());
        approval.setApproverId(operatorId);
        approval.setAction(Boolean.TRUE.equals(req.getApprove()) ? "APPROVE" : "REJECT");
        approval.setInstruction(req.getInstruction());
        approval.setOpinion(req.getOpinion());
        approval.setApprovedAt(now);
        approvalRecordMapper.insert(approval);

        String orderStatusAfter;
        if (Boolean.TRUE.equals(req.getApprove())) {
            switch (req.getInstruction()) {
                // 恢复执行：若司机此时已有其他进行中工单，状态机命中互斥返回 30001，整个事务回滚
                case "CONTINUE" -> {
                    workOrderStateMachine.transition(order, ORDER_STATUS_IN_PROGRESS, operatorId, "异常批复: 继续施工");
                    orderStatusAfter = ORDER_STATUS_IN_PROGRESS;
                }
                // 保持暂停：仅记录批复，工单停留 SUSPENDED 等待后续指令
                case "SUSPEND" -> orderStatusAfter = "SUSPENDED";
                // 终止：经状态机取消并写对客户可见的取消原因
                case "TERMINATE" -> {
                    workOrderStateMachine.transition(order, "CANCELLED", operatorId, "异常批复: 终止施工");
                    order.setCancelledReason("异常终止: " + record.getDescription());
                    workOrderMapper.updateById(order);
                    orderStatusAfter = "CANCELLED";
                }
                default -> throw new BizException(ErrorCode.ENUM_INVALID, "批复指令取值非法");
            }
        } else {
            // 驳回：工单状态保持批复前快照不变，仅记录驳回留痕
            orderStatusAfter = record.getOrderStatusBefore() != null
                    ? record.getOrderStatusBefore() : order.getStatus();
        }

        // 回读工单最新状态作为批复后快照，保证记录与工单表一致
        WorkOrder latest = workOrderMapper.selectById(record.getOrderId());
        if (latest != null && latest.getStatus() != null) {
            orderStatusAfter = latest.getStatus();
        }

        record.setStatus(STATUS_APPROVED);
        record.setInstruction(req.getInstruction());
        record.setRepliedAt(now);
        record.setOrderStatusAfter(orderStatusAfter);
        exceptionRecordMapper.updateById(record);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("exceptionId", record.getId());
        data.put("workOrderStatus", orderStatusAfter);
        return data;
    }

    // ==================== 私有方法 ====================

    private AuthContext requireContext() {
        AuthContext ctx = AuthContext.get();
        if (ctx == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return ctx;
    }

    private AuthContext requireDriver() {
        AuthContext ctx = requireContext();
        if (!ctx.isDriver()) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅司机本人可上报异常");
        }
        return ctx;
    }

    private void requireAdmin() {
        AuthContext ctx = requireContext();
        if (!ctx.isAdmin()) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅管理员可批复异常");
        }
    }

    private boolean hasPendingException(Long orderId) {
        Long count = exceptionRecordMapper.selectCount(new LambdaQueryWrapper<ExceptionRecord>()
                .eq(ExceptionRecord::getOrderId, orderId)
                .eq(ExceptionRecord::getStatus, STATUS_PENDING));
        return count != null && count > 0;
    }

    private void ensurePage(int pageNum, int pageSize) {
        if (pageNum < 1 || pageSize < 1 || pageSize > 100) {
            throw new BizException(ErrorCode.PAGE_INVALID);
        }
    }

    private <T> PageResult<T> pagedQuery(com.baomidou.mybatisplus.core.mapper.BaseMapper<T> mapper,
                                         LambdaQueryWrapper<T> wrapper,
                                         SFunction<T, ?> orderField,
                                         int pageNum, int pageSize) {
        long total = mapper.selectCount(wrapper);
        int offset = (pageNum - 1) * pageSize;
        wrapper.orderByDesc(orderField).last("LIMIT " + offset + ", " + pageSize);
        List<T> list = mapper.selectList(wrapper);
        return PageResult.of(total, list);
    }

    private String toJson(List<String> urls) {
        if (urls == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(urls);
        } catch (JsonProcessingException e) {
            log.warn("配图路径序列化失败: {}", urls, e);
            throw new BizException(ErrorCode.SYSTEM_ERROR, "配图路径序列化失败");
        }
    }
}
