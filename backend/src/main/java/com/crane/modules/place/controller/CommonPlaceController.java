package com.crane.modules.place.controller;

import com.crane.common.auth.AuthContext;
import com.crane.common.exception.BizException;
import com.crane.common.exception.ErrorCode;
import com.crane.common.result.ApiResult;
import com.crane.modules.place.dto.CommonPlaceUpsertRequest;
import com.crane.modules.place.entity.CommonPlace;
import com.crane.modules.place.service.CommonPlaceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 常用施工地点接口。
 * 读取（GET）面向全部登录用户（下单页/提报页下拉数据源）；
 * 新增与删除仅 ADMIN 可操作，结果实时落库。
 */
@RestController
@RequestMapping("/api/v1/place")
@RequiredArgsConstructor
public class CommonPlaceController {

    private final CommonPlaceService commonPlaceService;

    @GetMapping("/common-places")
    public ApiResult<List<CommonPlace>> list(@RequestParam(required = false) String keyword) {
        return ApiResult.ok(commonPlaceService.list(keyword));
    }

    /**
     * 常用地点：客户调用=本客户历史工单地点；管理员可传 customerId 查指定客户的历史地点
     *（直建工单时按所选客户带出，地点与实际用户关联）。均含经纬度，最近使用在前。
     */
    @GetMapping("/my-places")
    public ApiResult<List<CommonPlace>> myPlaces(@RequestParam(required = false) Long customerId) {
        AuthContext ctx = AuthContext.get();
        if (ctx == null) {
            return ApiResult.ok(List.of());
        }
        Long targetCustomerId;
        if (ctx.isAdmin()) {
            targetCustomerId = customerId;
        } else if (ctx.isCustomer()) {
            targetCustomerId = ctx.getCustomerId();
        } else {
            return ApiResult.ok(List.of());
        }
        if (targetCustomerId == null) {
            return ApiResult.ok(List.of());
        }
        return ApiResult.ok(commonPlaceService.listMyPlaces(targetCustomerId));
    }

    @PostMapping("/common-places")
    public ApiResult<CommonPlace> create(@Valid @RequestBody CommonPlaceUpsertRequest request) {
        requireAdmin();
        return ApiResult.ok(commonPlaceService.create(request, currentUserId()));
    }

    @DeleteMapping("/common-places/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        requireAdmin();
        commonPlaceService.delete(id, currentUserId());
        return ApiResult.ok();
    }

    private void requireAdmin() {
        AuthContext ctx = AuthContext.get();
        if (ctx == null || !ctx.isAdmin()) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
    }

    private Long currentUserId() {
        AuthContext ctx = AuthContext.get();
        return ctx == null ? null : ctx.getUserId();
    }
}
