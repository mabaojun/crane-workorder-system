package com.crane.modules.workorder.service;

import com.crane.common.exception.BizException;
import com.crane.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 工单运行明细导出（工作台，仅管理员）。
 * 按「实际开始时间 → 计划开始时间 → 创建时间」归期，导出工单明细、签到签退、每日签退、
 * 异常记录与状态流转五个工作表，供财务、人事对账与工时统计使用。
 */
@Service
@RequiredArgsConstructor
public class WorkOrderExportService {

    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final Map<String, String> SOURCE_LABELS = Map.of(
            "DEMAND", "需求单转入",
            "DIRECT", "管理员直建"
    );

    private static final Map<String, String> STATUS_LABELS = Map.of(
            "PENDING_ACCEPT", "待接单",
            "PENDING_ARRIVE", "待到场",
            "IN_PROGRESS", "进行中",
            "SUSPENDED", "施工受阻",
            "COMPLETED", "已完成",
            "CANCELLED", "已取消"
    );

    private static final Map<String, String> VISIBLE_STATUS_LABELS = Map.of(
            "DISPATCHED", "已派单",
            "ACCEPTED", "已接单",
            "STARTED", "任务开始执行",
            "PAUSED", "任务暂停",
            "FINISHED", "任务已完成",
            "CANCELLED", "任务已取消"
    );

    private static final Map<String, String> VEHICLE_TYPE_LABELS = Map.of(
            "CRANE", "吊车",
            "LIFT", "举升车",
            "SEMI_TRAILER", "板车",
            "HEAVY_TRAILER", "大件托盘车"
    );

    private static final Map<String, String> SETTLEMENT_LABELS = Map.of(
            "ENTERPRISE", "企业主体",
            "PERSONAL", "个人结算"
    );

    private static final Map<String, String> CUSTOMER_TYPE_LABELS = Map.of(
            "COMPANY", "公司",
            "PERSON", "个人"
    );

    private static final Map<String, String> ATTENDANCE_TYPE_LABELS = Map.of(
            "CHECK_IN", "签到",
            "DAILY_CHECKOUT", "当日签退",
            "TASK_END", "任务结束"
    );

    private static final Map<String, String> EXCEPTION_TYPE_LABELS = Map.of(
            "RAIN", "下雨",
            "PARTY_STOP", "甲方停工",
            "VEHICLE_FAULT", "车辆故障",
            "CUSTOM", "其他原因"
    );

    private static final Map<String, String> EXCEPTION_STATUS_LABELS = Map.of(
            "PENDING", "待批复",
            "APPROVED", "已批复",
            "CLOSED", "已关闭"
    );

    private static final Map<String, String> EXCEPTION_INSTRUCTION_LABELS = Map.of(
            "CONTINUE", "继续施工",
            "SUSPEND", "暂停",
            "TERMINATE", "终止"
    );

    private static final Map<String, String> UPLOAD_MODE_LABELS = Map.of(
            "ONLINE", "实时上传",
            "OFFLINE_RETRY", "离线补传"
    );

    /** 归期口径：优先实际开始时间，其次计划开始时间，最后创建时间 */
    private static final String PERIOD_CONDITION =
            " COALESCE(wo.started_at, wo.expected_start_at, wo.created_at) >= ?"
                    + " AND COALESCE(wo.started_at, wo.expected_start_at, wo.created_at) < ? ";

