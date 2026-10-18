package com.crane.modules.place.service;

import com.crane.common.auth.AuthContext;
import com.crane.common.exception.BizException;
import com.crane.modules.place.dto.CommonPlaceUpsertRequest;
import com.crane.modules.place.entity.CommonPlace;
import com.crane.modules.place.mapper.CommonPlaceMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 常用施工地点服务单元测试：名称唯一性、实时落库（无本地缓存）、逻辑删除。
 */
@ExtendWith(MockitoExtension.class)
class CommonPlaceServiceTest {

    @Mock
    private CommonPlaceMapper commonPlaceMapper;

    @InjectMocks
    private CommonPlaceService commonPlaceService;

    @BeforeEach
    void setUp() {
        AuthContext.set(new AuthContext(1L, "ADMIN", "SELF", null, null, null));
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    void createSuccessPersistsImmediately() {
        when(commonPlaceMapper.selectCount(any())).thenReturn(0L);

        CommonPlaceUpsertRequest req = new CommonPlaceUpsertRequest();
        req.setPlaceName("  济南奥体中心  ");
        req.setSortOrder(5);

        CommonPlace result = commonPlaceService.create(req, 1L);

        // 名称去除首尾空格后实时落库
        assertThat(result.getPlaceName()).isEqualTo("济南奥体中心");
        assertThat(result.getSortOrder()).isEqualTo(5);
        assertThat(result.getCreatedBy()).isEqualTo(1L);
        ArgumentCaptor<CommonPlace> captor = ArgumentCaptor.forClass(CommonPlace.class);
        verify(commonPlaceMapper).insert(captor.capture());
        assertThat(captor.getValue().getPlaceName()).isEqualTo("济南奥体中心");
    }

    @Test
    void createDuplicateNameThrows() {
        when(commonPlaceMapper.selectCount(any())).thenReturn(1L);

        CommonPlaceUpsertRequest req = new CommonPlaceUpsertRequest();
        req.setPlaceName("济南奥体中心");

        assertThatThrownBy(() -> commonPlaceService.create(req, 1L))
                .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getMessage()).contains("地点名称已存在"));
        verify(commonPlaceMapper, never()).insert(any(CommonPlace.class));
    }

    @Test
    void createRaceDuplicateKeyThrows() {
        // 并发场景：名称查重通过但唯一键冲突，兜底转换为业务异常
        when(commonPlaceMapper.selectCount(any())).thenReturn(0L);
        when(commonPlaceMapper.insert(any(CommonPlace.class))).thenThrow(new DuplicateKeyException("dup"));

        CommonPlaceUpsertRequest req = new CommonPlaceUpsertRequest();
        req.setPlaceName("济南西站");

        assertThatThrownBy(() -> commonPlaceService.create(req, 1L))
                .isInstanceOf(BizException.class);
    }

    @Test
    void deleteMissingPlaceThrows404() {
        when(commonPlaceMapper.selectById(9L)).thenReturn(null);

        assertThatThrownBy(() -> commonPlaceService.delete(9L, 1L))
                .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getMessage()).contains("施工地点不存在"));
        verify(commonPlaceMapper, never()).deleteById(9L);
    }

    @Test
    void deleteSuccess() {
        CommonPlace place = new CommonPlace();
        place.setId(3L);
        place.setPlaceName("遥墙机场");
        when(commonPlaceMapper.selectById(3L)).thenReturn(place);

        commonPlaceService.delete(3L, 1L);

        verify(commonPlaceMapper).deleteById(3L);
    }

    @Test
    void upsertFromSiteInsertsNewPlaceWithCoordinates() {
        // 施工点位带入：名称不存在时新增并携带经纬度
        when(commonPlaceMapper.selectOne(any())).thenReturn(null);

        commonPlaceService.upsertFromSite(" 济南遥墙机场 ", new java.math.BigDecimal("117.216000"), new java.math.BigDecimal("36.721000"));

        ArgumentCaptor<CommonPlace> captor = ArgumentCaptor.forClass(CommonPlace.class);
        verify(commonPlaceMapper).insert(captor.capture());
        assertThat(captor.getValue().getPlaceName()).isEqualTo("济南遥墙机场");
        assertThat(captor.getValue().getLongitude()).isEqualByComparingTo("117.216000");
        assertThat(captor.getValue().getLatitude()).isEqualByComparingTo("36.721000");
    }

    @Test
    void upsertFromSiteExistingUpdatesCoordinatesOnly() {
        // 命中已有地点：仅回填经纬度，不重复插入
        CommonPlace existing = new CommonPlace();
        existing.setId(7L);
        existing.setPlaceName("济南西站");
        when(commonPlaceMapper.selectOne(any())).thenReturn(existing);

        commonPlaceService.upsertFromSite("济南西站", new java.math.BigDecimal("116.900000"), new java.math.BigDecimal("36.650000"));

        verify(commonPlaceMapper, never()).insert(any(CommonPlace.class));
        verify(commonPlaceMapper).update(any(), any());
    }

    @Test
    void upsertFromSiteBlankNameSkips() {
        commonPlaceService.upsertFromSite("   ", null, null);
        verify(commonPlaceMapper, never()).insert(any(CommonPlace.class));
        verify(commonPlaceMapper, never()).selectOne(any());
    }
}
