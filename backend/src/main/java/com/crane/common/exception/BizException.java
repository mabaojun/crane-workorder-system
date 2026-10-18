package com.crane.common.exception;

import com.crane.common.result.ApiResult;
import lombok.Getter;

/**
 * 业务异常：携带 03 §5 分段错误码与用户可读文案。
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }
}
