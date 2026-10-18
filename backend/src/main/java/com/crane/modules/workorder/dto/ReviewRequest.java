package com.crane.modules.workorder.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * API-WO-008 提交客户评价请求体。score 1~5，一单一评。
 */
@Data
public class ReviewRequest {

    @NotNull(message = "score 必填")
    private Integer score;

    @Size(max = 500, message = "content 最长 500")
    private String content;
}
