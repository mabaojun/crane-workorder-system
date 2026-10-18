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
        vehicle.setTonnage(req.getTonnage());
        vehicle.setDefaultStaffId(req.getDefaultStaffId());
        vehicle.setRemark(req.getRemark());
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
