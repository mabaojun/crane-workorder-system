package com.crane.modules.place.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 常用施工地点表：管理员维护，客户端下单时下拉快捷选择。
 */
@Data
@TableName("biz_common_place")
public class CommonPlace {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 地点名称（展示与模糊搜索用） */
    private String placeName;

    /** 排序权重，越大越靠前 */
    private Integer sortOrder;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Long createdBy;

    private Long updatedBy;

    @TableLogic
    private Integer deleted;
}
