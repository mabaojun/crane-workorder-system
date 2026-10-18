package com.crane.modules.notify.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crane.modules.notify.entity.NotificationRecord;
import com.crane.modules.notify.mapper.NotificationRecordMapper;
import com.crane.modules.system.entity.SysUser;
import com.crane.modules.system.mapper.SysUserMapper;
import com.crane.modules.workorder.entity.WorkOrder;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 管理端事件通知服务：站内通知落库（biz_notification_record）+ SSE 实时推送。
 * 推送为同步发送（毫秒级），满足"状态变更延迟 ≤ 500ms"的要求；
 * SSE 断连的管理端可在重连后通过 recent() 拉取补偿，不丢事件。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotifyService {

    private static final DateTimeFormatter NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    /** 全部在线管理端 SSE 连接（管理端用户量小，全局注册表即可） */
    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    private final NotificationRecordMapper notificationRecordMapper;
    private final SysUserMapper sysUserMapper;
    private final ObjectMapper objectMapper;

    // ==================== SSE ====================

    /** 注册管理端 SSE 连接：无限超时，断开/超时自动清理 */
    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);
        Runnable cleanup = () -> emitters.remove(emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(t -> cleanup.run());
        return emitter;
    }

    // ==================== 业务通知 ====================

    /**
     * 工单事件通知全部管理员：站内记录落库 + SSE 实时推送。
     * round 用于去重键区分轮次（如退单轮次），同轮次重复调用幂等跳过。
     *
     * @return 落库的通知记录数
     */
    public int notifyAdminsOrderEvent(WorkOrder order, String node, String title, String content, long round) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("bizType", "ORDER");
        payload.put("orderId", order.getId());
        payload.put("orderNo", order.getOrderNo());
        payload.put("taskName", order.getName());
        payload.put("status", order.getStatus());
        payload.put("node", node);
        payload.put("title", title);
        payload.put("content", content);
        payload.put("occurredAt", LocalDateTime.now().toString());
        String payloadJson = writeJson(payload);

        List<SysUser> admins = sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getRoleId, 1L));
        int saved = 0;
        for (SysUser admin : admins) {
            NotificationRecord record = buildRecord(order, node, content, admin.getId(), round);
            try {
                notificationRecordMapper.insert(record);
                saved++;
            } catch (DuplicateKeyException e) {
                // 同轮次重复通知幂等跳过
                continue;
            }
        }
        pushAll("notify", payloadJson);
        log.info("管理端事件通知已推送 node={} orderId={} admins={} sseClients={}",
                node, order.getId(), admins.size(), emitters.size());
        return saved;
    }

    /** 最近通知（SSE 断连补偿拉取），按时间倒序 */
    public List<NotificationRecord> recent(int limit) {
        int size = Math.min(Math.max(limit, 1), 100);
        return notificationRecordMapper.selectList(new LambdaQueryWrapper<NotificationRecord>()
                .orderByDesc(NotificationRecord::getId)
                .last("LIMIT " + size));
    }

    // ==================== 私有工具 ====================

    private NotificationRecord buildRecord(WorkOrder order, String node, String content, Long adminUserId, long round) {
        NotificationRecord record = new NotificationRecord();
        record.setNotifyNo("NT" + LocalDateTime.now().format(NO_FMT)
                + String.format("%04d", (int) (System.nanoTime() % 10000)));
        record.setDedupKey(String.format("ORDER:%d:%s:ADMIN:%d:%d", order.getId(), node, adminUserId, round));
        record.setBizType("ORDER");
        record.setBizId(order.getId());
        record.setOrderId(order.getId());
        record.setReceiverType("ADMIN");
        record.setReceiverId(adminUserId);
        record.setNotifyNode(node);
        record.setChannel("INAPP");
        record.setContent(content);
        record.setSendStatus("SUCCESS");
        record.setRetryCount(0);
        record.setSentAt(LocalDateTime.now());
        record.setIsBillable(0);
        record.setCreatedBy(order.getStaffId());
        return record;
    }

    /** 向全部在线管理端推送事件；单个连接失败即摘除，不影响其他客户端 */
    private void pushAll(String eventName, String payloadJson) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(payloadJson));
            } catch (Exception e) {
                emitters.remove(emitter);
            }
        }
    }

    private String writeJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return String.valueOf(payload);
        }
    }
}
