package com.crane.modules.place.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 常用施工地点新增/修改请求。
 */
@Data
public class CommonPlaceUpsertRequest {

    @NotBlank(message = "地点名称不能为空")
    @Size(max = 128, message = "地点名称不能超过128个字符")
    private String placeName;

    /** 排序权重，可选，默认 0 */
    private Integer sortOrder;
}
