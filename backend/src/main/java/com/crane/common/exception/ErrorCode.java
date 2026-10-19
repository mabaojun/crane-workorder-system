package com.crane.common.exception;

import lombok.Getter;

/**
 * 统一错误码，字面与分段与 03 §5 完全一致：
 * 1xxxx 认证鉴权、2xxxx 参数校验、3xxxx 业务规则、4xxxx 资源状态、5xxxx 系统与第三方。
 */
@Getter
public enum ErrorCode {

    // 1xxxx 认证鉴权
    UNAUTHORIZED(10001, "未登录或令牌缺失"),
    TOKEN_INVALID(10002, "令牌无效或已过期"),
    FORBIDDEN(10003, "无权限访问该资源"),
    TOKEN_REVOKED(10004, "令牌已失效"),
    ACCOUNT_DISABLED(10005, "账号已停用或员工已离职"),
    WECHAT_CODE_INVALID(10006, "微信登录 code 无效或已使用"),
    ACCOUNT_NOT_OPENED(10007, "账号未开通，请联系管理员"),
    PHONE_ALREADY_BOUND(10008, "手机号已绑定其他账号"),
    SMS_CODE_INVALID(10009, "短信验证码错误或已过期"),
    BAD_CREDENTIALS(10010, "账号或密码错误"),

    // 2xxxx 参数校验
    PARAM_MISSING(20001, "必填参数缺失"),
    PARAM_INVALID(20002, "参数格式非法"),
    PAGE_INVALID(20003, "分页参数越界或非法"),
    TIME_INVALID(20004, "时间格式非法"),
    VALUE_OUT_OF_RANGE(20005, "字段长度或取值范围超出限制"),
    ATTACHMENT_INVALID(20006, "附件规格不符"),
    COORDINATE_INVALID(20007, "经纬度坐标非法"),
    ENUM_INVALID(20008, "枚举取值非法"),

    // 3xxxx 业务规则
    DRIVER_MUTEX_CONFLICT(30001, "司机已有未暂停的进行中工单"),
    DRIVER_NOT_AVAILABLE(30002, "司机处于离岗或离职状态，不可派单"),
    PENDING_DISPATCH_LIMIT(30003, "同一司机未接单派单数已达上限"),
    PASSWORD_LOGIN_DISABLED(30015, "管理员已关闭密码登录，请使用验证码登录"),
    PAST_START_TIME(30004, "预计开始时间早于当前时间，需二次确认"),
    OUT_OF_CHECKIN_RANGE(30005, "超出签到有效范围"),
    LOCATION_UNAVAILABLE(30006, "定位获取失败或未授权"),
    ORDER_STATUS_NOT_ALLOWED(30007, "当前工单状态不允许执行该操作"),
    PENDING_EXCEPTION_EXISTS(30008, "存在未批复异常，不可签到或签退"),
    CHANGE_ALREADY_HANDLED(30009, "变更申请已处理或状态不允许审批"),
    POSTPONE_MUTEX_CONFLICT(30010, "延期申请命中互斥冲突"),
    DUPLICATE_SUBMIT(30011, "重复提交"),
    PHOTO_REQUIRED(30012, "必传照片缺失或附件数量不符"),
    CHECKOUT_NOT_COMPLIANT(30013, "签退位置不合规，加班需人工确认"),
    CONFLICT_ALREADY_HANDLED(30014, "冲突提醒记录已处置，不可重复处置"),

    // 4xxxx 资源状态
    NOT_FOUND(40001, "资源不存在"),
    VEHICLE_NOT_IDLE(40002, "车辆非空闲，不可派单"),
    VEHICLE_OCCUPIED(40003, "车辆已被占用"),
    RESOURCE_STATUS_NOT_ALLOWED(40004, "资源状态不允许该操作"),
    STAFF_NOT_FOUND(40005, "员工或账号不存在"),
    CUSTOMER_NOT_FOUND(40006, "客户不存在"),
    FILE_NOT_FOUND(40007, "文件不存在或已删除"),
    CONFLICT_NOT_FOUND(40008, "冲突提醒记录不存在"),
    DEMAND_NOT_FOUND(40009, "需求单不存在或已关闭"),
    ORDER_CANCELLED(40010, "已取消工单不可再被派单"),

    // 5xxxx 系统与第三方
    SYSTEM_ERROR(50000, "系统内部错误"),
    DATABASE_ERROR(50001, "数据库异常"),
    WECHAT_SEND_FAILED(50002, "微信订阅消息发送失败"),
    SMS_SEND_FAILED(50003, "短信发送失败或超出上限"),
    AMAP_ERROR(50004, "高德地图服务异常"),
    AI_UNAVAILABLE(50005, "AI 服务不可用（已降级）"),
    FILE_STORAGE_ERROR(50006, "文件存储读写失败"),
    IDEMPOTENCY_PROCESSING(50007, "幂等键处理中，请稍后重试"),
    RATE_LIMITED(50008, "请求过于频繁"),
    AI_NOT_CONFIGURED(50009, "AI 调用配置缺失"),
    AI_CALL_FAILED(50010, "AI 调用失败");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
