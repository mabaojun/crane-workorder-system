package com.crane.common.result;

/**
 * TraceId 持有器：由 TraceIdFilter 写入 MDC，ApiResult 出参与日志共用。
 */
public final class TraceHolder {

    private static final String KEY = "traceId";
    private static final ThreadLocal<String> TRACE = new ThreadLocal<>();

    private TraceHolder() {
    }

    public static void set(String traceId) {
        TRACE.set(traceId);
        org.slf4j.MDC.put(KEY, traceId);
    }

    public static String currentTraceId() {
        return TRACE.get();
    }

    public static void clear() {
        TRACE.remove();
        org.slf4j.MDC.remove(KEY);
    }
}