    private static final String ORDER_SQL = """
            SELECT wo.id AS order_id,
                   wo.order_no AS order_no,
                   wo.name AS task_name,
                   wo.source AS source,
                   d.demand_no AS demand_no,
                   wo.status AS status,
                   wo.customer_visible_status AS customer_visible_status,
                   c.customer_no AS customer_no,
                   c.name AS customer_name,
                   c.type AS customer_type,
                   c.industry AS customer_industry,
                   wo.settlement_type AS settlement_type,
                   sc.name AS settlement_customer_name,
                   COALESCE(NULLIF(d.contact_name, ''), cc.name) AS contact_name,
                   COALESCE(NULLIF(d.contact_phone, ''), cc.phone) AS contact_phone,
                   cc.position AS contact_position,
                   wo.address AS address,
                   wo.longitude AS longitude,
                   wo.latitude AS latitude,
                   wo.vehicle_type AS vehicle_type,
                   v.plate_no AS vehicle_plate_no,
                   v.model AS vehicle_model,
                   v.tonnage AS vehicle_tonnage,
                   v.lift_height AS vehicle_lift_height,
                   wo.cargo_name AS cargo_name,
                   wo.cargo_quantity AS cargo_quantity,
                   wo.cargo_weight AS cargo_weight,
                   wo.crane_tonnage AS crane_tonnage,
                   wo.estimated_days AS estimated_days,
                   s.staff_no AS staff_no,
                   s.name AS staff_name,
                   s.phone AS staff_phone,
                   s.license_type AS license_type,
                   s.special_cert_no AS special_cert_no,
                   wo.expected_start_at AS expected_start_at,
                   wo.created_at AS created_at,
                   wo.accept_deadline_at AS accept_deadline_at,
                   wo.accepted_at AS accepted_at,
                   wo.started_at AS started_at,
                   wo.suspended_at AS suspended_at,
                   wo.completed_at AS completed_at,
                   wo.cancelled_at AS cancelled_at,
                   wo.cancelled_reason AS cancelled_reason,
                   wo.timeout_reminded_at AS timeout_reminded_at,
                   wo.is_pinned_alarm AS is_pinned_alarm,
                   wo.remark AS remark,
                   wo.requirement AS requirement,
                   wo.updated_at AS updated_at,
                   whs.total_minutes AS total_minutes,
                   whs.overtime_minutes AS overtime_minutes,
                   whs.check_in_count AS check_in_count,
                   whs.checkout_count AS checkout_count,
                   cr.score AS review_score,
                   cr.content AS review_content,
                   cr.reviewed_at AS reviewed_at,
                   (SELECT MIN(a.checked_at) FROM biz_attendance a
                     WHERE a.order_id = wo.id AND a.type = 'CHECK_IN' AND a.deleted = 0) AS first_check_in_at,
                   (SELECT MAX(a.checked_at) FROM biz_attendance a
                     WHERE a.order_id = wo.id AND a.type = 'DAILY_CHECKOUT' AND a.deleted = 0) AS last_daily_checkout_at,
                   (SELECT MAX(a.checked_at) FROM biz_attendance a
                     WHERE a.order_id = wo.id AND a.type = 'TASK_END' AND a.deleted = 0) AS task_end_at,
                   (SELECT COUNT(*) FROM biz_exception_record er
                     WHERE er.order_id = wo.id AND er.deleted = 0) AS exception_count,
                   (SELECT GROUP_CONCAT(COALESCE(NULLIF(er.type_label, ''), er.type)
                     ORDER BY er.id SEPARATOR '、')
                     FROM biz_exception_record er
                     WHERE er.order_id = wo.id AND er.deleted = 0) AS exception_types
            FROM biz_work_order wo
            LEFT JOIN biz_customer c ON c.id = wo.customer_id AND c.deleted = 0
            LEFT JOIN biz_customer_contact cc ON cc.id = wo.contact_id AND cc.deleted = 0
            LEFT JOIN biz_demand_order d ON d.id = wo.demand_id AND d.deleted = 0
            LEFT JOIN biz_staff s ON s.id = wo.staff_id AND s.deleted = 0
            LEFT JOIN biz_vehicle v ON v.id = wo.vehicle_id AND v.deleted = 0
            LEFT JOIN biz_customer sc ON sc.id = wo.settlement_customer_id AND sc.deleted = 0
            LEFT JOIN biz_work_hour_stat whs ON whs.order_id = wo.id AND whs.deleted = 0
            LEFT JOIN biz_customer_review cr ON cr.order_id = wo.id AND cr.deleted = 0
            WHERE wo.deleted = 0 AND
            """ + PERIOD_CONDITION + """
            ORDER BY COALESCE(wo.started_at, wo.expected_start_at, wo.created_at), wo.id
            """;

