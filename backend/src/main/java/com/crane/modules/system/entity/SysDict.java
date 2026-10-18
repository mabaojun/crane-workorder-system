package com.crane.modules.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 字典表（02 §3.2.5）。为枚举与前端下拉提供统一数据源，
 * 后端枚举、本表与前端共享层三者字面一致。
 */
@Data
@TableName("sys_dict")
public class SysDict {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 字典类型，对齐枚举字典名，如 work_order_status */
    private String dictType;

    /** 字典码，即枚举字面量，如 IN_PROGRESS */
    private String dictCode;

    /** 中文标签，如「进行中」 */
    private String dictLabel;

    /** 附加值（如颜色语义、排序权重） */
    private String dictValue;

    /** 状态色语义令牌，供前端色彩语义化使用 */
    private String colorToken;

    /** 排序 */
    private Integer sort;

    /** 是否启用 */
    private Integer isEnabled;

    /** 说明 */
    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Long createdBy;

    private Long updatedBy;

    @TableLogic
    private Integer deleted;
}
