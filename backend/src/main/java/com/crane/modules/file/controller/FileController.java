package com.crane.modules.file.controller;

import com.crane.common.auth.AuthContext;
import com.crane.common.exception.BizException;
import com.crane.common.exception.ErrorCode;
import com.crane.common.result.ApiResult;
import com.crane.modules.file.dto.FileDtos.OrderPhotoVO;
import com.crane.modules.file.dto.FileDtos.UploadVO;
import com.crane.modules.file.entity.SysFile;
import com.crane.modules.file.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 文件接口：上传、鉴权下载、工单图片聚合。
 * 所有接口均需登录；下载以附件形式返回（Content-Disposition），避免内联加载浪费流量。
 */
@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    /** 上传附件（multipart/form-data，字段名 file） */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResult<UploadVO> upload(@RequestParam("file") MultipartFile file) {
        return ApiResult.ok(fileService.upload(file, currentUserId()));
    }

    /** 鉴权下载：路径末段文件名仅用于前端展示，实际以库中原始名返回附件 */
    @GetMapping("/{id}/{name}")
    public ResponseEntity<org.springframework.core.io.Resource> downloadWithName(
            @PathVariable Long id, @PathVariable String name) {
        return buildDownload(id);
    }

    /** 鉴权下载（兼容不含文件名的地址） */
    @GetMapping("/{id}")
    public ResponseEntity<org.springframework.core.io.Resource> download(@PathVariable Long id) {
        return buildDownload(id);
    }

    /** 工单图片聚合：客户提报图 + 签到/签退/任务结束照片 + 异常配图 */
    @GetMapping("/order/{orderId}")
    public ApiResult<List<OrderPhotoVO>> orderPhotos(@PathVariable Long orderId) {
        return ApiResult.ok(fileService.orderPhotos(orderId));
    }

    private ResponseEntity<org.springframework.core.io.Resource> buildDownload(Long id) {
        FileService.DownloadResult result = fileService.download(id);
        SysFile meta = result.meta();
        String encoded = URLEncoder.encode(meta.getFileName(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        String contentType = meta.getContentType() == null || meta.getContentType().isBlank()
                ? MediaType.APPLICATION_OCTET_STREAM_VALUE
                : meta.getContentType();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encoded)
                .contentType(MediaType.parseMediaType(contentType))
                .contentLength(meta.getSizeBytes() == null ? 0 : meta.getSizeBytes())
                .body(result.resource());
    }

    private Long currentUserId() {
        AuthContext ctx = AuthContext.get();
        if (ctx == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return ctx.getUserId();
    }
}
