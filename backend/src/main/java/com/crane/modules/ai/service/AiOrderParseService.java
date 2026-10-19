package com.crane.modules.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crane.modules.ai.client.AiChatClient;
import com.crane.modules.ai.dto.AiOrderParseDtos.ParseResultVO;
import com.crane.modules.customer.entity.Customer;
import com.crane.modules.customer.mapper.CustomerMapper;
import com.crane.modules.demand.entity.DemandOrder;
import com.crane.modules.demand.mapper.DemandOrderMapper;
import com.crane.modules.place.entity.CommonPlace;
import com.crane.modules.place.mapper.CommonPlaceMapper;
import com.crane.modules.staff.entity.Staff;
import com.crane.modules.staff.entity.Vehicle;
import com.crane.modules.staff.mapper.StaffMapper;
import com.crane.modules.staff.mapper.VehicleMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 一句话下工单解析服务。
 * 优先调用大模型抽取结构化字段，未配置/调用失败时降级为本地规则解析；
 * 解析结果再与客户、司机、车辆、常用地点、待转需求单做数据库匹配，返回可直接回填表单的字段。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiOrderParseService {

    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Pattern ISO_DATE = Pattern.compile("(\\d{4})-(\\d{1,2})-(\\d{1,2})");
    private static final Pattern CN_DATE = Pattern.compile("(\\d{1,2})月(\\d{1,2})[日号]");
    private static final Pattern REL_DATE = Pattern.compile("(今天|明天|后天|大后天)");
    private static final Pattern TIME_PATTERN = Pattern.compile(
            "(凌晨|早上|上午|中午|下午|晚上)?\\s*(\\d{1,2}|[一二三四五六七八九十]{1,3})\\s*[点时:：]\\s*(\\d{1,2}|半)?");
    private static final Pattern TONNAGE_PATTERN = Pattern.compile(
            "(\\d+(?:\\.\\d+)?|[一二三四五六七八九十]{1,3})\\s*吨");
    private static final Pattern PLATE_PATTERN = Pattern.compile("[\\u4e00-\\u9fa5][A-Z][A-Z0-9]{5,7}");
    private static final Pattern DEMAND_NO_PATTERN = Pattern.compile("XQ\\d{8,20}");
    private static final Pattern CARGO_NAME_PATTERN = Pattern.compile(
            "货物(?:名称)?(?:是|为|：|:)?\\s*([^，,。；;\\d]{1,20}?)(?=，|,|。|；|;|\\d|$)");
    private static final Pattern REMARK_PATTERN = Pattern.compile(
            "(?:备注|注意|要求)(?:是|为|：|:)?\\s*([^。；;]{1,100})");
    private static final Pattern ADDRESS_PATTERN = Pattern.compile(
            "(?:在|地址(?:是|为|：|:)?)\\s*([^，,。；;]{2,60}?)(?=\\s*(?:施工|干活|吊装|派车|派|用|，|,|。|；|;|$))");

    private final AiChatClient aiChatClient;
    private final ObjectMapper objectMapper;
    private final CustomerMapper customerMapper;
    private final StaffMapper staffMapper;
    private final VehicleMapper vehicleMapper;
    private final DemandOrderMapper demandOrderMapper;
    private final CommonPlaceMapper commonPlaceMapper;

    @Transactional(readOnly = true)
    public ParseResultVO parse(String text) {
        String raw = text == null ? "" : text.trim();
        List<Customer> customers = customerMapper.selectList(new LambdaQueryWrapper<Customer>()
                .eq(Customer::getStatus, "ACTIVE"));
        List<Staff> staffs = staffMapper.selectList(new LambdaQueryWrapper<Staff>()
                .eq(Staff::getPosition, "DRIVER")
                .ne(Staff::getStatus, "RESIGNED"));
        List<Vehicle> vehicles = vehicleMapper.selectList(new LambdaQueryWrapper<>());
        List<CommonPlace> places = commonPlaceMapper.selectList(new LambdaQueryWrapper<CommonPlace>()
                .orderByDesc(CommonPlace::getSortOrder));
        List<DemandOrder> pendingDemands = demandOrderMapper.selectList(new LambdaQueryWrapper<DemandOrder>()
                .in(DemandOrder::getStatus, List.of("PENDING", "REVIEWING"))
                .orderByDesc(DemandOrder::getId)
                .last("LIMIT 100"));

        Fields rule = ruleParse(raw);
        LlmFields llm = llmParse(raw, customers, staffs, vehicles, pendingDemands);
        String parseMode = llm != null ? "LLM" : "RULE";
        Fields fields = merge(rule, llm);

        Customer customer = matchCustomer(raw, fields.customerName, customers);
        Staff staff = matchStaff(raw, fields.staffName, staffs);
        Vehicle vehicle = matchVehicle(raw, fields.plateNo, vehicles);
        CommonPlace place = matchPlace(raw, fields.address, places);
        DemandOrder demand = matchDemand(raw, fields, customer, pendingDemands);

        ParseResultVO vo = new ParseResultVO();
        vo.setRawText(raw);
        vo.setParseMode(parseMode);

        String intent = resolveIntent(raw, fields, demand);
        if ("DEMAND_CONVERT".equals(intent) && demand == null) {
            intent = "CREATE_ORDER";
            vo.getWarnings().add("未匹配到可转工单的需求单，已按新建工单处理");
        }
        vo.setIntent(intent);

        if ("DEMAND_CONVERT".equals(intent) && demand != null) {
            if (demand.getCustomerId() != null) {
                Customer demandCustomer = customers.stream()
                        .filter(c -> c.getId().equals(demand.getCustomerId()))
                        .findFirst().orElse(null);
                if (demandCustomer != null) {
                    customer = demandCustomer;
                }
            }
            vo.setDemandId(demand.getId());
            vo.setDemandNo(demand.getDemandNo());
            vo.setAddress(demand.getAddress());
            vo.setLongitude(demand.getLongitude());
            vo.setLatitude(demand.getLatitude());
            vo.setPlanStartAt(fields.planStartAt != null ? fields.planStartAt : futureOrNull(demand.getExpectedStartAt()));
            vo.setCraneTonnage(fields.craneTonnage != null ? fields.craneTonnage : demand.getCraneTonnage());
            vo.setCargoWeight(fields.cargoWeight != null ? fields.cargoWeight : demand.getCargoWeight());
            vo.setCargoName(fields.cargoName);
            vo.setRemark(notBlank(fields.remark) ? fields.remark : demand.getDescription());
            vo.getWarnings().add("已匹配待转需求单 " + demand.getDemandNo() + "，将按需求转工单处理");
        } else {
            vo.setAddress(firstNotBlank(fields.address, place == null ? null : place.getPlaceName(),
                    customer == null ? null : customer.getAddress()));
            if (place != null) {
                vo.setLongitude(place.getLongitude());
                vo.setLatitude(place.getLatitude());
            }
            vo.setPlanStartAt(fields.planStartAt);
            vo.setCraneTonnage(fields.craneTonnage);
            vo.setCargoWeight(fields.cargoWeight);
            vo.setCargoName(fields.cargoName);
            vo.setRemark(fields.remark);
            if (notBlank(vo.getAddress()) && place == null && customer != null
                    && vo.getAddress().equals(customer.getAddress())) {
                vo.getWarnings().add("未识别施工地址，已带入客户档案地址，请核对并地图选点");
            }
        }

        if (customer != null) {
            vo.setCustomerId(customer.getId());
            vo.setCustomerName(customer.getName());
        } else if (notBlank(fields.customerName)) {
            vo.setCustomerName(fields.customerName);
            vo.getWarnings().add("未在客户档案中找到「" + fields.customerName + "」，请在表单中选择");
        }
        if (staff != null) {
            vo.setStaffId(staff.getId());
            vo.setStaffName(staff.getName());
        } else if (notBlank(fields.staffName)) {
            vo.setStaffName(fields.staffName);
            vo.getWarnings().add("未在司机档案中找到「" + fields.staffName + "」，请在表单中选择");
        }
        if (vehicle != null) {
            vo.setVehicleId(vehicle.getId());
            vo.setPlateNo(vehicle.getPlateNo());
        } else if (notBlank(fields.plateNo)) {
            vo.setPlateNo(fields.plateNo);
            vo.getWarnings().add("未在车辆档案中找到车牌「" + fields.plateNo + "」，请在表单中选择");
        }

        if (vo.getPlanStartAt() == null) {
            vo.setPlanStartAt(LocalDate.now().plusDays(1).atTime(8, 0));
            vo.getWarnings().add("未识别到具体施工时间，已默认明天 08:00，请核对");
        } else if (vo.getPlanStartAt().isBefore(LocalDateTime.now())) {
            vo.getWarnings().add("识别到的时间早于当前时间，请在表单中调整");
        }

        fillMissing(vo, intent);
        return vo;
    }

    private void fillMissing(ParseResultVO vo, String intent) {
        if ("DEMAND_CONVERT".equals(intent)) {
            if (vo.getDemandId() == null) vo.getMissingFields().add("需求单");
            if (vo.getStaffId() == null) vo.getMissingFields().add("司机");
            if (vo.getVehicleId() == null) vo.getMissingFields().add("车辆");
            if (vo.getPlanStartAt() == null) vo.getMissingFields().add("计划时间");
            if (vo.getCraneTonnage() == null) vo.getMissingFields().add("吊车吨位");
            return;
        }
        if (vo.getCustomerId() == null) vo.getMissingFields().add("客户");
        if (vo.getStaffId() == null) vo.getMissingFields().add("司机");
        if (vo.getVehicleId() == null) vo.getMissingFields().add("车辆");
        if (!notBlank(vo.getAddress())) vo.getMissingFields().add("施工地址");
        if (vo.getLongitude() == null || vo.getLatitude() == null) vo.getMissingFields().add("经纬度（需地图选点）");
        if (vo.getPlanStartAt() == null) vo.getMissingFields().add("计划时间");
        if (vo.getCraneTonnage() == null) vo.getMissingFields().add("吊车吨位");
    }

    private String resolveIntent(String raw, Fields fields, DemandOrder demand) {
        if (containsAny(raw, "新建工单", "直接下工单", "直接派单", "新下一单", "不用需求")) {
            return "CREATE_ORDER";
        }
        if ("DEMAND_CONVERT".equals(fields.intent)) {
            return "DEMAND_CONVERT";
        }
        if (demand != null) {
            return "DEMAND_CONVERT";
        }
        return "CREATE_ORDER";
    }

    // ==================== 本地规则解析 ====================

    private Fields ruleParse(String text) {
        Fields f = new Fields();
        f.planStartAt = parseTime(text);
        parseTonnage(text, f);
        f.plateNo = extractPlate(text);
        f.address = extractAddress(text);
        f.cargoName = extractCargoName(text);
        f.remark = extractRemark(text);
        if (containsAny(text, "需求", "转工单", "转成工单")) {
            f.intent = "DEMAND_CONVERT";
        }
        return f;
    }

    private LocalDateTime parseTime(String text) {
        LocalDate date = null;
        Matcher iso = ISO_DATE.matcher(text);
        if (iso.find()) {
            date = LocalDate.of(Integer.parseInt(iso.group(1)),
                    Integer.parseInt(iso.group(2)), Integer.parseInt(iso.group(3)));
        } else {
            Matcher cn = CN_DATE.matcher(text);
            if (cn.find()) {
                date = LocalDate.of(LocalDate.now().getYear(),
                        Integer.parseInt(cn.group(1)), Integer.parseInt(cn.group(2)));
            } else {
                Matcher rel = REL_DATE.matcher(text);
                if (rel.find()) {
                    int plus = switch (rel.group(1)) {
                        case "明天" -> 1;
                        case "后天" -> 2;
                        case "大后天" -> 3;
                        default -> 0;
                    };
                    date = LocalDate.now().plusDays(plus);
                }
            }
        }

        LocalTime time = parseTimeOfDay(text);
        if (date == null && time == null) {
            return null;
        }
        if (date == null) {
            LocalDate today = LocalDate.now();
            LocalDateTime candidate = LocalDateTime.of(today, time);
            date = candidate.isBefore(LocalDateTime.now()) ? today.plusDays(1) : today;
        }
        if (time == null) {
            time = LocalTime.of(8, 0);
        }
        return LocalDateTime.of(date, time);
    }

    private LocalTime parseTimeOfDay(String text) {
        Matcher m = TIME_PATTERN.matcher(text);
        while (m.find()) {
            String period = m.group(1);
            int hour = cnNumber(m.group(2));
            if (hour < 0 || hour > 23) continue;
            int minute = 0;
            String minGroup = m.group(3);
            if ("半".equals(minGroup)) {
                minute = 30;
            } else if (minGroup != null) {
                minute = cnNumber(minGroup);
            }
            if (minute < 0 || minute > 59) continue;
            if (("下午".equals(period) || "晚上".equals(period)) && hour < 12) {
                hour += 12;
            } else if ("中午".equals(period) && hour < 11) {
                hour += 12;
            } else if ("凌晨".equals(period) && hour == 12) {
                hour = 0;
            }
            if (hour > 23) continue;
            return LocalTime.of(hour, minute);
        }
        return null;
    }

    private void parseTonnage(String text, Fields f) {
        Matcher m = TONNAGE_PATTERN.matcher(text);
        while (m.find()) {
            int numeric = cnNumber(m.group(1));
            if (numeric <= 0) continue;
            BigDecimal value = new BigDecimal(numeric);
            int start = Math.max(0, m.start() - 10);
            String before = text.substring(start, m.start());
            String after = text.substring(m.end(), Math.min(text.length(), m.end() + 5));
            boolean cargo = before.contains("货物") || before.contains("货重");
            boolean crane = before.contains("吊车") || before.contains("汽车吊") || before.contains("起重机")
                    || before.contains("设备") || before.contains("吨位") || before.contains("派")
                    || after.startsWith("吊车") || after.startsWith("汽车吊") || after.startsWith("起重机");
            if (cargo) {
                if (f.cargoWeight == null) f.cargoWeight = value;
            } else if (crane) {
                if (f.craneTonnage == null) f.craneTonnage = value;
            } else if (f.craneTonnage == null) {
                f.craneTonnage = value;
            } else if (f.cargoWeight == null) {
                f.cargoWeight = value;
            }
        }
    }

    private String extractPlate(String text) {
        Matcher m = PLATE_PATTERN.matcher(text);
        return m.find() ? m.group() : null;
    }

    private String extractAddress(String text) {
        Matcher m = ADDRESS_PATTERN.matcher(text);
        return m.find() ? m.group(1).trim() : null;
    }

    private String extractCargoName(String text) {
        Matcher m = CARGO_NAME_PATTERN.matcher(text);
        if (m.find()) {
            String value = m.group(1).trim();
            value = value.replaceFirst("^(约|大概|大约|左右|是|为)\\s*", "").trim();
            return value.isBlank() ? null : value;
        }
        return null;
    }

    private String extractRemark(String text) {
        Matcher m = REMARK_PATTERN.matcher(text);
        return m.find() ? m.group(1).trim() : null;
    }

    // ==================== 大模型解析（可选） ====================

    private LlmFields llmParse(String text, List<Customer> customers, List<Staff> staffs,
                               List<Vehicle> vehicles, List<DemandOrder> demands) {
        AiChatClient.AiConfig config = aiChatClient.loadConfig();
        if (!config.enabled() || config.mock() || !config.callable()) {
            return null;
        }
        try {
            String systemPrompt = """
                    你是吊装公司的「一句话下工单」助手。请从管理员口述中提取工单字段，只输出一个 JSON 对象，不要 Markdown、不要解释。
                    JSON 字段：
                    intent：CREATE_ORDER（新建工单）或 DEMAND_CONVERT（需求转工单）
                    demandNo：提到的需求单号，没有则为空
                    customerName：客户名称
                    staffName：司机姓名
                    plateNo：车辆车牌
                    address：施工地址
                    planStartAt：yyyy-MM-ddTHH:mm:ss
                    craneTonnage：吊车吨位（吨，数字）
                    cargoWeight：货物重量（吨，数字）
                    cargoName：货物名称
                    remark：备注
                    要求：以当前时间为基准解析今天/明天/后天、上午/下午等时间；不确定的字段输出空字符串或 null；不得编造客户、司机、车牌。
                    """;
            String userPrompt = "当前时间：" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                    + "\n客户档案：" + customers.stream().limit(150).map(Customer::getName)
                    .collect(Collectors.joining("、"))
                    + "\n司机档案：" + staffs.stream().limit(150).map(Staff::getName)
                    .collect(Collectors.joining("、"))
                    + "\n车辆档案：" + vehicles.stream().limit(150).map(Vehicle::getPlateNo)
                    .collect(Collectors.joining("、"))
                    + "\n待转需求单：" + demands.stream().limit(100)
                    .map(d -> d.getDemandNo() + "|" + d.getAddress())
                    .collect(Collectors.joining("；"))
                    + "\n管理员口述：" + text;
            String reply = aiChatClient.chat(systemPrompt, userPrompt);
            String json = extractJson(reply);
            if (json == null) {
                return null;
            }
            return objectMapper.readValue(json, LlmFields.class);
        } catch (Exception e) {
            log.warn("一句话下工单大模型解析失败，降级本地规则: {}", e.getMessage());
            return null;
        }
    }

    private String extractJson(String reply) {
        if (reply == null) return null;
        String trimmed = reply.trim();
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start < 0 || end <= start) return null;
        return trimmed.substring(start, end + 1);
    }

    private Fields merge(Fields rule, LlmFields llm) {
        Fields f = rule;
        if (llm == null) {
            return f;
        }
        if (notBlank(llm.getIntent())) {
            String intent = llm.getIntent().trim().toUpperCase(Locale.ROOT);
            if ("CREATE_ORDER".equals(intent) || "DEMAND_CONVERT".equals(intent)) {
                f.intent = intent;
            }
        }
        if (notBlank(llm.getCustomerName())) f.customerName = llm.getCustomerName().trim();
        if (notBlank(llm.getStaffName())) f.staffName = llm.getStaffName().trim();
        if (notBlank(llm.getPlateNo())) f.plateNo = llm.getPlateNo().trim().toUpperCase(Locale.ROOT);
        if (notBlank(llm.getAddress())) f.address = llm.getAddress().trim();
        if (notBlank(llm.getCargoName())) f.cargoName = llm.getCargoName().trim();
        if (notBlank(llm.getRemark())) f.remark = llm.getRemark().trim();
        if (llm.getCraneTonnage() != null) f.craneTonnage = llm.getCraneTonnage();
        if (llm.getCargoWeight() != null) f.cargoWeight = llm.getCargoWeight();
        if (notBlank(llm.getPlanStartAt()) && f.planStartAt == null) {
            f.planStartAt = parseDateTime(llm.getPlanStartAt());
        }
        return f;
    }

    private LocalDateTime parseDateTime(String value) {
        String v = value.trim().replace(' ', 'T');
        try {
            return LocalDateTime.parse(v);
        } catch (Exception ignored) {
            try {
                return LocalDateTime.parse(v, DATE_TIME_FMT);
            } catch (Exception e) {
                return null;
            }
        }
    }

    // ==================== 数据库匹配 ====================

    private Customer matchCustomer(String text, String hint, List<Customer> customers) {
        if (notBlank(hint)) {
            Customer byHint = matchCustomerName(hint, customers);
            if (byHint != null) return byHint;
        }
        List<Customer> sorted = new ArrayList<>(customers);
        sorted.sort(Comparator.comparingInt((Customer c) -> c.getName() == null ? 0 : c.getName().length()).reversed());
        for (Customer c : sorted) {
            if (c.getName() != null && text.contains(c.getName())) return c;
        }
        for (Customer c : sorted) {
            String shortName = shortCustomerName(c.getName());
            if (shortName.length() >= 3 && text.contains(shortName)) return c;
        }
        return null;
    }

    private Customer matchCustomerName(String hint, List<Customer> customers) {
        for (Customer c : customers) {
            if (hint.equals(c.getName())) return c;
        }
        for (Customer c : customers) {
            if (c.getName() != null && (hint.contains(c.getName()) || c.getName().contains(hint))) return c;
        }
        return null;
    }

    private String shortCustomerName(String name) {
        if (name == null) return "";
        return name.replaceAll("(有限责任公司|有限公司|股份有限公司|集团|公司)$", "").trim();
    }

    private Staff matchStaff(String text, String hint, List<Staff> staffs) {
        if (notBlank(hint)) {
            for (Staff s : staffs) {
                if (hint.equals(s.getName())) return s;
            }
        }
        List<Staff> sorted = new ArrayList<>(staffs);
        sorted.sort(Comparator.comparingInt((Staff s) -> s.getName() == null ? 0 : s.getName().length()).reversed());
        for (Staff s : sorted) {
            if (s.getName() != null && text.contains(s.getName())) return s;
        }
        if (notBlank(hint)) {
            for (Staff s : staffs) {
                if (s.getName() != null && (hint.contains(s.getName()) || s.getName().contains(hint))) return s;
            }
        }
        return null;
    }

    private Vehicle matchVehicle(String text, String hint, List<Vehicle> vehicles) {
        String plate = notBlank(hint) ? hint : null;
        if (plate == null) {
            Matcher m = PLATE_PATTERN.matcher(text);
            plate = m.find() ? m.group() : null;
        }
        if (plate != null) {
            String target = plate.toUpperCase(Locale.ROOT);
            for (Vehicle v : vehicles) {
                if (v.getPlateNo() != null && v.getPlateNo().toUpperCase(Locale.ROOT).equals(target)) return v;
            }
            for (Vehicle v : vehicles) {
                if (v.getPlateNo() != null && target.contains(v.getPlateNo().toUpperCase(Locale.ROOT))) return v;
            }
        }
        for (Vehicle v : vehicles) {
            if (v.getPlateNo() != null && text.contains(v.getPlateNo())) return v;
        }
        return null;
    }

    private CommonPlace matchPlace(String text, String addressHint, List<CommonPlace> places) {
        List<CommonPlace> sorted = new ArrayList<>(places);
        sorted.sort(Comparator.comparingInt((CommonPlace p) -> p.getPlaceName() == null ? 0 : p.getPlaceName().length())
                .reversed());
        for (CommonPlace p : sorted) {
            if (p.getPlaceName() != null && text.contains(p.getPlaceName())) return p;
        }
        if (notBlank(addressHint)) {
            for (CommonPlace p : sorted) {
                if (p.getPlaceName() != null
                        && (addressHint.contains(p.getPlaceName()) || p.getPlaceName().contains(addressHint))) {
                    return p;
                }
            }
        }
        return null;
    }

    private DemandOrder matchDemand(String text, Fields fields, Customer customer, List<DemandOrder> pendingDemands) {
        Matcher noMatcher = DEMAND_NO_PATTERN.matcher(text);
        if (noMatcher.find()) {
            String demandNo = noMatcher.group();
            for (DemandOrder d : pendingDemands) {
                if (demandNo.equals(d.getDemandNo())) return d;
            }
            DemandOrder byNo = demandOrderMapper.selectOne(new LambdaQueryWrapper<DemandOrder>()
                    .eq(DemandOrder::getDemandNo, demandNo)
                    .in(DemandOrder::getStatus, List.of("PENDING", "REVIEWING"))
                    .last("LIMIT 1"));
            if (byNo != null) return byNo;
        }
        if (customer == null) {
            return null;
        }
        List<DemandOrder> own = pendingDemands.stream()
                .filter(d -> customer.getId().equals(d.getCustomerId()))
                .toList();
        if (own.isEmpty()) {
            return null;
        }
        if (containsAny(text, "需求", "转工单", "转成工单") || "DEMAND_CONVERT".equals(fields.intent)) {
            return own.get(0);
        }
        if (notBlank(fields.address)) {
            for (DemandOrder d : own) {
                if (addressMatches(fields.address, d.getAddress())) return d;
            }
        }
        return null;
    }

    private boolean addressMatches(String a, String b) {
        if (!notBlank(a) || !notBlank(b)) return false;
        String x = a.replaceAll("\\s+", "");
        String y = b.replaceAll("\\s+", "");
        return x.contains(y) || y.contains(x);
    }

    // ==================== 工具 ====================

    private int cnNumber(String value) {
        if (value == null || value.isBlank()) return -1;
        String v = value.trim();
        if (v.matches("\\d+")) {
            return Integer.parseInt(v);
        }
        int tenIndex = v.indexOf('十');
        if (tenIndex >= 0) {
            int tens = tenIndex == 0 ? 1 : digit(v.charAt(tenIndex - 1));
            int ones = tenIndex == v.length() - 1 ? 0 : digit(v.charAt(tenIndex + 1));
            return tens * 10 + ones;
        }
        if (v.length() == 1) {
            return digit(v.charAt(0));
        }
        return -1;
    }

    private int digit(char ch) {
        return switch (ch) {
            case '一' -> 1;
            case '二', '两' -> 2;
            case '三' -> 3;
            case '四' -> 4;
            case '五' -> 5;
            case '六' -> 6;
            case '七' -> 7;
            case '八' -> 8;
            case '九' -> 9;
            default -> 0;
        };
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) return true;
        }
        return false;
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private String firstNotBlank(String... values) {
        for (String value : values) {
            if (notBlank(value)) return value;
        }
        return null;
    }

    private LocalDateTime futureOrNull(LocalDateTime value) {
        return value != null && value.isAfter(LocalDateTime.now()) ? value : null;
    }

    private static class Fields {
        private String intent;
        private String customerName;
        private String staffName;
        private String plateNo;
        private String address;
        private LocalDateTime planStartAt;
        private BigDecimal craneTonnage;
        private BigDecimal cargoWeight;
        private String cargoName;
        private String remark;
    }

    @Data
    public static class LlmFields {
        private String intent;
        private String demandNo;
        private String customerName;
        private String staffName;
        private String plateNo;
        private String address;
        private String planStartAt;
        private BigDecimal craneTonnage;
        private BigDecimal cargoWeight;
        private String cargoName;
        private String remark;
    }
}
