package com.crane.modules.file.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crane.common.auth.AuthContext;
import com.crane.common.exception.BizException;
import com.crane.common.exception.ErrorCode;
import com.crane.modules.attendance.entity.Attendance;
import com.crane.modules.attendance.entity.DailyCheckout;
import com.crane.modules.attendance.mapper.AttendanceMapper;
import com.crane.modules.attendance.mapper.DailyCheckoutMapper;
import com.crane.modules.demand.entity.DemandOrder;
import com.crane.modules.demand.mapper.DemandOrderMapper;
import com.crane.modules.exception.entity.ExceptionRecord;
import com.crane.modules.exception.mapper.ExceptionRecordMapper;
import com.crane.modules.file.dto.FileDtos.OrderPhotoVO;
import com.crane.modules.file.dto.FileDtos.UploadVO;
import com.crane.modules.file.entity.SysFile;
import com.crane.modules.file.mapper.SysFileMapper;
import com.crane.modules.system.service.SystemConfigService;
import com.crane.modules.workorder.entity.WorkOrder;
import com.crane.modules.workorder.mapper.WorkOrderMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 文件服务：附件上传（私有存储）、鉴权下载、工单图片聚合。
 * 物理文件存 crane.file.upload-dir/yyyy/MM/uuid.ext，元数据落 sys_file；
 * 下载地址 /api/v1/files/{id}/{文件名} 便于前端直接展示文件名超链。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

    /** 允许上传的扩展名：图片与 PDF（现场照片、需求配图、单据扫描件） */
    private static final Set<String> ALLOWED_EXT = Set.of(
            "jpg", "jpeg", "png", "webp", "gif", "bmp", "pdf");

    private static final String STAGE_DEMAND = "DEMAND";
    private static final String STAGE_EXCEPTION = "EXCEPTION";

    private final SysFileMapper sysFileMapper;
    private final SystemConfigService systemConfigService;
    private final WorkOrderMapper workOrderMapper;
    private final DemandOrderMapper demandOrderMapper;
    private final AttendanceMapper attendanceMapper;
    private final DailyCheckoutMapper dailyCheckoutMapper;
    private final ExceptionRecordMapper exceptionRecordMapper;
    private final ObjectMapper objectMapper;

    @Value("${crane.file.upload-dir:./data/files}")
    private String uploadDir;

    /** 上传附件：校验大小与类型后落盘并登记元数据 */
    public UploadVO upload(MultipartFile file, Long uploaderId) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_MISSING, "请选择要上传的文件");
        }
        int maxMb = systemConfigService.getInt("file.max_size_mb", 10);
        if (file.getSize() > maxMb * 1024L * 1024L) {
            throw new BizException(ErrorCode.ATTACHMENT_INVALID, "文件大小不能超过 " + maxMb + " MB");
        }
        String originalName = sanitizeFileName(file.getOriginalFilename());
        String ext = extensionOf(originalName);
        if (!ALLOWED_EXT.contains(ext)) {
            throw new BizException(ErrorCode.ATTACHMENT_INVALID, "仅支持 JPG/PNG/WEBP/GIF/BMP/PDF 文件");
        }
        LocalDate today = LocalDate.now();
        String month = String.format("%04d/%02d", today.getYear(), today.getMonthValue());
        String storedName = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        String storedPath = month + "/" + storedName;

        Path base = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path target = base.resolve(storedPath).normalize();
        try {
            Files.createDirectories(target.getParent());
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.error("附件落盘失败 path={}", target, e);
            throw new BizException(ErrorCode.FILE_STORAGE_ERROR);
        }

        SysFile meta = new SysFile();
        meta.setFileName(originalName);
        meta.setStoredPath(storedPath);
        meta.setContentType(file.getContentType());
        meta.setSizeBytes(file.getSize());
        meta.setUploaderId(uploaderId);
        meta.setCreatedBy(uploaderId);
        meta.setUpdatedBy(uploaderId);
        sysFileMapper.insert(meta);
        return new UploadVO(meta.getId(), originalName, downloadUrl(meta), file.getSize(), file.getContentType());
    }

    /** 鉴权下载：返回元数据 + 文件资源，由控制器写 Content-Disposition */
    public DownloadResult download(Long id) {
        SysFile meta = sysFileMapper.selectById(id);
        if (meta == null) {
            throw new BizException(ErrorCode.FILE_NOT_FOUND);
        }
        Path base = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path path = base.resolve(meta.getStoredPath()).normalize();
        if (!path.startsWith(base) || !Files.exists(path)) {
            throw new BizException(ErrorCode.FILE_NOT_FOUND);
        }
        return new DownloadResult(meta, new FileSystemResource(path));
    }

    /**
     * 工单图片聚合：客户提报配图 + 签到/签退/任务结束照片 + 异常上报配图。
     * 仅管理员、该工单司机与所属客户可查看。
     */
    public List<OrderPhotoVO> orderPhotos(Long orderId) {
        WorkOrder order = workOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "工单不存在");
        }
        checkOrderAccess(order);

        List<OrderPhotoVO> photos = new ArrayList<>();
        // 需求单提报图片（转工单时沿用需求单）
        if (order.getDemandId() != null) {
            DemandOrder demand = demandOrderMapper.selectById(order.getDemandId());
            if (demand != null) {
                addPhotos(photos, STAGE_DEMAND, "客户提报图片", demand.getImageUrls(), demand.getCreatedAt());
            }
        }
        // 打卡照片：签到 / 当日签退 / 任务结束
        List<Attendance> attendances = attendanceMapper.selectList(new LambdaQueryWrapper<Attendance>()
                .eq(Attendance::getOrderId, orderId)
                .isNotNull(Attendance::getPhotoUrls)
                .orderByAsc(Attendance::getCheckedAt));
        for (Attendance attendance : attendances) {
            String stage = attendance.getType() == null ? "CHECK_IN" : attendance.getType();
            addPhotos(photos, stage, attendanceStageLabel(stage), attendance.getPhotoUrls(), attendance.getCheckedAt());
        }
        // 当日签退离场照片
        List<DailyCheckout> checkouts = dailyCheckoutMapper.selectList(new LambdaQueryWrapper<DailyCheckout>()
                .eq(DailyCheckout::getOrderId, orderId)
                .isNotNull(DailyCheckout::getPhotoUrls)
                .orderByAsc(DailyCheckout::getDayEndAt));
        for (DailyCheckout checkout : checkouts) {
            addPhotos(photos, "DAILY_CHECKOUT", "签退照片", checkout.getPhotoUrls(), checkout.getDayEndAt());
        }
        // 异常上报配图
        List<ExceptionRecord> exceptions = exceptionRecordMapper.selectList(new LambdaQueryWrapper<ExceptionRecord>()
                .eq(ExceptionRecord::getOrderId, orderId)
                .isNotNull(ExceptionRecord::getImageUrls)
                .orderByAsc(ExceptionRecord::getReportedAt));
        for (ExceptionRecord record : exceptions) {
            addPhotos(photos, STAGE_EXCEPTION, "异常配图", record.getImageUrls(), record.getReportedAt());
        }
        photos.sort(Comparator.comparing(OrderPhotoVO::time, Comparator.nullsLast(Comparator.naturalOrder())));
        return photos;
    }

    private void checkOrderAccess(WorkOrder order) {
        AuthContext ctx = AuthContext.get();
        if (ctx == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        if (ctx.isAdmin()) {
            return;
        }
        if (ctx.isDriver() && ctx.getStaffId() != null && ctx.getStaffId().equals(order.getStaffId())) {
            return;
        }
        if (ctx.isCustomer() && ctx.getCustomerId() != null && ctx.getCustomerId().equals(order.getCustomerId())) {
            return;
        }
        throw new BizException(ErrorCode.FORBIDDEN);
    }

    /** JSON 数组字符串逐项展开为图片项；兼容历史占位路径 */
    private void addPhotos(List<OrderPhotoVO> photos, String stage, String stageLabel,
                           String jsonUrls, LocalDateTime time) {
        for (String url : parseUrls(jsonUrls)) {
            photos.add(new OrderPhotoVO(stage, stageLabel, fileNameOf(url), url, time));
        }
    }

    private List<String> parseUrls(String jsonUrls) {
        if (jsonUrls == null || jsonUrls.isBlank()) {
            return List.of();
        }
        try {
            List<String> urls = objectMapper.readValue(jsonUrls, new TypeReference<List<String>>() {
            });
            return urls.stream().filter(u -> u != null && !u.isBlank()).toList();
        } catch (Exception e) {
            log.warn("图片路径 JSON 解析失败: {}", jsonUrls);
            return List.of();
        }
    }

    private String attendanceStageLabel(String stage) {
        return switch (stage) {
            case "CHECK_IN" -> "签到照片";
            case "DAILY_CHECKOUT" -> "签退照片";
            case "TASK_END" -> "任务结束照片";
            default -> "现场照片";
        };
    }

    /** 下载地址：id + 原始文件名（URL 编码），前端可直接从末段还原文件名 */
    private String downloadUrl(SysFile meta) {
        String name = URLEncoder.encode(meta.getFileName(), StandardCharsets.UTF_8).replace("+", "%20");
        return "/api/v1/files/" + meta.getId() + "/" + name;
    }

    /** 从下载地址还原文件名（兼容旧占位路径） */
    private String fileNameOf(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        int query = url.indexOf('?');
        String path = query >= 0 ? url.substring(0, query) : url;
        int slash = path.lastIndexOf('/');
        String name = slash >= 0 ? path.substring(slash + 1) : path;
        try {
            return URLDecoder.decode(name, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return name;
        }
    }

    /** 原始文件名清洗：仅保留基础名，去除控制字符并限长 */
    private String sanitizeFileName(String originalName) {
        String name = originalName == null ? "" : originalName;
        name = name.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replaceAll("[\\p{Cntrl}]", "").trim();
        if (name.isBlank()) {
            return "attachment";
        }
        return name.length() > 200 ? name.substring(name.length() - 200) : name;
    }

    private String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** 下载结果：元数据 + 物理资源 */
    public record DownloadResult(SysFile meta, Resource resource) {
    }
}
