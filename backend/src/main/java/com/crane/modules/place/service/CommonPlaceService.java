package com.crane.modules.place.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crane.common.exception.BizException;
import com.crane.common.exception.ErrorCode;
import com.crane.modules.place.dto.CommonPlaceUpsertRequest;
import com.crane.modules.place.entity.CommonPlace;
import com.crane.modules.place.mapper.CommonPlaceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private void checkNameUnique(String name, Long excludeId) {
        Long count = commonPlaceMapper.selectCount(new LambdaQueryWrapper<CommonPlace>()
                .eq(CommonPlace::getPlaceName, name)
                .ne(excludeId != null, CommonPlace::getId, excludeId));
        if (count != null && count > 0) {
            throw new BizException(ErrorCode.RESOURCE_STATUS_NOT_ALLOWED, "地点名称已存在");
        }
    }
}
