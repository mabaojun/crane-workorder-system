package com.crane.modules.workorder.service;

import com.crane.common.exception.BizException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 工单运行明细导出单元测试：空数据仍生成合法 xlsx、日期区间校验。
 */
@ExtendWith(MockitoExtension.class)
class WorkOrderExportServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private WorkOrderExportService exportService;

    @Test
    void emptyDataStillProducesValidXlsx() {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());

        WorkOrderExportService.ExportFile file =
                exportService.export(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

        assertThat(file.fileName()).isEqualTo("工单运行明细_2026-10-01_2026-10-31.xlsx");
        assertThat(file.content()).isNotEmpty();
        // xlsx 为 zip 容器，固定以 PK 开头
        assertThat(file.content()[0]).isEqualTo((byte) 'P');
        assertThat(file.content()[1]).isEqualTo((byte) 'K');
    }

    @Test
    void startDateAfterEndDateIsRejected() {
        assertThatThrownBy(() -> exportService.export(
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 10, 1)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("开始日期不能晚于结束日期");
    }

    @Test
    void rangeLongerThanOneYearIsRejected() {
        assertThatThrownBy(() -> exportService.export(
                LocalDate.of(2025, 1, 1), LocalDate.of(2026, 12, 31)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不能超过 366 天");
    }
}
