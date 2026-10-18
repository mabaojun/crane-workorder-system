package com.crane.common.exception;

import com.crane.common.result.ApiResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 全局异常处理：BizException 透传契约错误码；校验异常归 2xxxx；其余归 50000 并记录 TraceId。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ApiResult<Void> handleBiz(BizException e) {
        log.warn("biz error code={} message={}", e.getCode(), e.getMessage());
        return ApiResult.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResult<Void> handleValid(MethodArgumentNotValidException e) {
        FieldError fe = e.getBindingResult().getFieldError();
        String msg = fe == null ? ErrorCode.PARAM_MISSING.getMessage()
                : fe.getField() + " " + fe.getDefaultMessage();
        return ApiResult.fail(ErrorCode.PARAM_MISSING.getCode(), msg);
    }

    @ExceptionHandler({MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class})
    public ApiResult<Void> handleParam(Exception e) {
        return ApiResult.fail(ErrorCode.PARAM_INVALID.getCode(), ErrorCode.PARAM_INVALID.getMessage());
    }

    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ApiResult<Void> handleNotFound(Exception e) {
        return ApiResult.fail(ErrorCode.NOT_FOUND.getCode(), ErrorCode.NOT_FOUND.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ApiResult<Void> handleOther(Exception e) {
        log.error("系统异常", e);
        return ApiResult.fail(ErrorCode.SYSTEM_ERROR.getCode(), ErrorCode.SYSTEM_ERROR.getMessage());
    }
}
