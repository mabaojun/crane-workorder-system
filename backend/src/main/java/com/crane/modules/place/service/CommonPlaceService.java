package com.crane.modules.place.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.crane.common.exception.BizException;
import com.crane.common.exception.ErrorCode;
import com.crane.modules.place.dto.CommonPlaceUpsertRequest;
import com.crane.modules.place.entity.CommonPlace;
import com.crane.modules.place.mapper.CommonPlaceMapper;
import com.crane.modules.workorder.entity.WorkOrder;
import com.crane.modules.workorder.mapper.WorkOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * 常用施工地点服务：管理员增删维护，客户端全量/模糊读取。
 * 数据实时持久化至 biz_common_place 表，不做本地缓存。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommonPlaceService {

    private final CommonPlaceMapper commonPlaceMapper;
    private final WorkOrderMapper workOrderMapper;

    /**
     * 客户端常用地点：仅返回本客户历史工单中使用过的施工地点（最近使用在前，去重）。
     * 客户未提交过的地点（含管理员维护的通用地点）不在此列，满足"常用地点=客户自己用过的地点"口径。
     */
    public List<CommonPlace> listMyPlaces(Long customerId) {
        List<WorkOrder> orders = workOrderMapper.selectList(new LambdaQueryWrapper<WorkOrder>()
                .eq(WorkOrder::getCustomerId, customerId)
                .isNotNull(WorkOrder::getAddress)
                .orderByDesc(WorkOrder::getId)
                .last("LIMIT 200"));
        LinkedHashMap<String, CommonPlace> places = new LinkedHashMap<>();
        for (WorkOrder order : orders) {
            String addr = order.getAddress().trim();
            if (addr.isEmpty() || places.containsKey(addr)) {
                continue;
            }
            CommonPlace place = new CommonPlace();
            place.setPlaceName(addr);
            place.setLongitude(order.getLongitude());
            place.setLatitude(order.getLatitude());
            place.setSortOrder(0);
            places.put(addr, place);
        }
        return new ArrayList<>(places.values());
    }

    /**
     * 全量地点列表（keyword 模糊过滤），按 sort_order 降序 + id 升序。
     * 供下单页下拉组件本地过滤使用，默认全量返回。
     */
    public List<CommonPlace> list(String keyword) {
        LambdaQueryWrapper<CommonPlace> wrapper = new LambdaQueryWrapper<CommonPlace>()
                .like(keyword != null && !keyword.isBlank(), CommonPlace::getPlaceName, keyword)
                .orderByDesc(CommonPlace::getSortOrder)
                .orderByAsc(CommonPlace::getId);
        return commonPlaceMapper.selectList(wrapper);
    }

    /**
     * 新增地点，名称唯一（逻辑删除同样占用名称唯一键，避免重复建项）。
     */
    @Transactional
    public CommonPlace create(CommonPlaceUpsertRequest req, Long operatorId) {
        String name = req.getPlaceName().trim();
        checkNameUnique(name, null);
        CommonPlace place = new CommonPlace();
        place.setPlaceName(name);
        place.setLongitude(req.getLongitude());
        place.setLatitude(req.getLatitude());
        place.setSortOrder(req.getSortOrder() == null ? 0 : req.getSortOrder());
        place.setCreatedBy(operatorId);
        place.setUpdatedBy(operatorId);
        try {
            commonPlaceMapper.insert(place);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.RESOURCE_STATUS_NOT_ALLOWED, "地点名称已存在");
        }
        return place;
    }

    /**
     * 删除地点（逻辑删除，保留历史引用可追溯）。
     */
    @Transactional
    public void delete(Long id, Long operatorId) {
        CommonPlace place = commonPlaceMapper.selectById(id);
        if (place == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "施工地点不存在或已删除");
        }
        commonPlaceMapper.deleteById(id);
    }

    /**
     * 施工点位带入常用地点（司机签到施工记录自动沉淀，也可由管理端建单时带入）：
     * 按名称去重 —— 命中已有地点仅回填最新经纬度；不存在则新增（并发重名时静默跳过，不阻断签到主流程）。
     */
    @Transactional
    public void upsertFromSite(String rawName, BigDecimal longitude, BigDecimal latitude) {
        if (rawName == null || rawName.isBlank()) {
            return;
        }
        String name = rawName.trim();
        CommonPlace existing = commonPlaceMapper.selectOne(new LambdaQueryWrapper<CommonPlace>()
                .eq(CommonPlace::getPlaceName, name)
                .last("LIMIT 1"));
        if (existing != null) {
            if (longitude != null || latitude != null) {
                commonPlaceMapper.update(null, new UpdateWrapper<CommonPlace>()
                        .eq("id", existing.getId())
                        .set(longitude != null, "longitude", longitude)
                        .set(latitude != null, "latitude", latitude));
            }
            return;
        }
        CommonPlace place = new CommonPlace();
        place.setPlaceName(name);
        place.setLongitude(longitude);
        place.setLatitude(latitude);
        place.setSortOrder(0);
        try {
            commonPlaceMapper.insert(place);
            log.info("施工点位已带入常用地点 name={} lng={} lat={}", name, longitude, latitude);
        } catch (DuplicateKeyException e) {
            // 并发或历史逻辑删除行占用名称唯一键：不阻断主流程
            log.info("常用地点已存在，跳过带入 name={}", name);
        }
    }

    private void checkNameUnique(String name, Long excludeId) {
        Long count = commonPlaceMapper.selectCount(new LambdaQueryWrapper<CommonPlace>()
                .eq(CommonPlace::getPlaceName, name)
                .ne(excludeId != null, CommonPlace::getId, excludeId));
        if (count != null && count > 0) {
            throw new BizException(ErrorCode.RESOURCE_STATUS_NOT_ALLOWED, "地点名称已存在");
        }
    }
}