    private static final String ATTENDANCE_SQL = """
            SELECT wo.order_no AS order_no,
                   s.staff_no AS staff_no,
                   s.name AS staff_name,
                   a.type AS type,
                   a.checked_at AS checked_at,
                   a.captured_at AS captured_at,
                   a.longitude AS longitude,
                   a.latitude AS latitude,
                   a.distance_meters AS distance_meters,
                   a.is_in_range AS is_in_range,
                   a.is_compliant AS is_compliant,
                   a.is_valid AS is_valid,
                   a.upload_mode AS upload_mode,
                   a.photo_urls AS photo_urls
            FROM biz_attendance a
            JOIN biz_work_order wo ON wo.id = a.order_id AND wo.deleted = 0
            LEFT JOIN biz_staff s ON s.id = a.staff_id AND s.deleted = 0
            WHERE a.deleted = 0 AND
            """ + PERIOD_CONDITION + """
            ORDER BY wo.order_no, a.checked_at, a.id
            """;

    private static final String DAILY_CHECKOUT_SQL = """
            SELECT wo.order_no AS order_no,
                   s.staff_no AS staff_no,
                   s.name AS staff_name,
                   dc.work_date AS work_date,
                   dc.day_start_at AS day_start_at,
                   dc.day_end_at AS day_end_at,
                   dc.distance_meters AS distance_meters,
                   dc.is_in_range AS is_in_range,
                   dc.is_left_site AS is_left_site,
                   dc.is_returned AS is_returned,
                   dc.photo_urls AS photo_urls
            FROM biz_daily_checkout dc
            JOIN biz_work_order wo ON wo.id = dc.order_id AND wo.deleted = 0
            LEFT JOIN biz_staff s ON s.id = dc.staff_id AND s.deleted = 0
            WHERE dc.deleted = 0 AND
            """ + PERIOD_CONDITION + """
            ORDER BY wo.order_no, dc.work_date, dc.id
            """;

    private static final String EXCEPTION_SQL = """
            SELECT wo.order_no AS order_no,
                   s.staff_no AS staff_no,
                   s.name AS staff_name,
                   er.type AS type,
                   er.type_label AS type_label,
                   er.description AS description,
                   er.status AS status,
                   er.instruction AS instruction,
                   er.order_status_before AS order_status_before,
                   er.order_status_after AS order_status_after,
                   er.is_public_to_customer AS is_public_to_customer,
                   er.reported_at AS reported_at,
                   er.replied_at AS replied_at
            FROM biz_exception_record er
            JOIN biz_work_order wo ON wo.id = er.order_id AND wo.deleted = 0
            LEFT JOIN biz_staff s ON s.id = er.staff_id AND s.deleted = 0
            WHERE er.deleted = 0 AND
            """ + PERIOD_CONDITION + """
            ORDER BY wo.order_no, er.reported_at, er.id
            """;

    private static final String STATUS_LOG_SQL = """
            SELECT wo.order_no AS order_no,
                   l.from_status AS from_status,
                   l.to_status AS to_status,
                   l.operator_id AS operator_id,
                   l.remark AS remark,
                   l.created_at AS created_at
            FROM biz_work_order_status_log l
            JOIN biz_work_order wo ON wo.id = l.order_id AND wo.deleted = 0
            WHERE l.deleted = 0 AND
            """ + PERIOD_CONDITION + """
            ORDER BY wo.order_no, l.created_at, l.id
            """;

    private final JdbcTemplate jdbcTemplate;

    /**
     * 生成 Excel。startDate / endDate 为空时默认取当月 1 日至今天；
     * 单次最多导出 366 天，避免一次性拉取过多数据。
     */
    @Transactional(readOnly = true)
    public ExportFile export(LocalDate startDate, LocalDate endDate) {
        LocalDate today = LocalDate.now();
        LocalDate start = startDate == null ? today.withDayOfMonth(1) : startDate;
        // 默认导出完整自然月，包含本月内已创建、计划在月末执行的工单
        LocalDate end = endDate == null ? today.withDayOfMonth(today.lengthOfMonth()) : endDate;
        if (start.isAfter(end)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "开始日期不能晚于结束日期");
        }
        if (ChronoUnit.DAYS.between(start, end) > 366) {
            throw new BizException(ErrorCode.VALUE_OUT_OF_RANGE, "单次导出区间不能超过 366 天");
        }
        Timestamp from = Timestamp.valueOf(start.atStartOfDay());
        Timestamp to = Timestamp.valueOf(end.plusDays(1).atStartOfDay());

