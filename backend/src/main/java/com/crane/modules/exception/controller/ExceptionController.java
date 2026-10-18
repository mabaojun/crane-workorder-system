package com.crane.modules.exception.controller;

import com.crane.common.auth.AuthContext;
import com.crane.common.exception.BizException;
import com.crane.common.exception.ErrorCode;
import com.crane.common.result.ApiResult;
import com.crane.common.result.PageResult;
import com.crane.modules.exception.dto.ExceptionReplyRequest;
import com.crane.modules.exception.dto.ExceptionReportRequest;
import com.crane.modules.exception.entity.ExceptionRecord;
import com.crane.modules.exception.service.ExceptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 异常上报与批复接口（API-EXC-001 ~ API-EXC-004），路径与 03 §2 清单严格一致。
 */
@RestController
@RequestMapping("/api/v1/exception")
@RequiredArgsConstructor
public class ExceptionController {

    private final ExceptionService exceptionService;

    /** API-EXC-001 异常上报 */
    @PostMapping("/reports")
    public ApiResult<Map<String, Object>> report(@RequestBody ExceptionReportRequest req) {
        return ApiResult.ok(exceptionService.report(req, currentUserId()));
    }

    /** API-EXC-002 异常列表 */
    @GetMapping("/reports")
    public ApiResult<PageResult<ExceptionRecord>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long orderId,
            @RequestParam(required = false) Long staffId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResult.ok(exceptionService.list(status, orderId, staffId, pageNum, pageSize));
    }

    /** API-EXC-003 异常详情 */
    @GetMapping("/reports/{id}")
    public ApiResult<ExceptionRecord> detail(@PathVariable Long id) {
        return ApiResult.ok(exceptionService.detail(id));
    }

    /** API-EXC-004 异常批复 */
    @PostMapping("/reports/{id}/reply")
    public ApiResult<Map<String, Object>> reply(@PathVariable Long id, @RequestBody ExceptionReplyRequest req) {
        return ApiResult.ok(exceptionService.reply(id, req, currentUserId()));
    }

    private Long currentUserId() {
        AuthContext ctx = AuthContext.get();
        if (ctx == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return ctx.getUserId();
    }
}
