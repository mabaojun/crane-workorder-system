package com.crane.modules.ai.client;

import com.crane.common.exception.BizException;
import com.crane.common.exception.ErrorCode;
import com.crane.modules.system.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容大模型调用客户端（/chat/completions）。
 * 配置全部来自 sys_config 的 ai 组：ai.enabled 总开关、ai.api_base_url、ai.api_key、ai.model、
 * ai.timeout_seconds（默认 30）、ai.mock（mock 模式返回固定演示文本，供无 Key 环境联调与演示）。
 * 调用失败统一抛 BizException（AI_CALL_FAILED），由调用方决定落 FAILED 简报或降级。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiChatClient {

    private static final int DEFAULT_TIMEOUT_SECONDS = 30;

    private final SystemConfigService systemConfigService;

    /** mock 模式演示文本：覆盖连通性 / 参数传递 / 稳定性验证的三段固定输出 */
    private static final String MOCK_REPLY = """
            ## 今日工作简报（mock 演示模式）

            **连通性校验**：AI 配置读取正常，链路（聚合 → 提示词组装 → 生成 → 落库）已打通。

            **参数传递校验**：本条内容由后端 mock 通道生成，说明提示词模板与业务数据占位符替换正确。

            **稳定性校验**：mock 模式响应恒定，可用于无外部 Key 环境的全流程演示。配置真实 ai.api_base_url 与 ai.api_key 后即可切换为大模型真实生成。
            """;

    /** 配置快照：校验与诊断共用 */
    public record AiConfig(boolean enabled, boolean mock, String baseUrl, String apiKey, String model, int timeoutSeconds) {
        /** 是否具备真实调用条件（启用且非 mock 且 base_url/key/model 齐） */
        public boolean callable() {
            return enabled && !mock && notBlank(baseUrl) && notBlank(apiKey) && notBlank(model);
        }

        private boolean notBlank(String s) {
            return s != null && !s.isBlank();
        }
    }

    public AiConfig loadConfig() {
        boolean enabled = Boolean.parseBoolean(systemConfigService.getOrDefault("ai.enabled", "false"));
        boolean mock = Boolean.parseBoolean(systemConfigService.getOrDefault("ai.mock", "false"));
        String baseUrl = systemConfigService.getOrDefault("ai.api_base_url", "");
        String apiKey = systemConfigService.getOrDefault("ai.api_key", "");
        String model = systemConfigService.getOrDefault("ai.model", "");
        int timeout = systemConfigService.getInt("ai.timeout_seconds", DEFAULT_TIMEOUT_SECONDS);
        return new AiConfig(enabled, mock, baseUrl.trim(), apiKey.trim(), model.trim(), timeout);
    }

    /**
     * 发起一次对话补全调用，返回模型正文。
     * mock 模式直接返回演示文本；未启用 / 配置缺失 / 调用失败分别抛带原因的 50005/50009/50010。
     */
    public String chat(String systemPrompt, String userPrompt) {
        AiConfig config = loadConfig();
        assertCallable(config);
        if (config.mock()) {
            log.info("AI mock 模式生效，跳过外部调用");
            return MOCK_REPLY;
        }
        long start = System.currentTimeMillis();
        try {
            RestClient client = buildClient(config);
            Map<String, Object> body = Map.of(
                    "model", config.model(),
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userPrompt)
                    ),
                    "temperature", 0.4,
                    "stream", false
            );
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = client.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            String content = extractContent(resp);
            log.info("AI 调用成功 model={} 耗时={}ms contentLen={}",
                    config.model(), System.currentTimeMillis() - start, content.length());
            return content;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("AI 调用失败 model={} base={} 耗时={}ms: {}",
                    config.model(), config.baseUrl(), System.currentTimeMillis() - start, e.getMessage());
            throw new BizException(ErrorCode.AI_CALL_FAILED, "AI 调用失败: " + rootMessage(e));
        }
    }

    /** 连通性诊断：返回 {ok, latencyMs, reason}，供接口校验报告与后台自检使用 */
    public Map<String, Object> diagnose() {
        AiConfig config = loadConfig();
        if (!config.enabled()) {
            return Map.of("ok", false, "latencyMs", 0, "reason", "ai.enabled 未开启");
        }
        if (config.mock()) {
            return Map.of("ok", true, "latencyMs", 0, "reason", "mock 模式（不调用外部接口）");
        }
        if (!config.callable()) {
            return Map.of("ok", false, "latencyMs", 0, "reason",
                    "配置缺失: " + missingKeys(config));
        }
        long start = System.currentTimeMillis();
        try {
            String reply = chat("你是连通性测试助手。", "请回复：OK");
            boolean ok = reply != null && !reply.isBlank();
            return Map.of("ok", ok, "latencyMs", System.currentTimeMillis() - start,
                    "reason", ok ? "连通正常" : "响应为空");
        } catch (BizException e) {
            return Map.of("ok", false, "latencyMs", System.currentTimeMillis() - start, "reason", e.getMessage());
        }
    }

    private void assertCallable(AiConfig config) {
        if (!config.enabled()) {
            throw new BizException(ErrorCode.AI_UNAVAILABLE, "AI 服务未开启，请在后台「系统配置-AI 服务配置」打开 ai.enabled");
        }
        if (config.mock()) {
            return;
        }
        if (!notBlank(config.baseUrl()) || !notBlank(config.apiKey()) || !notBlank(config.model())) {
            throw new BizException(ErrorCode.AI_NOT_CONFIGURED, "AI 调用配置缺失: " + missingKeys(config));
        }
    }

    private String missingKeys(AiConfig config) {
        StringBuilder sb = new StringBuilder();
        if (!notBlank(config.baseUrl())) sb.append("ai.api_base_url ");
        if (!notBlank(config.apiKey())) sb.append("ai.api_key ");
        if (!notBlank(config.model())) sb.append("ai.model ");
        return sb.toString().trim();
    }

    private RestClient buildClient(AiConfig config) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        Duration timeout = Duration.ofSeconds(config.timeoutSeconds() <= 0 ? DEFAULT_TIMEOUT_SECONDS : config.timeoutSeconds());
        factory.setConnectTimeout((int) timeout.toMillis());
        factory.setReadTimeout((int) timeout.toMillis());
        return RestClient.builder()
                .baseUrl(stripTail(config.baseUrl()))
                .requestFactory(factory)
                .defaultHeader("Authorization", "Bearer " + config.apiKey())
                .build();
    }

    /** base_url 兼容带 /v1 或带完整路径两种写法，统一拼接 /chat/completions */
    private String stripTail(String baseUrl) {
        String url = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return url.endsWith("/chat/completions") ? url : url + "/chat/completions";
    }

    /** 解析 OpenAI 兼容响应：choices[0].message.content */
    @SuppressWarnings("unchecked")
    private String extractContent(Map<String, Object> resp) {
        if (resp == null) {
            throw new BizException(ErrorCode.AI_CALL_FAILED, "AI 响应为空");
        }
        Object error = resp.get("error");
        if (error instanceof Map<?, ?> err && err.get("message") != null) {
            throw new BizException(ErrorCode.AI_CALL_FAILED, "AI 服务返回错误: " + err.get("message"));
        }
        Object choices = resp.get("choices");
        if (!(choices instanceof List<?> list) || list.isEmpty()
                || !(list.get(0) instanceof Map<?, ?> first)) {
            throw new BizException(ErrorCode.AI_CALL_FAILED, "AI 响应缺少 choices");
        }
        Object message = first.get("message");
        if (!(message instanceof Map<?, ?> msg)) {
            throw new BizException(ErrorCode.AI_CALL_FAILED, "AI 响应缺少 message");
        }
        Object content = msg.get("content");
        if (!(content instanceof String text) || text.isBlank()) {
            throw new BizException(ErrorCode.AI_CALL_FAILED, "AI 响应 content 为空");
        }
        return text;
    }

    private String rootMessage(Throwable e) {
        Throwable cur = e;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur.getMessage() == null ? cur.getClass().getSimpleName() : cur.getMessage();
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
