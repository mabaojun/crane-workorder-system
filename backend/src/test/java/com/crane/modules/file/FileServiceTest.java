package com.crane.modules.file;

import com.crane.common.auth.AuthContext;
import com.crane.common.exception.BizException;
import com.crane.modules.attendance.entity.Attendance;
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
import com.crane.modules.file.service.FileService;
import com.crane.modules.system.service.SystemConfigService;
import com.crane.modules.workorder.entity.WorkOrder;
import com.crane.modules.workorder.mapper.WorkOrderMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 文件服务单元测试：上传类型/大小校验、文件名下载地址、工单图片聚合与访问控制。
 */
class FileServiceTest {

    @TempDir
    Path tempDir;

    private SysFileMapper sysFileMapper;
    private SystemConfigService systemConfigService;
    private WorkOrderMapper workOrderMapper;
    private DemandOrderMapper demandOrderMapper;
    private AttendanceMapper attendanceMapper;
    private DailyCheckoutMapper dailyCheckoutMapper;
    private ExceptionRecordMapper exceptionRecordMapper;
    private FileService fileService;

    @BeforeEach
    void setUp() {
        sysFileMapper = mock(SysFileMapper.class);
        systemConfigService = mock(SystemConfigService.class);
        workOrderMapper = mock(WorkOrderMapper.class);
        demandOrderMapper = mock(DemandOrderMapper.class);
        attendanceMapper = mock(AttendanceMapper.class);
        dailyCheckoutMapper = mock(DailyCheckoutMapper.class);
        exceptionRecordMapper = mock(ExceptionRecordMapper.class);
        fileService = new FileService(sysFileMapper, systemConfigService, workOrderMapper,
                demandOrderMapper, attendanceMapper, dailyCheckoutMapper, exceptionRecordMapper,
                new ObjectMapper());
        ReflectionTestUtils.setField(fileService, "uploadDir", tempDir.toString());
        lenient().when(systemConfigService.getInt("file.max_size_mb", 10)).thenReturn(10);
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    void 上传图片成功并返回文件名超链() throws Exception {
        when(sysFileMapper.insert(any(SysFile.class))).thenAnswer(inv -> {
            inv.getArgument(0, SysFile.class).setId(7L);
            return 1;
        });
        MockMultipartFile file = new MockMultipartFile(
                "file", "现场照片 1.jpg", "image/jpeg", "hello".getBytes());

        UploadVO vo = fileService.upload(file, 1L);

        assertEquals(7L, vo.id());
        assertEquals("现场照片 1.jpg", vo.fileName());
        assertTrue(vo.url().startsWith("/api/v1/files/7/"));
        assertTrue(vo.url().contains("%E7%8E%B0%E5%9C%BA"));
        try (var stream = Files.walk(tempDir)) {
            assertTrue(stream.anyMatch(p -> p.toString().endsWith(".jpg")));
        }
    }

    @Test
    void 上传非法扩展名返回20006() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "virus.exe", "application/octet-stream", "x".getBytes());
        BizException e = assertThrows(BizException.class, () -> fileService.upload(file, 1L));
        assertEquals(20006, e.getCode());
    }

    @Test
    void 上传超出大小上限返回20006() {
        when(systemConfigService.getInt("file.max_size_mb", 10)).thenReturn(0);
        MockMultipartFile file = new MockMultipartFile(
                "file", "big.jpg", "image/jpeg", "1234".getBytes());
        BizException e = assertThrows(BizException.class, () -> fileService.upload(file, 1L));
        assertEquals(20006, e.getCode());
    }

    @Test
    void 工单图片聚合需求图签到照片与异常配图() {
        AuthContext ctx = new AuthContext();
        ctx.setUserId(1L);
        ctx.setRoleCode("ADMIN");
        AuthContext.set(ctx);

        WorkOrder order = new WorkOrder();
        order.setId(100L);
        order.setDemandId(5L);
        order.setStaffId(8L);
        order.setCustomerId(9L);
        when(workOrderMapper.selectById(100L)).thenReturn(order);

        DemandOrder demand = new DemandOrder();
        demand.setImageUrls("[\"/api/v1/files/1/%E9%9C%80%E6%B1%82.jpg\"]");
        demand.setCreatedAt(LocalDateTime.now().minusHours(3));
        when(demandOrderMapper.selectById(5L)).thenReturn(demand);

        Attendance attendance = new Attendance();
        attendance.setType("CHECK_IN");
        attendance.setPhotoUrls("[\"/api/v1/files/2/checkin.png\"]");
        attendance.setCheckedAt(LocalDateTime.now().minusHours(2));
        when(attendanceMapper.selectList(any())).thenReturn(List.of(attendance));
        when(dailyCheckoutMapper.selectList(any())).thenReturn(List.of());

        ExceptionRecord record = new ExceptionRecord();
        record.setImageUrls("[\"/api/v1/files/3/rain.pdf\"]");
        record.setReportedAt(LocalDateTime.now().minusHours(1));
        when(exceptionRecordMapper.selectList(any())).thenReturn(List.of(record));

        List<OrderPhotoVO> photos = fileService.orderPhotos(100L);

        assertEquals(3, photos.size());
        assertEquals("DEMAND", photos.get(0).stage());
        assertEquals("需求.jpg", photos.get(0).fileName());
        assertEquals("CHECK_IN", photos.get(1).stage());
        assertEquals("签到照片", photos.get(1).stageLabel());
        assertEquals("checkin.png", photos.get(1).fileName());
        assertEquals("EXCEPTION", photos.get(2).stage());
        assertEquals("rain.pdf", photos.get(2).fileName());
    }

    @Test
    void 非本人工单不可查看图片() {
        AuthContext ctx = new AuthContext();
        ctx.setUserId(2L);
        ctx.setRoleCode("DRIVER");
        ctx.setStaffId(99L);
        AuthContext.set(ctx);
        WorkOrder order = new WorkOrder();
        order.setId(100L);
        order.setStaffId(8L);
        when(workOrderMapper.selectById(100L)).thenReturn(order);

        BizException e = assertThrows(BizException.class, () -> fileService.orderPhotos(100L));
        assertEquals(10003, e.getCode());
    }
}
