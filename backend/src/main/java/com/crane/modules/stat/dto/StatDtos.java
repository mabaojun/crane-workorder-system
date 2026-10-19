package com.crane.modules.stat.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 统计域响应载荷集合：数据驾驶舱四大接口（总览、报警、车辆地图、员工绩效）的对接契约。
 * 字段名与前端大屏约定严格一致，勿随意改名。
 */
public final class StatDtos {

    private StatDtos() {
    }

    /** 驾驶舱总览响应 */
    @Data
    public static class DashboardVO {
        private OrderStat order;
        private List<TrendPoint> trend7d;
        private List<ExceptionTypeCount> exceptionDistribution;
        private VehicleStat vehicle;
        private StaffStat staff;
        private CustomerStat customer;
        private List<PendingDemand> pendingDemands;
        private List<StaffRank> staffRank;
    }

    /** 工单统计块 */
    @Data
    public static class OrderStat {
        private int today;
        private int week;
        private int month;
        private int inProgress;
        private int suspended;
        private int completed;
        private int pendingAccept;
        private int pendingArrive;
        private int cancelled;
        /** 六态全量状态分布 */
        private List<StatusCount> statusDistribution;
    }

    /** 状态分布项 */
    @Data
    public static class StatusCount {
        private String status;
        private int count;

        public StatusCount() {
        }

        public StatusCount(String status, int count) {
            this.status = status;
            this.count = count;
        }
    }

    /** 近 7 日趋势项 */
    @Data
    public static class TrendPoint {
        /** 日期，MM-dd */
        private String date;
        private int count;

        public TrendPoint() {
        }

        public TrendPoint(String date, int count) {
            this.date = date;
            this.count = count;
        }
    }

    /** 当日异常类型分布项 */
    @Data
    public static class ExceptionTypeCount {
        private String type;
        private String label;
        private int count;

        public ExceptionTypeCount() {
        }

        public ExceptionTypeCount(String type, String label, int count) {
            this.type = type;
            this.label = label;
            this.count = count;
        }
    }

    /** 车辆统计块 */
    @Data
    public static class VehicleStat {
        private int total;
        private int idle;
        private int busy;
        private int busyThird;
        private int maintenance;
        private int disabled;
        /** 当前处于 BUSY_THIRD 的车辆数 */
        private long externalOccupyVehicles;
        /** 当日 BUSY_THIRD 车辆外部占用分钟数求和 */
        private long externalOccupyMinutesToday;
    }

    /** 员工统计块 */
    @Data
    public static class StaffStat {
        private int total;
        private int onDuty;
        private int busy;
        private int idle;
        private int away;
    }

    /** 客户与需求统计块 */
    @Data
    public static class CustomerStat {
        private int newThisMonth;
        private int demandMonth;
        private int demandPending;
    }

    /** 待处理需求单摘要项 */
    @Data
    public static class PendingDemand {
        private String demandNo;
        /** 需求描述截断 60 字 */
        private String title;
        private String contactName;
        private String status;
        private LocalDateTime createdAt;
    }

    /** 本月司机工单量排名项 */
    @Data
    public static class StaffRank {
        private Long staffId;
        private String name;
        private int orderCount;
        private int completedCount;

        public StaffRank() {
        }

        public StaffRank(Long staffId, String name, int orderCount, int completedCount) {
            this.staffId = staffId;
            this.name = name;
            this.orderCount = orderCount;
            this.completedCount = completedCount;
        }
    }

    /** 报警中心响应 */
    @Data
    public static class AlarmListVO {
        private int count;
        private List<AlarmItem> list;
    }

    /** 报警项 */
    @Data
    public static class AlarmItem {
        /** 报警编号，AL- 前缀手工拼接 */
        private String alarmNo;
        /** CONFLICT / NOT_ACCEPTED / NOT_ARRIVED / NOT_CHECKED_IN / VEHICLE_OCCUPIED / VEHICLE_INSURANCE_EXPIRING / VEHICLE_INSPECTION_EXPIRING */
        private String type;
        /** HIGH / MEDIUM */
        private String level;
        /** STAFF / ORDER / VEHICLE */
        private String targetType;
        private Long orderId;
        private String orderNo;
        private Long vehicleId;
        private String plateNo;
        private Long staffId;
        private String staffName;
        private String summary;
        private LocalDateTime triggeredAt;
        private long durationMinutes;
        private boolean pinned;
    }

    /** 车辆地图响应 */
    @Data
    public static class VehicleMapVO {
        private int total;
        private int onMap;
        private List<MapPoint> points;
    }

    /** 车辆地图点位 */
    @Data
    public static class MapPoint {
        private Long vehicleId;
        private String plateNo;
        private String staffName;
        private String orderNo;
        private String status;
        private BigDecimal lng;
        private BigDecimal latitude;
        private boolean alarm;
        private LocalDateTime updatedAt;
    }

    /** 员工绩效项 */
    @Data
    public static class StaffPerformance {
        private Long staffId;
        private String name;
        private int orderCount;
        private int completedCount;
        /** 完工率百分比，两位小数；orderCount 为 0 时为 null */
        private BigDecimal completionRate;

        public StaffPerformance() {
        }

        public StaffPerformance(Long staffId, String name, int orderCount, int completedCount, BigDecimal completionRate) {
            this.staffId = staffId;
            this.name = name;
            this.orderCount = orderCount;
            this.completedCount = completedCount;
            this.completionRate = completionRate;
        }
    }
}
