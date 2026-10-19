package com.crane.modules.file.dto;

import java.time.LocalDateTime;

/**
 * 文件域出参：上传结果与工单图片聚合。
 */
public class FileDtos {

    /** 上传结果：url 为鉴权下载地址，前端以文件名超链展示 */
    public record UploadVO(Long id, String fileName, String url, long size, String contentType) {
    }

    /** 工单图片聚合项：stage 区分需求提报 / 签到 / 签退 / 任务结束 / 异常上报 */
    public record OrderPhotoVO(String stage, String stageLabel, String fileName, String url, LocalDateTime time) {
    }
}
