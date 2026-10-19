package com.crane.modules.stat.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 工作台状态看板响应：按日期聚合的人员 / 车辆 / 工单三种视角，共用同一份数据源。
 */
public final class StatusBoardDtos {

    private StatusBoardDtos() {
    }

    /** 状态看板整体响应 */
    @Data
    public static class BoardVO {
        /** 查询日期 yyyy-MM-dd */
        private String date;
        /** 当日工单数 */
        private int totalOrders;
        private List<StaffStatus> staff;
        private List<VehicleStatus> vehicles;
        /** 当日工单（按计划开始时间升序） */
        private List<OrderStatus> orders;
    }

    /** 人员视角：一个人当日做什么、用什么车、持续多久 */
    @Data
    public static class StaffStatus {
        private Long staffId;
        private String name;
        private String phone;
        private String position;
        /** IDLE / BUSY / AWAY / RESIGNED */
        private String status;
        /** 当前进行中的工单（无则为空） */
        private OrderStatus currentOrder;
        /** 当日工单合计持续分钟 */
        private long totalMinutes;
        private List<OrderStatus> orders;
    }

    /** 车辆视角：车辆在干什么活、最新签到位置 */
    @Data
    public static class VehicleStatus {
        private Long vehicleId;
        private String plateNo;
        private String type;
        private BigDecimal tonnage;
        private BigDecimal liftHeight;
        /** IDLE / BUSY / BUSY_THIRD / MAINTENANCE / DISABLED */
        private String status;
        private Long currentOrderId;
        private OrderStatus currentOrder;
        /** 最新一次到场签到位置 */
        private CheckIn latestCheckIn;
        private List<OrderStatus> orders;
    }

    /** 工单视角：围绕时间的持续状态 */
    @Data
    public static class OrderStatus {
        private Long id;
        private String orderNo;
        private String taskName;
        private Long customerId;
        private String customerName;
        private Long staffId;
        private String staffName;
        private Long vehicleId;
        private String vehiclePlateNo;
        private String vehicleType;
        private BigDecimal tonnage;
        private BigDecimal liftHeight;
        private String status;
        private String address;
        private LocalDateTime expectedStartAt;
        private LocalDateTime acceptedAt;
        private LocalDateTime startedAt;
        private LocalDateTime completedAt;
        private LocalDateTime cancelledAt;
        /** 实际持续分钟：开工时间 → 完工时间（未完工算至当前） */
        private Long durationMinutes;
        /** 是否仍在进行（含待接单/待到场/进行中/受阻） */
        private boolean ongoing;
    }

    /** 最新签到位置 */
    @Data
    public static class CheckIn {
        private BigDecimal lng;
        private BigDecimal latitude;
        private LocalDateTime checkedAt;
        private Integer distanceMeters;
    }
}