        List<Map<String, Object>> orders = jdbcTemplate.queryForList(ORDER_SQL, from, to);
        List<Map<String, Object>> attendances = jdbcTemplate.queryForList(ATTENDANCE_SQL, from, to);
        List<Map<String, Object>> dailyCheckouts = jdbcTemplate.queryForList(DAILY_CHECKOUT_SQL, from, to);
        List<Map<String, Object>> exceptions = jdbcTemplate.queryForList(EXCEPTION_SQL, from, to);
        List<Map<String, Object>> statusLogs = jdbcTemplate.queryForList(STATUS_LOG_SQL, from, to);

        String title = "工单运行明细（" + start + " 至 " + end + "）";
        SXSSFWorkbook workbook = new SXSSFWorkbook(200);
        workbook.setCompressTempFiles(true);
        try {
            Styles styles = createStyles(workbook);
            writeSheet(workbook, styles, "工单明细", title,
                    "归期口径：实际开始时间 → 计划开始时间 → 创建时间；记录数：" + orders.size(),
                    orderColumns(), orders);
            writeSheet(workbook, styles, "签到签退", title,
                    "签到 / 当日签退 / 任务结束打卡记录；记录数：" + attendances.size(),
                    attendanceColumns(), attendances);
            writeSheet(workbook, styles, "每日签退", title,
                    "跨天任务逐日签退与车辆离场 / 回场记录；记录数：" + dailyCheckouts.size(),
                    dailyCheckoutColumns(), dailyCheckouts);
            writeSheet(workbook, styles, "异常记录", title,
                    "司机上报与管理员批复记录；记录数：" + exceptions.size(),
                    exceptionColumns(), exceptions);
            writeSheet(workbook, styles, "状态流转", title,
                    "工单状态变更与管理员修改留痕；记录数：" + statusLogs.size(),
                    statusLogColumns(), statusLogs);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return new ExportFile("工单运行明细_" + start + "_" + end + ".xlsx", out.toByteArray());
        } catch (IOException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "Excel 生成失败");
        } finally {
            workbook.dispose();
            try {
                workbook.close();
            } catch (IOException ignored) {
                // 临时文件清理失败不影响已经生成的字节流
            }
        }
    }

    private void writeSheet(SXSSFWorkbook workbook, Styles styles, String sheetName, String title,
                            String note, List<Column> columns, List<Map<String, Object>> rows) {
        SXSSFSheet sheet = workbook.createSheet(sheetName);
        for (int i = 0; i < columns.size(); i++) {
            sheet.setColumnWidth(i, columns.get(i).width() * 256);
        }

        Row titleRow = sheet.createRow(0);
        titleRow.setHeightInPoints(26);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue(title);
        titleCell.setCellStyle(styles.title());
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, Math.max(0, columns.size() - 1)));

        Row noteRow = sheet.createRow(1);
        Cell noteCell = noteRow.createCell(0);
        noteCell.setCellValue(note + " ｜ 导出时间：" + LocalDateTime.now().format(DATE_TIME_FMT));
        noteCell.setCellStyle(styles.note());
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, Math.max(0, columns.size() - 1)));

        Row headerRow = sheet.createRow(2);
        headerRow.setHeightInPoints(22);
        for (int i = 0; i < columns.size(); i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(columns.get(i).header());
            cell.setCellStyle(styles.header());
        }

        int rowIndex = 3;
        for (Map<String, Object> rowMap : rows) {
            Row row = sheet.createRow(rowIndex++);
            for (int i = 0; i < columns.size(); i++) {
                writeCell(row.createCell(i), columns.get(i).getter().apply(rowMap), styles);
            }
        }

        sheet.createFreezePane(0, 3);
        sheet.setAutoFilter(new CellRangeAddress(2, 2, 0, Math.max(0, columns.size() - 1)));
        sheet.setFitToPage(true);
        sheet.getPrintSetup().setLandscape(true);
        sheet.getPrintSetup().setFitWidth((short) 1);
        sheet.getPrintSetup().setFitHeight((short) 0);
    }

    private void writeCell(Cell cell, Object value, Styles styles) {
        if (value == null) {
            cell.setBlank();
            cell.setCellStyle(styles.data());
        } else if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
            cell.setCellStyle(styles.number());
        } else if (value instanceof LocalDateTime dateTime) {
            cell.setCellValue(dateTime);
            cell.setCellStyle(styles.dateTime());
        } else if (value instanceof LocalDate date) {
            cell.setCellValue(date);
            cell.setCellStyle(styles.date());
        } else {
            cell.setCellValue(String.valueOf(value));
            cell.setCellStyle(styles.data());
        }
    }

    private Styles createStyles(SXSSFWorkbook workbook) {
        Font titleFont = workbook.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 14);

        Font noteFont = workbook.createFont();
        noteFont.setFontHeightInPoints((short) 10);
        noteFont.setColor(IndexedColors.GREY_50_PERCENT.getIndex());

        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setColor(IndexedColors.WHITE.getIndex());

        CellStyle title = workbook.createCellStyle();
        title.setFont(titleFont);
        title.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
        title.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        title.setVerticalAlignment(VerticalAlignment.CENTER);

        CellStyle note = workbook.createCellStyle();
        note.setFont(noteFont);
        note.setVerticalAlignment(VerticalAlignment.CENTER);

        CellStyle header = workbook.createCellStyle();
        header.setFont(headerFont);
        header.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        header.setAlignment(HorizontalAlignment.CENTER);
        header.setVerticalAlignment(VerticalAlignment.CENTER);
        setBorder(header);

        CellStyle data = workbook.createCellStyle();
        data.setVerticalAlignment(VerticalAlignment.CENTER);
        setBorder(data);

        CellStyle dateTime = workbook.createCellStyle();
        dateTime.cloneStyleFrom(data);
        dateTime.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd hh:mm:ss"));

        CellStyle date = workbook.createCellStyle();
        date.cloneStyleFrom(data);
        date.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd"));

        CellStyle number = workbook.createCellStyle();
        number.cloneStyleFrom(data);
        number.setDataFormat(workbook.createDataFormat().getFormat("0.##"));

        return new Styles(title, note, header, data, dateTime, date, number);
    }

    private void setBorder(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setTopBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setLeftBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setRightBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
    }

    private List<Column> orderColumns() {
        return List.of(
                col("工单号", 20, r -> r.get("order_no")),
                col("任务名称", 28, r -> r.get("task_name")),
                col("工单来源", 14, r -> label(SOURCE_LABELS, r.get("source"))),
                col("来源需求单号", 20, r -> r.get("demand_no")),
                col("工单状态", 12, r -> label(STATUS_LABELS, r.get("status"))),
                col("客户可见状态", 14, r -> label(VISIBLE_STATUS_LABELS, r.get("customer_visible_status"))),
                col("客户编号", 18, r -> r.get("customer_no")),
                col("客户名称", 28, r -> r.get("customer_name")),
                col("客户类型", 10, r -> label(CUSTOMER_TYPE_LABELS, r.get("customer_type"))),
                col("所属行业", 16, r -> r.get("customer_industry")),
                col("结算主体", 12, r -> label(SETTLEMENT_LABELS, r.get("settlement_type"))),
                col("结算客户名称", 28, r -> settlementName(r)),
                col("联系人姓名", 14, r -> r.get("contact_name")),
                col("联系人电话", 16, r -> r.get("contact_phone")),
                col("联系人职务", 16, r -> r.get("contact_position")),
                col("施工地址", 42, r -> r.get("address")),
                col("经度", 13, r -> r.get("longitude")),
                col("纬度", 13, r -> r.get("latitude")),
                col("用车种类", 12, r -> label(VEHICLE_TYPE_LABELS, r.get("vehicle_type"))),
                col("车牌号", 13, r -> r.get("vehicle_plate_no")),
                col("车辆型号", 20, r -> r.get("vehicle_model")),
                col("车辆吨位", 12, r -> r.get("vehicle_tonnage")),
                col("举升高度（米）", 14, r -> r.get("vehicle_lift_height")),
                col("货物名称", 20, r -> r.get("cargo_name")),
                col("货物数量", 12, r -> r.get("cargo_quantity")),
                col("货物重量（吨）", 14, r -> r.get("cargo_weight")),
                col("吊车吨位", 12, r -> r.get("crane_tonnage")),
                col("预计使用时长（天）", 16, r -> r.get("estimated_days")),
                col("司机编号", 18, r -> r.get("staff_no")),
                col("司机姓名", 14, r -> r.get("staff_name")),
                col("司机手机号", 16, r -> r.get("staff_phone")),
                col("驾驶证类型", 13, r -> r.get("license_type")),
                col("特种作业证号", 20, r -> r.get("special_cert_no")),
                col("计划开始时间", 20, r -> dt(r, "expected_start_at")),
                col("派单/创建时间", 20, r -> dt(r, "created_at")),
                col("接单截止时间", 20, r -> dt(r, "accept_deadline_at")),
                col("实际接单时间", 20, r -> dt(r, "accepted_at")),
                col("实际开始时间", 20, r -> dt(r, "started_at")),
                col("最近暂停时间", 20, r -> dt(r, "suspended_at")),
                col("完成时间", 20, r -> dt(r, "completed_at")),
                col("取消时间", 20, r -> dt(r, "cancelled_at")),
                col("取消原因", 26, r -> r.get("cancelled_reason")),
                col("派单至接单耗时（分钟）", 18, r -> minutesBetween(r.get("created_at"), r.get("accepted_at"))),
                col("首次签到时间", 20, r -> dt(r, "first_check_in_at")),
                col("最后当日签退时间", 20, r -> dt(r, "last_daily_checkout_at")),
                col("任务结束打卡时间", 20, r -> dt(r, "task_end_at")),
                col("总工时（分钟）", 14, r -> r.get("total_minutes")),
                col("加班时长（分钟）", 14, r -> r.get("overtime_minutes")),
                col("签到次数", 12, r -> r.get("check_in_count")),
                col("签退次数", 12, r -> r.get("checkout_count")),
                col("异常次数", 12, r -> r.get("exception_count")),
                col("异常类型汇总", 26, r -> exceptionTypes(r.get("exception_types"))),
                col("客户评分", 10, r -> r.get("review_score")),
                col("客户评价", 32, r -> r.get("review_content")),
                col("报警置顶", 10, r -> yesNo(r.get("is_pinned_alarm"))),
                col("超时提醒时间", 20, r -> dt(r, "timeout_reminded_at")),
                col("任务备注", 32, r -> r.get("remark")),
                col("施工要求", 32, r -> r.get("requirement")),
                col("最后更新时间", 20, r -> dt(r, "updated_at"))
        );
    }

    private List<Column> attendanceColumns() {
        return List.of(
                col("工单号", 20, r -> r.get("order_no")),
                col("司机编号", 18, r -> r.get("staff_no")),
                col("司机姓名", 14, r -> r.get("staff_name")),
                col("打卡类型", 14, r -> label(ATTENDANCE_TYPE_LABELS, r.get("type"))),
                col("打卡时间", 20, r -> dt(r, "checked_at")),
                col("客户端采集时间", 20, r -> dt(r, "captured_at")),
                col("经度", 13, r -> r.get("longitude")),
                col("纬度", 13, r -> r.get("latitude")),
                col("距工单点（米）", 14, r -> r.get("distance_meters")),
                col("是否在范围内", 14, r -> yesNo(r.get("is_in_range"))),
                col("位置是否合规", 14, r -> yesNo(r.get("is_compliant"))),
                col("是否有效", 10, r -> yesNo(r.get("is_valid"))),
                col("上传方式", 12, r -> label(UPLOAD_MODE_LABELS, r.get("upload_mode"))),
                col("照片路径", 40, r -> r.get("photo_urls"))
        );
    }

    private List<Column> dailyCheckoutColumns() {
        return List.of(
                col("工单号", 20, r -> r.get("order_no")),
                col("司机编号", 18, r -> r.get("staff_no")),
                col("司机姓名", 14, r -> r.get("staff_name")),
                col("施工日期", 14, r -> date(r, "work_date")),
                col("当日开始时间", 20, r -> dt(r, "day_start_at")),
                col("当日结束时间", 20, r -> dt(r, "day_end_at")),
                col("距任务点（米）", 14, r -> r.get("distance_meters")),
                col("是否在范围内", 14, r -> yesNo(r.get("is_in_range"))),
                col("车辆是否离场", 14, r -> yesNo(r.get("is_left_site"))),
                col("车辆是否回场", 14, r -> yesNo(r.get("is_returned"))),
                col("照片路径", 40, r -> r.get("photo_urls"))
        );
    }

    private List<Column> exceptionColumns() {
        return List.of(
                col("工单号", 20, r -> r.get("order_no")),
                col("司机编号", 18, r -> r.get("staff_no")),
                col("司机姓名", 14, r -> r.get("staff_name")),
                col("异常类型", 14, r -> label(EXCEPTION_TYPE_LABELS, r.get("type"))),
                col("自定义类型", 18, r -> r.get("type_label")),
                col("异常描述", 40, r -> r.get("description")),
                col("处理状态", 12, r -> label(EXCEPTION_STATUS_LABELS, r.get("status"))),
                col("批复指令", 14, r -> label(EXCEPTION_INSTRUCTION_LABELS, r.get("instruction"))),
                col("上报时工单状态", 16, r -> label(STATUS_LABELS, r.get("order_status_before"))),
                col("批复后工单状态", 16, r -> label(STATUS_LABELS, r.get("order_status_after"))),
                col("是否对客户公开", 14, r -> yesNo(r.get("is_public_to_customer"))),
                col("上报时间", 20, r -> dt(r, "reported_at")),
                col("批复时间", 20, r -> dt(r, "replied_at"))
        );
    }

    private List<Column> statusLogColumns() {
        return List.of(
                col("工单号", 20, r -> r.get("order_no")),
                col("变更前状态", 16, r -> label(STATUS_LABELS, r.get("from_status"))),
                col("变更后状态", 16, r -> label(STATUS_LABELS, r.get("to_status"))),
                col("操作人ID", 14, r -> r.get("operator_id")),
                col("操作说明", 40, r -> r.get("remark")),
                col("变更时间", 20, r -> dt(r, "created_at"))
        );
    }

    private static Column col(String header, int width, Function<Map<String, Object>, Object> getter) {
        return new Column(header, width, getter);
    }

    private static String label(Map<String, String> labels, Object code) {
        if (code == null) {
            return null;
        }
        String key = String.valueOf(code);
        return labels.getOrDefault(key, key);
    }

    private static String settlementName(Map<String, Object> row) {
        if ("PERSONAL".equals(row.get("settlement_type"))) {
            return "个人";
        }
        Object settle = row.get("settlement_customer_name");
        if (settle != null && !String.valueOf(settle).isBlank()) {
            return String.valueOf(settle);
        }
        Object customer = row.get("customer_name");
        return customer == null ? null : String.valueOf(customer);
    }

    private static String exceptionTypes(Object raw) {
        if (raw == null || String.valueOf(raw).isBlank()) {
            return null;
        }
        return Arrays.stream(String.valueOf(raw).split("、"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> EXCEPTION_TYPE_LABELS.getOrDefault(s, s))
                .distinct()
                .collect(Collectors.joining("、"));
    }

    private static String yesNo(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue() == 1 ? "是" : "否";
        }
        return "true".equalsIgnoreCase(String.valueOf(value)) || "1".equals(String.valueOf(value)) ? "是" : "否";
    }

    private static Long minutesBetween(Object start, Object end) {
        LocalDateTime s = toDateTime(start);
        LocalDateTime e = toDateTime(end);
        if (s == null || e == null) {
            return null;
        }
        return Math.max(0, Duration.between(s, e).toMinutes());
    }

    private static LocalDateTime dt(Map<String, Object> row, String key) {
        return toDateTime(row.get(key));
    }

    private static LocalDateTime toDateTime(Object value) {
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime;
        }
        if (value instanceof java.util.Date date) {
            return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        }
        return null;
    }

    private static LocalDate date(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value instanceof Date date) {
            return date.toLocalDate();
        }
        if (value instanceof LocalDate date) {
            return date;
        }
        LocalDateTime dateTime = toDateTime(value);
        return dateTime == null ? null : dateTime.toLocalDate();
    }

    /** 写接口返回体：文件名 + xlsx 字节流 */
    public record ExportFile(String fileName, byte[] content) {
    }

    private record Column(String header, int width, Function<Map<String, Object>, Object> getter) {
    }

    private record Styles(CellStyle title, CellStyle note, CellStyle header, CellStyle data,
                          CellStyle dateTime, CellStyle date, CellStyle number) {
    }
}
