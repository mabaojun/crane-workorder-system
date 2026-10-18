package com.crane.common.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一响应信封，契约见 03 §1.4：{ code, message, data, traceId }，code=0 成功。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResult<T> {

    private int code;
    private String message;
    private T data;
    private String traceId;

    public static <T> ApiResult<T> ok(T data) {
        return new ApiResult<>(0, "OK", data, TraceHolder.currentTraceId());
    }

    public static ApiResult<Void> ok() {
        return new ApiResult<>(0, "OK", null, TraceHolder.currentTraceId());
    }

    public static <T> ApiResult<T> fail(int code, String message) {
        return new ApiResult<>(code, message, null, TraceHolder.currentTraceId());
    }

    public static <T> ApiResult<T> fail(ErrorCodeHolder holder) {
        return new ApiResult<>(holder.code(), holder.message(), null, TraceHolder.currentTraceId());
    }

    /** 错误码持有接口，避免 common.result 反向依赖 exception 包。 */
    public interface ErrorCodeHolder {
        int code();

        String message();
    }
}
