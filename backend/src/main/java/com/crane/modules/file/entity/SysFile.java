package com.crane.modules.file.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件元数据 sys_file：私有资源（需求图片 / 打卡照片 / 异常配图）统一登记。
 * 物理文件按 yyyy/MM/uuid.ext 存放于 crane.file.upload-dir，下载经鉴权接口以附件形式返回。
 */
@Data
@TableName("sys_file")
public class SysFile {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 原始文件名，下载时作为附件名展示 */
    private String fileName;

    /** 相对上传目录的存储路径，如 2026/10/uuid.jpg */
    private String storedPath;

    private String contentType;

    private Long sizeBytes;

    private Long uploaderId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Long createdBy;

    private Long updatedBy;

    @TableLogic
    private Integer deleted;
}
