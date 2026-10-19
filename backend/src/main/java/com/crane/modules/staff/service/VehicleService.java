package com.crane.modules.staff.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crane.common.exception.BizException;
import com.crane.common.exception.ErrorCode;
import com.crane.common.result.PageResult;
import com.crane.modules.staff.dto.VehicleUpsertRequest;
import com.crane.modules.staff.entity.Vehicle;
import com.crane.modules.staff.entity.VehicleStatusLog;
import com.crane.modules.staff.mapper.StaffMapper;
import com.crane.modules.staff.mapper.VehicleMapper;
import com.crane.modules.staff.mapper.VehicleStatusLogMapper;
import com.crane.modules.staff.entity.Staff;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 车辆档案服务：车辆增删改查 + 内部状态变更（写状态日志）。
 * 派单 / 打卡域驱动状态流转时统一经 changeStatus 落日志，保证 biz_vehicle_status_log 完整。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VehicleService {

    /** 车辆状态合法取值（01 §6.3 锁定） */
    private static final Set<String> STATUSES = Set.of("IDLE", "BUSY", "BUSY_THIRD", "MAINTENANCE", "DISABLED");

    /** 车辆类型合法取值 */
    private static final Set<String> TYPES = Set.of("CRANE", "LIFT");

    /** 司机端允许手动切换的目标状态（停用仅管理员） */
    private static final Set<String> DRIVER_ALLOWED_TARGETS = Set.of("IDLE", "BUSY_THIRD", "MAINTENANCE");

    private final VehicleMapper vehicleMapper;
    private final VehicleStatusLogMapper vehicleStatusLogMapper;
    private final StaffMapper staffMapper;

    /**
     * 分页查询（plateNo 模糊 / status 过滤），分页参数校验见 03 §1.4。
     */
    public PageResult<Vehicle> page(int pageNum, int pageSize, String plateNo, String status) {
        checkPage(pageNum, pageSize);
        LambdaQueryWrapper<Vehicle> wrapper = new LambdaQueryWrapper<Vehicle>()
                .like(plateNo != null && !plateNo.isBlank(), Vehicle::getPlateNo, plateNo)
                .eq(status != null && !status.isBlank(), Vehicle::getStatus, status)
                .orderByDesc(Vehicle::getId);
        long total = vehicleMapper.selectCount(wrapper);
        int offset = (pageNum - 1) * pageSize;
        List<Vehicle> list = vehicleMapper.selectList(wrapper.last("LIMIT " + offset + "," + pageSize));
        return PageResult.of(total, list);
    }

    /**
     * 创建车辆档案：plateNo 唯一；状态缺省 IDLE；默认司机可空，提供时须存在。
     */
    @Transactional
    public Vehicle create(VehicleUpsertRequest req, Long operatorId) {
        checkStatus(req.getStatus());
        checkPlateNoUnique(req.getPlateNo(), null);
        checkDefaultStaff(req.getDefaultStaffId());

        Vehicle vehicle = new Vehicle();
        applyUpsert(vehicle, req);
        vehicle.setStatus(req.getStatus() == null || req.getStatus().isBlank() ? "IDLE" : req.getStatus());
        vehicle.setIsAway(0);
        vehicle.setCreatedBy(operatorId);
        vehicle.setUpdatedBy(operatorId);
        vehicleMapper.insert(vehicle);
        return vehicle;
    }

    /**
     * 更新车辆档案（全量字段）。currentOrderId、thirdPartyMarkedAt、isAway 为运行时字段，档案更新不覆盖。
     */
    @Transactional
    public Vehicle update(Long id, VehicleUpsertRequest req, Long operatorId) {
        Vehicle vehicle = vehicleMapper.selectById(id);
        if (vehicle == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "车辆不存在");
        }
        checkStatus(req.getStatus());
        checkPlateNoUnique(req.getPlateNo(), id);
        checkDefaultStaff(req.getDefaultStaffId());
        applyUpsert(vehicle, req);
        if (req.getStatus() != null && !req.getStatus().isBlank()) {
            vehicle.setStatus(req.getStatus());
        }
        vehicle.setUpdatedBy(operatorId);
        vehicleMapper.updateById(vehicle);
        return vehicle;
    }

    /**
     * 逻辑删除车辆档案。
     */
    @Transactional
    public void delete(Long id, Long operatorId) {
        Vehicle vehicle = vehicleMapper.selectById(id);
        if (vehicle == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "车辆不存在");
        }
        vehicleMapper.deleteById(id);
    }

    /**
     * 内部状态变更（供派单 / 打卡 / 管理端维护使用）：更新车辆状态并写状态日志。
     * 目标 BUSY_THIRD 时记录第三方标记时刻；BUSY_THIRD 恢复 IDLE 时回填外部占用时长。
     */
    @Transactional
    public Vehicle changeStatus(Long vehicleId, String status, Long operatorId) {
        changeStatus(vehicleId, status, operatorId, null, null);
        return vehicleMapper.selectById(vehicleId);
    }

    /**
     * 带上下文的状态变更：可携带关联工单、操作司机与备注，供派单域复用。
     */
    @Transactional
    public Vehicle changeStatus(Long vehicleId, String status, Long operatorId, Long orderId, Long staffId) {
        Vehicle vehicle = vehicleMapper.selectById(vehicleId);
        if (vehicle == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "车辆不存在");
        }
        if (!STATUSES.contains(status)) {
            throw new BizException(ErrorCode.ENUM_INVALID, "车辆状态取值非法");
        }
        String before = vehicle.getStatus();
        if (status.equals(before)) {
            return vehicle;
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime markedAt = vehicle.getThirdPartyMarkedAt();
        vehicle.setStatus(status);
        if ("BUSY_THIRD".equals(status)) {
            // 进入第三方派单：记录标记时刻，供外部占用时长与超阈值提醒巡检使用
            vehicle.setThirdPartyMarkedAt(now);
        } else if ("BUSY_THIRD".equals(before)) {
            // 退出第三方派单：清空标记时刻，占用时长回填到状态日志
            vehicle.setThirdPartyMarkedAt(null);
        }
        vehicle.setUpdatedBy(operatorId);
        vehicleMapper.updateById(vehicle);

        VehicleStatusLog statusLog = new VehicleStatusLog();
        statusLog.setVehicleId(vehicleId);
        statusLog.setBeforeStatus(before);
        statusLog.setAfterStatus(status);
        statusLog.setChangeType(resolveChangeType(before, status));
        statusLog.setOrderId(orderId);
        statusLog.setStaffId(staffId);
        statusLog.setOperatorId(operatorId);
        statusLog.setOccurAt(now);
        if ("BUSY_THIRD".equals(before)) {
            statusLog.setExternalOccupyMinutes(externalMinutes(markedAt, now));
        }
        vehicleStatusLogMapper.insert(statusLog);
        return vehicle;
    }

    /** 状态到变更类型的映射（02 §3.3.19 字面量） */
    private String resolveChangeType(String before, String after) {
        return switch (after) {
            case "BUSY" -> "DISPATCH";
            case "IDLE" -> "BUSY_THIRD".equals(before) ? "THIRD_PARTY_RESUME" : "RELEASE";
            case "BUSY_THIRD" -> "THIRD_PARTY_MARK";
            case "MAINTENANCE" -> "MAINTENANCE";
            case "DISABLED" -> "DISABLE";
            default -> "RELEASE";
        };
    }

    private Integer externalMinutes(LocalDateTime markedAt, LocalDateTime now) {
        if (markedAt == null) {
            return null;
        }
        return (int) Duration.between(markedAt, now).toMinutes();
    }

    private void applyUpsert(Vehicle vehicle, VehicleUpsertRequest req) {
        vehicle.setPlateNo(req.getPlateNo());
        vehicle.setModel(req.getModel());
        vehicle.setDefaultStaffId(req.getDefaultStaffId());
        vehicle.setRemark(req.getRemark());
        // 车辆类型规格：吊车维护吨位、举升车维护举升高度，互斥清理
        String type = req.getType() == null || req.getType().isBlank() ? "CRANE" : req.getType();
        if (!TYPES.contains(type)) {
            throw new BizException(ErrorCode.ENUM_INVALID, "车辆类型取值非法");
        }
        vehicle.setType(type);
        if ("LIFT".equals(type)) {
            if (req.getLiftHeight() == null) {
                throw new BizException(ErrorCode.PARAM_MISSING, "举升车必须填写举升高度");
            }
            vehicle.setLiftHeight(req.getLiftHeight());
            vehicle.setTonnage(null);
        } else {
            if (req.getTonnage() == null) {
                throw new BizException(ErrorCode.PARAM_MISSING, "吊车必须填写吨位");
            }
            vehicle.setTonnage(req.getTonnage());
            vehicle.setLiftHeight(null);
        }
    }

    /**
     * 管理员手动变更车辆状态：BUSY（作业中，由工单驱动）不允许手动改，
     * 其余 IDLE/BUSY_THIRD/MAINTENANCE/DISABLED 可自由切换，状态日志 changeType 走 MANUAL 前缀语义由 resolveChangeType 承担。
     */
    @Transactional
    public Vehicle manualChangeStatus(Long vehicleId, String status, Long operatorId) {
        if (!STATUSES.contains(status)) {
            throw new BizException(ErrorCode.ENUM_INVALID, "车辆状态取值非法");
        }
        if ("BUSY".equals(status)) {
            throw new BizException(ErrorCode.RESOURCE_STATUS_NOT_ALLOWED, "作业中状态由工单自动驱动，不可手动设置");
        }
        Vehicle vehicle = vehicleMapper.selectById(vehicleId);
        if (vehicle == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "车辆不存在");
        }
        if ("BUSY".equals(vehicle.getStatus())) {
            throw new BizException(ErrorCode.RESOURCE_STATUS_NOT_ALLOWED, "车辆作业中，请先完成工单再变更状态");
        }
        return changeStatus(vehicleId, status, operatorId);
    }

    /**
     * 司机变更常用设备状态：仅限本人为默认司机的车辆；
     * 目标仅 空闲/第三方派单外出/维保（停用仅管理员）；作业中车辆不可动。
     */
    @Transactional
    public Vehicle driverChangeStatus(Long vehicleId, String status, Long staffId, Long operatorId) {
        if (!DRIVER_ALLOWED_TARGETS.contains(status)) {
            throw new BizException(ErrorCode.ENUM_INVALID, "司机仅可切换 空闲/第三方派单外出/维保 状态");
        }
        Vehicle vehicle = vehicleMapper.selectById(vehicleId);
        if (vehicle == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "车辆不存在");
        }
        if (!staffId.equals(vehicle.getDefaultStaffId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅可变更本人常用设备的状态");
        }
        if ("BUSY".equals(vehicle.getStatus())) {
            throw new BizException(ErrorCode.RESOURCE_STATUS_NOT_ALLOWED, "车辆作业中，状态由工单自动驱动");
        }
        return changeStatus(vehicleId, status, operatorId, null, staffId);
    }

    /** 司机常用设备：本人为默认司机的车辆列表（含状态），供司机端主页维护状态 */
    public List<Vehicle> listMyVehicles(Long staffId) {
        return vehicleMapper.selectList(new LambdaQueryWrapper<Vehicle>()
                .eq(Vehicle::getDefaultStaffId, staffId)
                .orderByDesc(Vehicle::getId));
    }

    private void checkStatus(String status) {
        if (status != null && !status.isBlank() && !STATUSES.contains(status)) {
            throw new BizException(ErrorCode.ENUM_INVALID, "车辆状态取值非法");
        }
    }

    private void checkPlateNoUnique(String plateNo, Long excludeId) {
        Long count = vehicleMapper.selectCount(new LambdaQueryWrapper<Vehicle>()
                .eq(Vehicle::getPlateNo, plateNo)
                .ne(excludeId != null, Vehicle::getId, excludeId));
        if (count != null && count > 0) {
            throw new BizException(ErrorCode.RESOURCE_STATUS_NOT_ALLOWED, "车牌号已存在");
        }
    }

    private void checkDefaultStaff(Long defaultStaffId) {
        if (defaultStaffId == null) {
            return;
        }
        Staff staff = staffMapper.selectById(defaultStaffId);
        if (staff == null) {
            throw new BizException(ErrorCode.STAFF_NOT_FOUND, "默认司机不存在");
        }
    }

    private void checkPage(int pageNum, int pageSize) {
        if (pageNum < 1 || pageSize < 1 || pageSize > 100) {
            throw new BizException(ErrorCode.PAGE_INVALID);
        }
    }
}
