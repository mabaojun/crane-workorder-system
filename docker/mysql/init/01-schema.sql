-- =============================================================
-- 济南诚重吊装业务管理系统 — 全量建库脚本
-- 依据：docs/前置设计/02-数据库设计文档.md（31 张表，逐字段忠实落地）
-- 引擎 InnoDB / utf8mb4 / utf8mb4_0900_ai_ci，无物理外键，逻辑删除统一 deleted
-- 建表顺序按 02 §6.1：先 sys_ 与档案主数据，后工单及派生表，最后 ai_
-- =============================================================
SET NAMES utf8mb4;
CREATE DATABASE IF NOT EXISTS crane_workorder DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE crane_workorder;

-- ------------------------------------------------------------
-- 1. sys_role 角色表（02 §3.2.2）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_role (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    code        VARCHAR(32)  NOT NULL COMMENT '角色码：ADMIN 管理员（含原总监职责）/DRIVER 司机（员工）/CUSTOMER 客户（普通用户）',
    name        VARCHAR(64)  NOT NULL COMMENT '角色名称',
    data_scope  VARCHAR(16)  NOT NULL DEFAULT 'ALL' COMMENT '数据范围：ALL 全部/SELF_STAFF 仅本人（员工）/SELF_CUSTOMER 仅本人关联（客户）',
    is_system   TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '系统内置角色，不允许删除',
    sort        INT          NOT NULL DEFAULT 0 COMMENT '展示排序',
    description VARCHAR(255) NULL COMMENT '角色说明',
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by  BIGINT UNSIGNED NULL COMMENT '创建人',
    updated_by  BIGINT UNSIGNED NULL COMMENT '更新人',
    deleted     TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sr_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色表';

-- ------------------------------------------------------------
-- 2. sys_permission 权限表（02 §3.2.3）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_permission (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    code       VARCHAR(64) NOT NULL COMMENT '权限码，如 work_order:create',
    name       VARCHAR(64) NOT NULL COMMENT '权限名称',
    type       VARCHAR(16) NOT NULL DEFAULT 'API' COMMENT '权限类型：MENU 菜单/BUTTON 按钮/API 接口',
    module     VARCHAR(64) NOT NULL COMMENT '所属业务域，对齐 01 §3 模块名',
    parent_id  BIGINT UNSIGNED NULL COMMENT '上级权限，构成菜单树',
    path       VARCHAR(255) NULL COMMENT '前端路由或接口路径',
    sort       INT NOT NULL DEFAULT 0 COMMENT '展示排序',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by BIGINT UNSIGNED NULL COMMENT '创建人',
    updated_by BIGINT UNSIGNED NULL COMMENT '更新人',
    deleted    TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sp_code (code),
    KEY idx_sp_module (module),
    KEY idx_sp_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='权限表';

-- ------------------------------------------------------------
-- 3. sys_role_permission 角色权限关联表（02 §3.2.4）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_role_permission (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    role_id       BIGINT UNSIGNED NOT NULL COMMENT '角色 id',
    permission_id BIGINT UNSIGNED NOT NULL COMMENT '权限 id',
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by    BIGINT UNSIGNED NULL COMMENT '创建人',
    updated_by    BIGINT UNSIGNED NULL COMMENT '更新人',
    deleted       TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_srp_role_perm (role_id, permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色权限关联表';

-- ------------------------------------------------------------
-- 4. sys_user 用户表（02 §3.2.1）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_user (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    phone           VARCHAR(20)  NOT NULL COMMENT '预留手机号，身份凭据与短信通知目标',
    password        VARCHAR(100) NULL COMMENT '密码 BCrypt 哈希（后台/大屏账号密码登录用，微信端账号可为空）',
    open_id         VARCHAR(64)  NULL COMMENT '微信小程序 openid，首次授权后写入',
    union_id        VARCHAR(64)  NULL COMMENT '微信 unionid，用于多端识别',
    nick_name       VARCHAR(64)  NULL COMMENT '微信昵称或系统展示名',
    avatar_url      VARCHAR(500) NULL COMMENT '头像资源路径',
    role_id         BIGINT UNSIGNED NOT NULL COMMENT '所属角色，一个账号一个角色',
    staff_id        BIGINT UNSIGNED NULL COMMENT '员工账号绑定的员工档案',
    customer_id     BIGINT UNSIGNED NULL COMMENT '客户账号绑定的客户档案',
    status          VARCHAR(16)  NOT NULL DEFAULT 'PENDING_BIND' COMMENT '账号状态：PENDING_BIND 待绑定/ACTIVE 已激活/FROZEN 冻结/DISABLED 停用',
    bound_at        DATETIME     NULL COMMENT '手机号绑定成功（激活）时间',
    last_login_at   DATETIME     NULL COMMENT '最近登录时间',
    force_logout_at DATETIME     NULL COMMENT '强制下线时间戳，早于该时间的令牌失效',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by      BIGINT UNSIGNED NULL COMMENT '创建人',
    updated_by      BIGINT UNSIGNED NULL COMMENT '更新人',
    deleted         TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_su_phone (phone),
    UNIQUE KEY uk_su_open_id (open_id),
    KEY idx_su_role (role_id),
    KEY idx_su_staff (staff_id),
    KEY idx_su_customer (customer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户表';

-- ------------------------------------------------------------
-- 4. sys_dict 字典表（02 §3.2.5）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_dict (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    dict_type   VARCHAR(64)  NOT NULL COMMENT '字典类型，如 work_order_status',
    dict_code   VARCHAR(64)  NOT NULL COMMENT '字典码，即枚举字面量',
    dict_label  VARCHAR(128) NOT NULL COMMENT '中文标签',
    dict_value  VARCHAR(64)  NULL COMMENT '附加值（如颜色语义、排序权重）',
    color_token VARCHAR(16)  NULL COMMENT '状态色语义令牌',
    sort        INT          NOT NULL DEFAULT 0 COMMENT '排序',
    is_enabled  TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否启用',
    remark      VARCHAR(255) NULL COMMENT '说明',
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by  BIGINT UNSIGNED NULL COMMENT '创建人',
    updated_by  BIGINT UNSIGNED NULL COMMENT '更新人',
    deleted     TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sd_type_code (dict_type, dict_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='字典表';

-- ------------------------------------------------------------
-- 5. sys_config 系统配置表（02 §3.2.6）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_config (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    config_key   VARCHAR(128) NOT NULL COMMENT '配置键，如 conflict.ahead_window_minutes',
    config_value VARCHAR(512) NULL COMMENT '配置值，统一字符串存储由服务层转换',
    value_type   VARCHAR(16)  NOT NULL DEFAULT 'STRING' COMMENT '值类型：INT/DECIMAL/STRING/BOOLEAN/JSON',
    group_name   VARCHAR(64)  NOT NULL COMMENT '分组：system/conflict/attendance/overtime/demand/alarm/sms/ai/storage',
    is_encrypted TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否密文存储',
    description  VARCHAR(255) NULL COMMENT '参数含义说明',
    created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by   BIGINT UNSIGNED NULL COMMENT '创建人',
    updated_by   BIGINT UNSIGNED NULL COMMENT '更新人',
    deleted      TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sc_key (config_key),
    KEY idx_sc_group (group_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统配置表';

-- ------------------------------------------------------------
-- 6. sys_sms_code 短信验证码表（02 §3.2.7）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_sms_code (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    phone       VARCHAR(20) NOT NULL COMMENT '接收手机号',
    scene       VARCHAR(32) NOT NULL COMMENT '场景：LOGIN 登录/BIND_PHONE 绑定手机号',
    code        VARCHAR(8)  NOT NULL COMMENT '验证码',
    expires_at  DATETIME    NOT NULL COMMENT '过期时间',
    used_at     DATETIME    NULL COMMENT '校验成功使用时间',
    send_count  INT         NOT NULL DEFAULT 1 COMMENT '当日同场景发送次数，频次控制',
    send_result VARCHAR(64) NULL COMMENT '短信发送结果或降级标记',
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by  BIGINT UNSIGNED NULL COMMENT '创建人',
    updated_by  BIGINT UNSIGNED NULL COMMENT '更新人',
    deleted     TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    KEY idx_ssc_phone_used (phone, used_at),
    KEY idx_ssc_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='短信验证码表';

-- ------------------------------------------------------------
-- 7. sys_idempotency 幂等键表（02 §3.2.8）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_idempotency (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    idempotency_key VARCHAR(64) NOT NULL COMMENT '幂等键，客户端生成',
    biz_type        VARCHAR(32) NOT NULL COMMENT '业务类型，如 dispatch/accept/checkout',
    result_code     VARCHAR(16) NULL COMMENT '首次处理结果码',
    result_payload  JSON        NULL COMMENT '首次响应摘要（脱敏）',
    expire_at       DATETIME    NOT NULL COMMENT '幂等窗口截止时间',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by      BIGINT UNSIGNED NULL COMMENT '创建人',
    updated_by      BIGINT UNSIGNED NULL COMMENT '更新人',
    deleted         TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_si_key (idempotency_key),
    KEY idx_si_expire (expire_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='幂等键表';

-- ------------------------------------------------------------
-- 8. biz_staff 员工档案表（02 §3.3.1）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS biz_staff (
    id                     BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    staff_no               VARCHAR(32)  NOT NULL COMMENT '员工编号，系统自动生成',
    name                   VARCHAR(64)  NOT NULL COMMENT '姓名',
    phone                  VARCHAR(20)  NOT NULL COMMENT '手机号，唯一校验，短信通知目标',
    id_card                VARCHAR(64)  NOT NULL COMMENT '身份证号，密文存储',
    position               VARCHAR(16)  NOT NULL COMMENT '岗位：DRIVER 司机/EMPLOYEE 员工',
    status                 VARCHAR(16)  NOT NULL DEFAULT 'IDLE' COMMENT '人员状态：IDLE 空闲/BUSY 作业中/AWAY 离岗/RESIGNED 离职',
    license_type           VARCHAR(16)  NULL COMMENT '驾驶证类型，司机必填',
    special_cert_no        VARCHAR(64)  NULL COMMENT '特种作业证（吊车操作证）编号，司机必填',
    special_cert_photo_url VARCHAR(500) NULL COMMENT '证件照片，私有资源路径',
    emergency_name         VARCHAR(64)  NULL COMMENT '紧急联系人姓名',
    emergency_phone        VARCHAR(20)  NULL COMMENT '紧急联系人电话',
    hire_date              DATE         NULL COMMENT '入职日期',
    user_id                BIGINT UNSIGNED NULL COMMENT '绑定登录账号',
    remark                 VARCHAR(255) NULL COMMENT '备注',
    created_at             DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at             DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by             BIGINT UNSIGNED NULL COMMENT '创建人',
    updated_by             BIGINT UNSIGNED NULL COMMENT '更新人',
    deleted                TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_st_staff_no (staff_no),
    UNIQUE KEY uk_st_phone (phone),
    UNIQUE KEY uk_st_id_card (id_card),
    KEY idx_st_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='员工档案表';

-- ------------------------------------------------------------
-- 9. biz_customer 客户资料表（02 §3.3.2）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS biz_customer (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    customer_no VARCHAR(32)  NOT NULL COMMENT '客户编号，系统自动生成',
    name        VARCHAR(128) NOT NULL COMMENT '客户名称，公司名称或个人姓名',
    type        VARCHAR(16)  NOT NULL COMMENT '客户类型：COMPANY 公司/PERSON 个人',
    credit_code VARCHAR(64)  NULL COMMENT '统一社会信用代码，公司客户必填',
    industry    VARCHAR(32)  NULL COMMENT '所属行业',
    address     VARCHAR(255) NULL COMMENT '联系地址',
    phone       VARCHAR(20)  NOT NULL COMMENT '客户总机/主号码',
    audit_status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT '审核状态：PENDING 待审核/APPROVED 已审核/REJECTED 已驳回',
    status      VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT '客户状态：ACTIVE 合作中/DISABLED 停用',
    user_id     BIGINT UNSIGNED NULL COMMENT '绑定的客户登录账号',
    remark      VARCHAR(255) NULL COMMENT '备注',
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by  BIGINT UNSIGNED NULL COMMENT '创建人',
    updated_by  BIGINT UNSIGNED NULL COMMENT '更新人',
    deleted     TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_cu_customer_no (customer_no),
    KEY idx_cu_name (name),
    KEY idx_cu_phone (phone),
    KEY idx_cu_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='客户资料表';

-- ------------------------------------------------------------
-- 10. biz_customer_contact 客户联系人表（02 §3.3.3）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS biz_customer_contact (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    customer_id BIGINT UNSIGNED NOT NULL COMMENT '所属客户',
    name        VARCHAR(64)  NOT NULL COMMENT '联系人姓名',
    gender      VARCHAR(8)   NULL COMMENT '性别',
    position    VARCHAR(64)  NULL COMMENT '职务',
    phone       VARCHAR(20)  NOT NULL COMMENT '手机号，全局唯一',
    wechat      VARCHAR(64)  NULL COMMENT '微信号',
    email       VARCHAR(128) NULL COMMENT '邮箱',
    is_default  TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否默认联系人，每客户唯一由服务层保证',
    remark      VARCHAR(255) NULL COMMENT '备注',
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by  BIGINT UNSIGNED NULL COMMENT '创建人',
    updated_by  BIGINT UNSIGNED NULL COMMENT '更新人',
    deleted     TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_cc_phone (phone),
    KEY idx_cc_customer (customer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='客户联系人表';

-- ------------------------------------------------------------
-- 11. biz_vehicle 车辆档案表（02 §3.3.4）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS biz_vehicle (
    id                     BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    plate_no               VARCHAR(16)   NOT NULL COMMENT '车牌号',
    model                  VARCHAR(64)   NULL COMMENT '车辆型号',
    type                   VARCHAR(16)   NOT NULL DEFAULT 'CRANE' COMMENT '车辆类型：CRANE 吊车/LIFT 举升车',
    tonnage                DECIMAL(8,2)  NULL COMMENT '吊车吨位（吨），仅 CRANE 填写',
    lift_height            DECIMAL(6,1)  NULL COMMENT '举升高度（米），仅 LIFT 填写，常用 20/30',
    default_staff_id       BIGINT UNSIGNED NULL COMMENT '默认司机（固定归属），一人多车表现为多行共用同一值',
    status                 VARCHAR(16)   NOT NULL DEFAULT 'IDLE' COMMENT '车辆状态：IDLE 空闲/BUSY 忙碌（系统派单）/BUSY_THIRD 忙碌（第三方派单）/MAINTENANCE 维保/DISABLED 停用',
    current_order_id       BIGINT UNSIGNED NULL COMMENT '当前绑定的进行中工单',
    third_party_marked_at  DATETIME      NULL COMMENT '第三方派单标记时刻，外部占用时长起点',
    is_away                TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '是否在外未回场，由每日签退确认派生',
    remark                 VARCHAR(255)  NULL COMMENT '备注',
    created_at             DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at             DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by             BIGINT UNSIGNED NULL COMMENT '创建人',
    updated_by             BIGINT UNSIGNED NULL COMMENT '更新人',
    deleted                TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ve_plate_no (plate_no),
    KEY idx_ve_default_staff (default_staff_id),
    KEY idx_ve_status (status),
    KEY idx_ve_current_order (current_order_id),
    KEY idx_ve_third_at (third_party_marked_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='车辆档案表';

-- ------------------------------------------------------------
-- 12. biz_demand_order 需求单表（02 §3.3.5）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS biz_demand_order (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    demand_no           VARCHAR(32)  NOT NULL COMMENT '需求单编号',
    source              VARCHAR(16)  NOT NULL COMMENT '来源：CUSTOMER_MP 客户小程序提报/PHONE 电话下单/ADMIN 管理员录入',
    customer_id         BIGINT UNSIGNED NULL COMMENT '关联客户资料，管理员代录时可能暂缺',
    contact_id          BIGINT UNSIGNED NULL COMMENT '关联客户联系人',
    contact_name        VARCHAR(64)  NULL COMMENT '提报人姓名快照',
    contact_phone       VARCHAR(20)  NOT NULL COMMENT '联系电话',
    address             VARCHAR(255) NOT NULL COMMENT '客户地址/施工地址',
    longitude           DECIMAL(10,7) NULL COMMENT '地图选点经度',
    latitude            DECIMAL(10,7) NULL COMMENT '地图选点纬度',
    description         TEXT         NULL COMMENT '需求描述',
    image_urls          JSON         NULL COMMENT '需求图片，私有资源路径数组',
    intended_vehicle_id BIGINT UNSIGNED NULL COMMENT '意向车辆',
    crane_tonnage       DECIMAL(8,2) NULL COMMENT '吊车吨位需求（需管理员复核）',
    cargo_weight        DECIMAL(10,2) NULL COMMENT '被吊装货物重量（吨，需管理员复核）',
    expected_start_at   DATETIME     NULL COMMENT '期望施工时间',
    status              VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT '需求单状态：PENDING 待处理/REVIEWING 待复核/CONVERTED 已转工单/REJECTED 已驳回/CLOSED 已关闭',
    review_remark       VARCHAR(500) NULL COMMENT '复核意见或驳回原因，对客户可见',
    reject_count        INT          NOT NULL DEFAULT 0 COMMENT '驳回次数',
    parent_demand_id    BIGINT UNSIGNED NULL COMMENT '重提前指向被驳回的原需求单',
    converted_order_id  BIGINT UNSIGNED NULL COMMENT '转化后的工单，双向回溯',
    expires_at          DATETIME     NULL COMMENT '暂存到期时间（创建+10 天可配置），超期自动转 CLOSED',
    closed_at           DATETIME     NULL COMMENT '关闭时间',
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by          BIGINT UNSIGNED NULL COMMENT '创建人',
    updated_by          BIGINT UNSIGNED NULL COMMENT '更新人',
    deleted             TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_do_demand_no (demand_no),
    KEY idx_do_customer (customer_id),
    KEY idx_do_status (status),
    KEY idx_do_expires_at (expires_at, status),
    KEY idx_do_converted_order (converted_order_id),
    KEY idx_do_parent (parent_demand_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='需求单表';

-- ------------------------------------------------------------
-- 13. biz_work_order 工单表（02 §3.3.6，聚合根）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS biz_work_order (
    id                       BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_no                 VARCHAR(32)   NOT NULL COMMENT '工单号，对客户与司机展示的唯一编号',
    name                     VARCHAR(128)  NOT NULL COMMENT '任务名称',
    source                   VARCHAR(16)   NOT NULL COMMENT '工单来源：DEMAND 需求单转入/DIRECT 管理员直建',
    demand_id                BIGINT UNSIGNED NULL COMMENT '来源需求单 id，用于回溯',
    customer_id              BIGINT UNSIGNED NOT NULL COMMENT '关联客户 id',
    contact_id               BIGINT UNSIGNED NULL COMMENT '关联客户联系人 id',
    staff_id                 BIGINT UNSIGNED NOT NULL COMMENT '执行司机 id',
    vehicle_id               BIGINT UNSIGNED NOT NULL COMMENT '施工车辆 id',
    address                  VARCHAR(255)  NOT NULL COMMENT '施工地点详细地址',
    longitude                DECIMAL(10,7) NOT NULL COMMENT '高德点位经度',
    latitude                 DECIMAL(10,7) NOT NULL COMMENT '高德点位纬度',
    cargo_name               VARCHAR(128)  NULL COMMENT '货物名称',
    cargo_quantity           DECIMAL(10,2) NULL COMMENT '货物数量',
    cargo_weight             DECIMAL(10,2) NULL COMMENT '实际吊装货物重量（吨）',
    crane_tonnage            DECIMAL(8,2)  NULL COMMENT '吊车吨位',
    expected_start_at        DATETIME      NOT NULL COMMENT '预计开始时间，超时提醒与冲突巡检基准',
    accepted_at              DATETIME      NULL COMMENT '司机接单时间',
    started_at               DATETIME      NULL COMMENT '任务启动时间（签到成功写入），工时起点',
    suspended_at             DATETIME      NULL COMMENT '最近一次进入施工受阻的时间',
    completed_at             DATETIME      NULL COMMENT '自动结单时间，工时终点',
    cancelled_at             DATETIME      NULL COMMENT '取消时间',
    cancelled_reason         VARCHAR(500)  NULL COMMENT '取消原因，对客户可见',
    status                   VARCHAR(16)   NOT NULL DEFAULT 'PENDING_ACCEPT' COMMENT '工单状态：PENDING_ACCEPT/PENDING_ARRIVE/IN_PROGRESS/SUSPENDED/COMPLETED/CANCELLED',
    customer_visible_status  VARCHAR(32)   NULL COMMENT '客户可见状态：DISPATCHED/ACCEPTED/STARTED/PAUSED/FINISHED/CANCELLED',
    accept_deadline_at       DATETIME      NULL COMMENT '接单时限（下发+30 分钟可配置）',
    timeout_reminded_at      DATETIME      NULL COMMENT '超时未签到提醒已发出时间，防重复提醒',
    is_pinned_alarm          TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '是否在报警中心置顶高亮',
    remark                   VARCHAR(500)  NULL COMMENT '任务备注',
    requirement              VARCHAR(500)  NULL COMMENT '施工要求',
    created_at               DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at               DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by               BIGINT UNSIGNED NULL COMMENT '创建人',
    updated_by               BIGINT UNSIGNED NULL COMMENT '更新人',
    deleted                  TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_wo_order_no (order_no),
    KEY idx_wo_staff_status (staff_id, status),
    KEY idx_wo_vehicle_status (vehicle_id, status),
    KEY idx_wo_status (status, expected_start_at),
    KEY idx_wo_customer (customer_id),
    KEY idx_wo_expected_start (expected_start_at),
    KEY idx_wo_demand (demand_id),
    KEY idx_wo_accept_deadline (accept_deadline_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='工单表';

-- ------------------------------------------------------------
-- 13.1 工单状态变更记录表（01 §6.4 状态变更与记录同事务；MVP 计划 T-13）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS biz_work_order_status_log (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_id    BIGINT UNSIGNED NOT NULL COMMENT '关联工单',
    from_status VARCHAR(16)  NULL COMMENT '变更前状态',
    to_status   VARCHAR(16)  NOT NULL COMMENT '变更后状态',
    operator_id BIGINT UNSIGNED NULL COMMENT '操作账号',
    remark      VARCHAR(255) NULL COMMENT '变更说明',
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by  BIGINT UNSIGNED NULL COMMENT '创建人',
    updated_by  BIGINT UNSIGNED NULL COMMENT '更新人',
    deleted     TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    KEY idx_wosl_order (order_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='工单状态变更记录表';

-- ------------------------------------------------------------
-- 14. biz_order_accept_log 接单拒单记录表（02 §3.3.7）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS biz_order_accept_log (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_id      BIGINT UNSIGNED NOT NULL COMMENT '关联工单',
    staff_id      BIGINT UNSIGNED NOT NULL COMMENT '操作司机',
    action        VARCHAR(16)  NOT NULL COMMENT '动作：ACCEPT 接单/REJECT 拒单',
    reason_type   VARCHAR(32)  NULL COMMENT '拒单原因类型：车辆故障/时间冲突/能力不符/其他',
    reason        VARCHAR(500) NULL COMMENT '拒单原因明细，接单时为空',
    acted_at      DATETIME     NOT NULL COMMENT '操作时间',
    deliver_round INT          NOT NULL DEFAULT 1 COMMENT '派单轮次，同一工单多次派单时区分留痕',
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by    BIGINT UNSIGNED NULL COMMENT '创建人',
    updated_by    BIGINT UNSIGNED NULL COMMENT '更新人',
    deleted       TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    KEY idx_oal_order (order_id),
    KEY idx_oal_staff_at (staff_id, acted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='接单拒单记录表';

-- ------------------------------------------------------------
-- 15. biz_attendance 打卡记录表（02 §3.3.8）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS biz_attendance (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_id        BIGINT UNSIGNED NOT NULL COMMENT '关联工单',
    staff_id        BIGINT UNSIGNED NOT NULL COMMENT '打卡司机',
    type            VARCHAR(16)   NOT NULL COMMENT '打卡类型：CHECK_IN 签到/DAILY_CHECKOUT 当日签退/TASK_END 任务结束',
    checked_at      DATETIME      NOT NULL COMMENT '打卡时间，服务端记录',
    captured_at     DATETIME      NULL COMMENT '客户端实际采集时间，弱网补传保留原值',
    longitude       DECIMAL(10,7) NULL COMMENT '打卡经度',
    latitude        DECIMAL(10,7) NULL COMMENT '打卡纬度',
    distance_meters INT           NULL COMMENT '打卡点与工单预设点位直线距离（米）',
    is_in_range     TINYINT(1)    NULL COMMENT '是否在有效范围内，签到强制、签退弱校验',
    is_compliant    TINYINT(1)    NULL DEFAULT 1 COMMENT '位置是否合规，签退范围外由后台标注为 0',
    photo_urls      JSON          NULL COMMENT '现场照片私有资源路径数组，不限张数',
    is_valid        TINYINT(1)    NULL COMMENT '是否有效打卡，签到须同时满足定位与照片',
    upload_mode     VARCHAR(16)   NULL DEFAULT 'ONLINE' COMMENT '上传方式：ONLINE 实时/OFFLINE_RETRY 离线补传',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by      BIGINT UNSIGNED NULL COMMENT '创建人',
    updated_by      BIGINT UNSIGNED NULL COMMENT '更新人',
    deleted         TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    KEY idx_at_order_type (order_id, type),
    KEY idx_at_staff_at (staff_id, checked_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='打卡记录表';

-- ------------------------------------------------------------
-- 16. biz_daily_checkout 每日签退记录表（02 §3.3.9）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS biz_daily_checkout (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_id        BIGINT UNSIGNED NOT NULL COMMENT '关联工单',
    staff_id        BIGINT UNSIGNED NOT NULL COMMENT '司机',
    attendance_id   BIGINT UNSIGNED NULL COMMENT '关联当日签退打卡记录',
    work_date       DATE          NOT NULL COMMENT '施工日期',
    day_start_at    DATETIME      NULL COMMENT '当日施工开始时间',
    day_end_at      DATETIME      NOT NULL COMMENT '当日施工结束时间（今日任务完成时刻）',
    longitude       DECIMAL(10,7) NULL COMMENT '签退经度',
    latitude        DECIMAL(10,7) NULL COMMENT '签退纬度',
    distance_meters INT           NULL COMMENT '签退点与任务执行地直线距离（米）',
    is_in_range     TINYINT(1)    NULL COMMENT '位置校验结果，签退弱校验允许范围外提交',
    photo_urls      JSON          NULL COMMENT '当日离场照片',
    is_left_site    TINYINT(1)    NULL COMMENT '车辆是否离场',
    is_returned     TINYINT(1)    NULL COMMENT '车辆是否回场；选回场则车辆状态置空闲',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by      BIGINT UNSIGNED NULL COMMENT '创建人',
    updated_by      BIGINT UNSIGNED NULL COMMENT '更新人',
    deleted         TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_dc_order_date (order_id, work_date),
    KEY idx_dc_staff_date (staff_id, work_date),
    KEY idx_dc_attendance (attendance_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='每日签退记录表';

-- ------------------------------------------------------------
-- 17. biz_overtime_record 加班记录表（02 §3.3.10）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS biz_overtime_record (
    id                       BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_id                 BIGINT UNSIGNED NOT NULL COMMENT '关联工单',
    staff_id                 BIGINT UNSIGNED NOT NULL COMMENT '司机',
    daily_checkout_id        BIGINT UNSIGNED NULL COMMENT '关联每日签退记录',
    work_date                DATE     NOT NULL COMMENT '加班所属日期',
    normal_off_at            DATETIME NOT NULL COMMENT '正常下班时间节点（默认 18:00 后台可配）',
    actual_checkout_at       DATETIME NOT NULL COMMENT '实际签退时间',
    checkout_longitude       DECIMAL(10,7) NULL COMMENT '签退坐标经度',
    checkout_latitude        DECIMAL(10,7) NULL COMMENT '签退坐标纬度',
    checkout_distance_meters INT      NULL COMMENT '签退点与任务执行地距离（米）',
    is_location_compliant    TINYINT(1) NOT NULL COMMENT '签退位置是否在任务执行地 1 公里内，不合规则不计加班',
    overtime_minutes         INT      NOT NULL DEFAULT 0 COMMENT '加班时长（分钟）',
    audit_status             VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT '审核状态：PENDING 待审核/APPROVED 已确认/REJECTED 已驳回',
    audited_by               BIGINT UNSIGNED NULL COMMENT '审核人',
    audited_at               DATETIME NULL COMMENT '审核时间',
    is_effective             TINYINT(1) NOT NULL DEFAULT 0 COMMENT '审核通过后置 1，方可计入绩效',
    created_at               DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at               DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by               BIGINT UNSIGNED NULL COMMENT '创建人',
    updated_by               BIGINT UNSIGNED NULL COMMENT '更新人',
    deleted                  TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    KEY idx_ot_order (order_id),
    KEY idx_ot_staff_date (staff_id, work_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='加班记录表';

-- ------------------------------------------------------------
-- 18. biz_work_hour_stat 工时统计表（02 §3.3.11）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS biz_work_hour_stat (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_id         BIGINT UNSIGNED NOT NULL COMMENT '关联工单，一单一条',
    staff_id         BIGINT UNSIGNED NOT NULL COMMENT '司机',
    vehicle_id       BIGINT UNSIGNED NULL COMMENT '车辆，用于车辆使用率口径',
    started_at       DATETIME NULL COMMENT '任务启动时间',
    ended_at         DATETIME NULL COMMENT '任务结束时间',
    total_minutes    INT      NOT NULL DEFAULT 0 COMMENT '总工作时长（分钟）',
    overtime_minutes INT      NOT NULL DEFAULT 0 COMMENT '加班总时长（分钟），仅含已生效加班',
    check_in_count   INT      NOT NULL DEFAULT 0 COMMENT '签到次数，含跨天任务的每日签到',
    checkout_count   INT      NOT NULL DEFAULT 0 COMMENT '签退次数',
    calc_version     INT      NOT NULL DEFAULT 1 COMMENT '计算版本，重算覆盖时递增',
    computed_at      DATETIME NOT NULL COMMENT '统计生成时间',
    created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by       BIGINT UNSIGNED NULL COMMENT '创建人',
    updated_by       BIGINT UNSIGNED NULL COMMENT '更新人',
    deleted          TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_whs_order (order_id),
    KEY idx_whs_staff_period (staff_id, computed_at),
    KEY idx_whs_vehicle_period (vehicle_id, computed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='工时统计表';

-- ------------------------------------------------------------
-- 19. biz_exception_record 异常记录表（02 §3.3.12）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS biz_exception_record (
    id                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_id              BIGINT UNSIGNED NOT NULL COMMENT '关联工单',
    staff_id              BIGINT UNSIGNED NOT NULL COMMENT '上报司机',
    type                  VARCHAR(32)   NOT NULL COMMENT '异常类型：RAIN 下雨/PARTY_STOP 甲方停工/VEHICLE_FAULT 车辆故障/CUSTOM 自定义',
    type_label            VARCHAR(64)   NULL COMMENT '自定义原因的标签文本，固定选项时为空',
    description           VARCHAR(1000) NOT NULL COMMENT '异常描述/自定义原因明细',
    image_urls            JSON          NULL COMMENT '配图，私有资源路径数组',
    status                VARCHAR(16)   NOT NULL DEFAULT 'PENDING' COMMENT '处理状态：PENDING 待批复/APPROVED 已批复/CLOSED 已关闭',
    instruction           VARCHAR(32)   NULL COMMENT '批复指令：CONTINUE 继续施工/SUSPEND 暂停/TERMINATE 终止',
    order_status_before   VARCHAR(16)   NULL COMMENT '上报时的工单状态快照',
    order_status_after    VARCHAR(16)   NULL COMMENT '批复后的工单状态快照',
    is_public_to_customer TINYINT(1)    NOT NULL DEFAULT 1 COMMENT '是否对客户公开，含司机自定义原因',
    reported_at           DATETIME      NOT NULL COMMENT '上报时间',
    replied_at            DATETIME      NULL COMMENT '批复时间',
    created_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by            BIGINT UNSIGNED NULL COMMENT '创建人',
    updated_by            BIGINT UNSIGNED NULL COMMENT '更新人',
    deleted               TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    KEY idx_ex_order_status (order_id, status, reported_at),
    KEY idx_ex_staff (staff_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='异常记录表';

-- ------------------------------------------------------------
-- 20. biz_order_change_request 工单变更申请表（02 §3.3.13）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS biz_order_change_request (
    id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    request_no         VARCHAR(32)  NOT NULL COMMENT '变更申请编号',
    order_id           BIGINT UNSIGNED NOT NULL COMMENT '关联工单',
    applicant_staff_id BIGINT UNSIGNED NOT NULL COMMENT '申请司机',
    type               VARCHAR(16)  NOT NULL COMMENT '变更类型：CANCEL 取消工单/VEHICLE_CHANGE 车辆更换/POSTPONE 延期/ADDRESS_CHANGE 改址',
    reason_type        VARCHAR(32)  NULL COMMENT '原因分类，如天气、客户要求、现场条件',
    reason             VARCHAR(500) NOT NULL COMMENT '变更原因，支持自定义输入',
    evidence_urls      JSON         NULL COMMENT '佐证照片，私有资源路径数组',
    before_snapshot    JSON         NULL COMMENT '变更前要素快照（车辆、司机、时间、地点）',
    after_snapshot     JSON         NULL COMMENT '申请中的变更后要素',
    status             VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT '申请状态：PENDING 待审批/APPROVED 已通过/REJECTED 已驳回',
    conflict_alert_id  BIGINT UNSIGNED NULL COMMENT '延期类申请触发的冲突提醒记录',
    applied_at         DATETIME     NOT NULL COMMENT '申请提交时间',
    handled_by         BIGINT UNSIGNED NULL COMMENT '审批人',
    handled_at         DATETIME     NULL COMMENT '审批时间',
    reject_reason      VARCHAR(500) NULL COMMENT '驳回原因，反馈申请人',
    created_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by         BIGINT UNSIGNED NULL COMMENT '创建人',
    updated_by         BIGINT UNSIGNED NULL COMMENT '更新人',
    deleted            TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ocr_request_no (request_no),
    KEY idx_ocr_order_status (order_id, status, applied_at),
    KEY idx_ocr_applicant (applicant_staff_id),
    KEY idx_ocr_conflict (conflict_alert_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='工单变更申请表';

-- ------------------------------------------------------------
-- 21. biz_conflict_alert 冲突提醒记录表（02 §3.3.14）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS biz_conflict_alert (
    id                     BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    alert_no               VARCHAR(32)  NOT NULL COMMENT '提醒编号，系统生成',
    trigger_scene          VARCHAR(16)  NOT NULL COMMENT '触发场景：AHEAD_WINDOW/DRIVER_POSTPONE/ADMIN_EDIT/ADMIN_DISPATCH',
    staff_id               BIGINT UNSIGNED NOT NULL COMMENT '涉及司机',
    staff_no_snapshot      VARCHAR(32)  NULL COMMENT '司机工号快照，供列表直接展示',
    prev_order_id          BIGINT UNSIGNED NULL COMMENT '冲突前一单（进行中工单）',
    next_order_id          BIGINT UNSIGNED NULL COMMENT '冲突后一单（进入提前提醒时段的工单）',
    prev_order_no          VARCHAR(32)  NULL COMMENT '前一单工单号快照',
    next_order_no          VARCHAR(32)  NULL COMMENT '后一单工单号快照',
    conflict_summary       VARCHAR(500) NOT NULL COMMENT '冲突说明，含两单任务名与约定服务时间',
    notify_targets         JSON         NOT NULL COMMENT '提醒对象数组：DRIVER/ADMIN',
    notify_channels        JSON         NULL COMMENT '各对象通道与送达：对象->通道->送达状态',
    generated_at           DATETIME     NOT NULL COMMENT '提醒生成时间',
    response_deadline_at   DATETIME     NOT NULL COMMENT '处置时限截止时间（生成+15 分钟可配置）',
    status                 VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT '处置状态：PENDING 待处理/HANDLED 已处置/ESCALATED 已升级/CLOSED 已关闭',
    handle_action          VARCHAR(32)  NULL COMMENT '处置动作：REASSIGN/APPROVE_POSTPONE/SUSPEND/NOTIFY/CONTINUE',
    handled_by             BIGINT UNSIGNED NULL COMMENT '处置人',
    handled_at             DATETIME     NULL COMMENT '处置时间',
    handle_remark          VARCHAR(500) NULL COMMENT '处置说明',
    escalated_at           DATETIME     NULL COMMENT '升级时间',
    escalate_targets       JSON         NULL COMMENT '升级提醒对象，超时未处置时写入',
    is_pinned              TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否在数据驾驶舱报警中心置顶',
    closed_reason          VARCHAR(255) NULL COMMENT '关闭原因',
    order_change_request_id BIGINT UNSIGNED NULL COMMENT '由延期类变更申请触发时指向该申请',
    created_at             DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at             DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by             BIGINT UNSIGNED NULL COMMENT '创建人',
    updated_by             BIGINT UNSIGNED NULL COMMENT '更新人',
    deleted                TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ca_alert_no (alert_no),
    KEY idx_ca_staff_status_time (staff_id, status, generated_at),
    KEY idx_ca_scene_status (trigger_scene, status),
    KEY idx_ca_prev_order (prev_order_id),
    KEY idx_ca_next_order (next_order_id),
    KEY idx_ca_deadline (response_deadline_at),
    KEY idx_ca_generated_at (generated_at),
    KEY idx_ca_change_request (order_change_request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='冲突提醒记录表';

-- ------------------------------------------------------------
-- 22. biz_approval_record 审批记录表（02 §3.3.15）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS biz_approval_record (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    biz_type    VARCHAR(32)   NOT NULL COMMENT '业务类型：EXCEPTION 异常批复/ORDER_CHANGE 变更申请审批',
    biz_id      BIGINT UNSIGNED NOT NULL COMMENT '关联业务记录 id',
    order_id    BIGINT UNSIGNED NOT NULL COMMENT '关联工单，供工单详情页聚合审批流',
    approver_id BIGINT UNSIGNED NOT NULL COMMENT '审批人账号',
    action      VARCHAR(16)   NOT NULL COMMENT '审批动作：APPROVE 通过/REJECT 驳回',
    instruction VARCHAR(32)   NULL COMMENT '下发指令，如继续施工、暂停、终止',
    opinion     VARCHAR(1000) NULL COMMENT '审批意见',
    approved_at DATETIME      NOT NULL COMMENT '审批时间',
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by  BIGINT UNSIGNED NULL COMMENT '创建人',
    updated_by  BIGINT UNSIGNED NULL COMMENT '更新人',
    deleted     TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    KEY idx_ar_biz (biz_type, biz_id),
    KEY idx_ar_order (order_id, approved_at),
    KEY idx_ar_approver (approver_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='审批记录表';

-- ------------------------------------------------------------
-- 23. biz_performance_stat 绩效统计表（02 §3.3.16）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS biz_performance_stat (
    id                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    stat_object_type        VARCHAR(16)   NOT NULL COMMENT '统计对象类型：STAFF 员工/VEHICLE 车辆/CUSTOMER 客户/PROJECT 整体项目',
    object_id               BIGINT UNSIGNED NOT NULL COMMENT '统计对象 id，项目级为 0',
    period_type             VARCHAR(16)   NOT NULL COMMENT '周期类型：DAY/WEEK/MONTH/CUSTOM',
    period_start            DATE          NOT NULL COMMENT '周期起始日期',
    period_end              DATE          NOT NULL COMMENT '周期结束日期',
    order_count             INT           NOT NULL DEFAULT 0 COMMENT '工单数量',
    completed_count         INT           NOT NULL DEFAULT 0 COMMENT '已完成工单数',
    completion_rate         DECIMAL(5,2)  NULL COMMENT '完工率',
    exception_order_count   INT           NOT NULL DEFAULT 0 COMMENT '发生异常上报的工单数',
    exception_rate          DECIMAL(5,2)  NULL COMMENT '异常率',
    attendance_count        INT           NOT NULL DEFAULT 0 COMMENT '有效签到次数，含跨天每日签到',
    total_tonnage           DECIMAL(12,2) NULL COMMENT '吊装总吨位',
    work_minutes            INT           NOT NULL DEFAULT 0 COMMENT '本系统作业时长（分钟）',
    external_occupy_minutes INT           NOT NULL DEFAULT 0 COMMENT '第三方派单外部占用时长（分钟）',
    usage_rate              DECIMAL(5,2)  NULL COMMENT '车辆使用率',
    fault_count             INT           NOT NULL DEFAULT 0 COMMENT '车辆故障次数',
    demand_count            INT           NOT NULL DEFAULT 0 COMMENT '客户需求单数量',
    deal_order_count        INT           NOT NULL DEFAULT 0 COMMENT '成交工单数量',
    activity_flag           TINYINT(1)    NULL COMMENT '客户合作活跃度标记',
    avg_review_score        DECIMAL(3,2)  NULL COMMENT '客户评分均值，仅记录留存',
    generated_at            DATETIME      NOT NULL COMMENT '统计生成时间',
    calc_version            INT           NOT NULL DEFAULT 1 COMMENT '计算版本，口径调整后重算递增',
    created_at              DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at              DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by              BIGINT UNSIGNED NULL COMMENT '创建人',
    updated_by              BIGINT UNSIGNED NULL COMMENT '更新人',
    deleted                 TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ps_object_period (stat_object_type, object_id, period_type, period_start)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='绩效统计表';

-- ------------------------------------------------------------
-- 24. biz_notification_record 客户通知记录表（02 §3.3.17）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS biz_notification_record (
    id                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    notify_no             VARCHAR(32)   NOT NULL COMMENT '通知流水号',
    dedup_key             VARCHAR(160)  NOT NULL COMMENT '去重键：工单/需求单+通知节点+接收人+通道',
    biz_type              VARCHAR(32)   NOT NULL COMMENT '业务类型：ORDER/DEMAND/EXCEPTION/CONFLICT',
    biz_id                BIGINT UNSIGNED NOT NULL COMMENT '关联业务记录',
    order_id              BIGINT UNSIGNED NULL COMMENT '关联工单',
    receiver_type         VARCHAR(16)   NOT NULL COMMENT '接收人类型：CUSTOMER 客户/DRIVER 司机/ADMIN 管理员',
    receiver_id           BIGINT UNSIGNED NOT NULL COMMENT '接收人 id：客户为 biz_customer.id、司机为 biz_staff.id、管理员为 sys_user.id',
    receiver_phone        VARCHAR(20)   NULL COMMENT '发送目标手机号快照',
    notify_node           VARCHAR(32)   NOT NULL COMMENT '通知节点：DISPATCHED/ACCEPTED/ARRIVED/EXCEPTION_REPLIED/COMPLETED/CANCELLED/DEMAND_REJECTED/DEMAND_CLOSED/TIMEOUT_CHECKIN/CONFLICT',
    channel               VARCHAR(16)   NOT NULL COMMENT '通道：WECHAT 微信订阅消息/SMS 短信/INAPP 站内',
    template_code         VARCHAR(64)   NULL COMMENT '模板编码，按模板统计短信用量与费用',
    content               VARCHAR(1000) NULL COMMENT '通知内容快照',
    send_status           VARCHAR(16)   NOT NULL DEFAULT 'PENDING' COMMENT '发送状态：PENDING 待发送/SUCCESS 成功/FAILED 失败/UNKNOWN 未知/DEGRADED 已降级',
    retry_count           INT           NOT NULL DEFAULT 0 COMMENT '已重试次数，默认最多 2 次且间隔递增',
    sent_at               DATETIME      NULL COMMENT '最近一次发送时间',
    fail_reason           VARCHAR(255)  NULL COMMENT '失败原因',
    degrade_from_channel  VARCHAR(16)   NULL COMMENT '降级来源通道',
    is_billable           TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '是否计费，短信为 1',
    created_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by            BIGINT UNSIGNED NULL COMMENT '创建人',
    updated_by            BIGINT UNSIGNED NULL COMMENT '更新人',
    deleted               TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_nr_dedup_key (dedup_key),
    KEY idx_nr_order_node (order_id, notify_node),
    KEY idx_nr_status_at (send_status, sent_at),
    KEY idx_nr_biz (biz_type, biz_id),
    KEY idx_nr_receiver (receiver_type, receiver_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='客户通知记录表';

-- ------------------------------------------------------------
-- 25. biz_vehicle_binding 车辆绑定记录表（02 §3.3.18）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS biz_vehicle_binding (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    vehicle_id     BIGINT UNSIGNED NOT NULL COMMENT '车辆',
    order_id       BIGINT UNSIGNED NOT NULL COMMENT '工单，车辆-工单-司机三方绑定',
    staff_id       BIGINT UNSIGNED NOT NULL COMMENT '司机',
    bound_at       DATETIME     NOT NULL COMMENT '绑定时间，外部占用与使用率统计计时起点',
    released_at    DATETIME     NULL COMMENT '释放时间',
    release_reason VARCHAR(64)  NULL COMMENT '释放原因：COMPLETED 结单/CANCELLED 取消/REASSIGN 改派',
    is_active      TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '绑定是否生效',
    created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by     BIGINT UNSIGNED NULL COMMENT '创建人',
    updated_by     BIGINT UNSIGNED NULL COMMENT '更新人',
    deleted        TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    KEY idx_vb_vehicle_active (vehicle_id, is_active),
    KEY idx_vb_order (order_id),
    KEY idx_vb_staff (staff_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='车辆绑定记录表';

-- ------------------------------------------------------------
-- 26. biz_vehicle_status_log 车辆状态变更记录表（02 §3.3.19）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS biz_vehicle_status_log (
    id                       BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    vehicle_id               BIGINT UNSIGNED NOT NULL COMMENT '车辆',
    before_status            VARCHAR(16)  NULL COMMENT '变更前状态',
    after_status             VARCHAR(16)  NOT NULL COMMENT '变更后状态',
    change_type              VARCHAR(32)  NOT NULL COMMENT '变更类型：DISPATCH 派单占用/RELEASE 结单释放/THIRD_PARTY_MARK/THIRD_PARTY_RESUME/MAINTENANCE/DISABLE/DAILY_CHECKOUT',
    order_id                 BIGINT UNSIGNED NULL COMMENT '关联工单',
    staff_id                 BIGINT UNSIGNED NULL COMMENT '操作司机',
    operator_id              BIGINT UNSIGNED NULL COMMENT '操作账号',
    occur_at                 DATETIME     NOT NULL COMMENT '变更发生时间',
    external_occupy_minutes  INT          NULL COMMENT '本次外部占用时长（分钟），THIRD_PARTY_RESUME 时回填',
    remark                   VARCHAR(255) NULL COMMENT '变更说明',
    created_at               DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at               DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by               BIGINT UNSIGNED NULL COMMENT '创建人',
    updated_by               BIGINT UNSIGNED NULL COMMENT '更新人',
    deleted                  TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    KEY idx_vsl_vehicle_at (vehicle_id, occur_at),
    KEY idx_vsl_type_at (change_type, occur_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='车辆状态变更记录表';

-- ------------------------------------------------------------
-- 27. biz_customer_review 客户评价表（02 §3.3.20）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS biz_customer_review (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_id    BIGINT UNSIGNED NOT NULL COMMENT '关联工单，一单一评价',
    customer_id BIGINT UNSIGNED NOT NULL COMMENT '评价客户',
    contact_id  BIGINT UNSIGNED NULL COMMENT '评价联系人',
    score       TINYINT      NOT NULL COMMENT '评分，1~5 星',
    content     VARCHAR(1000) NULL COMMENT '文字评价',
    is_default  TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否默认好评，客户未操作时由定时任务置 1',
    reviewed_at DATETIME     NOT NULL COMMENT '评价时间',
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by  BIGINT UNSIGNED NULL COMMENT '创建人',
    updated_by  BIGINT UNSIGNED NULL COMMENT '更新人',
    deleted     TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_cr_order (order_id),
    KEY idx_cr_customer (customer_id, reviewed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='客户评价表';

-- ------------------------------------------------------------
-- 28. ai_service_config AI 服务配置表（02 §3.4.1）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ai_service_config (
    id                       BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    name                     VARCHAR(64)  NOT NULL COMMENT '配置名称，供管理员识别',
    provider                 VARCHAR(64)  NULL COMMENT '服务供应方标识，通用文本字段，不体现具体厂商',
    endpoint                 VARCHAR(255) NOT NULL COMMENT '接口地址',
    api_key_cipher           VARCHAR(512) NOT NULL COMMENT 'API Key 密文，加密存储',
    model_name               VARCHAR(64)  NULL COMMENT '模型名称',
    channel_role             VARCHAR(16)  NOT NULL DEFAULT 'PRIMARY' COMMENT '通道角色：PRIMARY 主用/BACKUP 备用',
    scenario                 VARCHAR(32)  NOT NULL DEFAULT 'ALL' COMMENT '适用场景：BRIEFING/PERSONNEL/ALARM/ALL',
    timeout_seconds          INT          NOT NULL DEFAULT 30 COMMENT '调用超时时间（秒）',
    retry_max                INT          NOT NULL DEFAULT 2 COMMENT '失败自动重试次数上限',
    daily_call_limit         INT          NULL COMMENT '每日调用次数上限',
    monthly_call_limit       INT          NULL COMMENT '每月调用次数上限',
    max_input_size           INT          NULL COMMENT '单次调用最大输入规模',
    schedule_time            TIME         NULL COMMENT '定时生成执行时刻，默认 20:00',
    push_channel             VARCHAR(16)  NULL COMMENT '结果推送途径：INAPP/SMS/WECHAT',
    briefing_retention_days  INT          NOT NULL DEFAULT 10 COMMENT '简报保留天数，到期自动清除',
    is_enabled               TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '调用开关',
    created_at               DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at               DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by               BIGINT UNSIGNED NULL COMMENT '创建人',
    updated_by               BIGINT UNSIGNED NULL COMMENT '更新人',
    deleted                  TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI 服务配置表';

-- ------------------------------------------------------------
-- 29. ai_briefing AI 简报表（02 §3.4.2）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ai_briefing (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    briefing_no      VARCHAR(32)  NOT NULL COMMENT '简报编号',
    type             VARCHAR(32)  NOT NULL COMMENT '简报类型：DAILY/WEEKLY/MONTHLY/PERSONNEL/PERSONNEL_ANOMALY/PERSONNEL_RANK',
    period_start     DATE         NULL COMMENT '统计周期起始日期',
    period_end       DATE         NULL COMMENT '统计周期结束日期',
    content_markdown MEDIUMTEXT   NOT NULL COMMENT '简报正文，Markdown 格式',
    content_json     JSON         NULL COMMENT '结构化简报数据',
    ai_config_id     BIGINT UNSIGNED NULL COMMENT '使用的 AI 服务配置',
    call_status      VARCHAR(16)  NOT NULL DEFAULT 'SUCCESS' COMMENT '生成状态：SUCCESS 成功/FAILED 失败/SKIPPED 跳过',
    retry_count      INT          NOT NULL DEFAULT 0 COMMENT '重试次数',
    fail_reason      VARCHAR(255) NULL COMMENT '失败原因',
    generated_at     DATETIME     NOT NULL COMMENT '生成时间',
    expires_at       DATETIME     NOT NULL COMMENT '过期时间 = 生成时间 + 保留天数',
    created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by       BIGINT UNSIGNED NULL COMMENT '创建人',
    updated_by       BIGINT UNSIGNED NULL COMMENT '更新人',
    deleted          TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ab_briefing_no (briefing_no),
    KEY idx_ab_type_period (type, period_start),
    KEY idx_ab_config (ai_config_id),
    KEY idx_ab_generated_at (generated_at),
    KEY idx_ab_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI 简报表';

-- ------------------------------------------------------------
-- 30. ai_alarm_record AI 报警记录表（02 §3.4.3）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ai_alarm_record (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    alarm_no         VARCHAR(32)  NOT NULL COMMENT '报警编号',
    type             VARCHAR(32)  NOT NULL COMMENT '报警类型：NOT_ARRIVED 未到场/NOT_CHECKED_IN 未签到/VEHICLE_OCCUPIED 长时间占用/CONFLICT_TIMEOUT 冲突升级',
    level            VARCHAR(8)   NOT NULL COMMENT '等级：MEDIUM 中/HIGH 高',
    target_type      VARCHAR(16)  NOT NULL COMMENT '报警对象类型：ORDER/STAFF/VEHICLE',
    order_id         BIGINT UNSIGNED NULL COMMENT '关联工单',
    staff_id         BIGINT UNSIGNED NULL COMMENT '关联司机',
    vehicle_id       BIGINT UNSIGNED NULL COMMENT '关联车辆',
    dedup_key        VARCHAR(160) NOT NULL COMMENT '去重合并键：报警类型+对象+时间窗口',
    raw_data         JSON         NOT NULL COMMENT '原始数据摘要，供 AI 分析输入与人工复核',
    trigger_rule     VARCHAR(64)  NULL COMMENT '触发的报警规则标识',
    threshold_value  VARCHAR(64)  NULL COMMENT '触发时使用的阈值快照',
    ai_analysis      TEXT         NULL COMMENT 'AI 分析结果：是否真异常、可能原因、处置建议',
    ai_analyzed_at   DATETIME     NULL COMMENT 'AI 分析时间',
    ai_config_id     BIGINT UNSIGNED NULL COMMENT '使用的 AI 服务配置',
    status           VARCHAR(16)  NOT NULL DEFAULT 'OPEN' COMMENT '处理状态：OPEN 待处理/ANALYZED 已分析/HANDLED 已处置/CLOSED 已关闭',
    is_pinned        TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否置顶高亮，高等级报警为 1',
    occurred_at      DATETIME     NOT NULL COMMENT '触发时间',
    created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by       BIGINT UNSIGNED NULL COMMENT '创建人',
    updated_by       BIGINT UNSIGNED NULL COMMENT '更新人',
    deleted          TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_aar_dedup_key (dedup_key),
    KEY idx_aar_type_status (type, status),
    KEY idx_aar_level (level),
    KEY idx_aar_order (order_id),
    KEY idx_aar_staff (staff_id),
    KEY idx_aar_vehicle (vehicle_id),
    KEY idx_aar_config (ai_config_id),
    KEY idx_aar_occurred_at (occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI 报警记录表';

-- ------------------------------------------------------------
-- 31. biz_common_place 常用施工地点表（下单地点快捷选择）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS biz_common_place (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    place_name  VARCHAR(128) NOT NULL COMMENT '地点名称（展示与搜索用）',
    longitude   DECIMAL(10,6) NULL COMMENT '经度（GCJ-02，下单随地点回填）',
    latitude    DECIMAL(10,6) NULL COMMENT '纬度（GCJ-02，下单随地点回填）',
    sort_order  INT          NOT NULL DEFAULT 0 COMMENT '排序权重，越大越靠前',
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by  BIGINT UNSIGNED NULL COMMENT '创建人',
    updated_by  BIGINT UNSIGNED NULL COMMENT '更新人',
    deleted     TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_bcp_place_name (place_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='常用施工地点表';

-- =============================================================
-- 初始化数据（02 §6.3 + 01 §6.3 锁定字典）
-- =============================================================

-- 角色三类（01 §6.3）
INSERT INTO sys_role (id, code, name, data_scope, is_system, sort, description) VALUES
    (1, 'ADMIN',    '管理员', 'ALL',           1, 1, '管理员（含原总监职责），拥有全部权限'),
    (2, 'DRIVER',   '司机',   'SELF_STAFF',    1, 2, '司机（员工）'),
    (3, 'CUSTOMER', '客户',   'SELF_CUSTOMER', 1, 3, '客户（普通用户）')
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- 字典：01 §6.3 锁定枚举（字面量与 01 完全一致）
INSERT INTO sys_dict (dict_type, dict_code, dict_label, sort, remark) VALUES
    ('demand_order_status', 'PENDING',   '待处理',   1, '锁定于 01 §6.3'),
    ('demand_order_status', 'REVIEWING', '待复核',   2, '锁定于 01 §6.3'),
    ('demand_order_status', 'CONVERTED', '已转工单', 3, '锁定于 01 §6.3'),
    ('demand_order_status', 'REJECTED',  '已驳回',   4, '锁定于 01 §6.3'),
    ('demand_order_status', 'CLOSED',    '已关闭',   5, '锁定于 01 §6.3'),
    ('work_order_status', 'PENDING_ACCEPT', '待接单',   1, '锁定于 01 §6.3'),
    ('work_order_status', 'PENDING_ARRIVE', '待到场',   2, '锁定于 01 §6.3'),
    ('work_order_status', 'IN_PROGRESS',    '进行中',   3, '锁定于 01 §6.3'),
    ('work_order_status', 'SUSPENDED',      '施工受阻', 4, '锁定于 01 §6.3'),
    ('work_order_status', 'COMPLETED',      '已完成',   5, '锁定于 01 §6.3'),
    ('work_order_status', 'CANCELLED',      '已取消',   6, '锁定于 01 §6.3'),
    ('work_order_source', 'DEMAND', '需求单转入', 1, '锁定于 01 §6.3'),
    ('work_order_source', 'DIRECT', '管理员直建', 2, '锁定于 01 §6.3'),
    ('vehicle_status', 'IDLE',         '空闲',              1, '锁定于 01 §6.3'),
    ('vehicle_status', 'BUSY',         '忙碌（系统派单）',  2, '锁定于 01 §6.3'),
    ('vehicle_status', 'BUSY_THIRD',  '忙碌（第三方派单）', 3, '锁定于 01 §6.3'),
    ('vehicle_status', 'MAINTENANCE', '维保',              4, '锁定于 01 §6.3'),
    ('vehicle_status', 'DISABLED',    '停用',              5, '锁定于 01 §6.3'),
    ('attendance_type', 'CHECK_IN',       '签到',     1, '锁定于 01 §6.3'),
    ('attendance_type', 'DAILY_CHECKOUT', '当日签退', 2, '锁定于 01 §6.3'),
    ('attendance_type', 'TASK_END',       '任务结束', 3, '锁定于 01 §6.3'),
    ('change_request_type', 'CANCEL',         '取消工单', 1, '锁定于 01 §6.3'),
    ('change_request_type', 'VEHICLE_CHANGE', '车辆更换', 2, '锁定于 01 §6.3'),
    ('change_request_type', 'POSTPONE',       '延期',     3, '锁定于 01 §6.3'),
    ('change_request_type', 'ADDRESS_CHANGE', '改址',     4, '锁定于 01 §6.3'),
    ('change_request_status', 'PENDING',  '待审批', 1, '锁定于 01 §6.3'),
    ('change_request_status', 'APPROVED', '已通过', 2, '锁定于 01 §6.3'),
    ('change_request_status', 'REJECTED', '已驳回', 3, '锁定于 01 §6.3'),
    ('conflict_trigger_scene', 'AHEAD_WINDOW',    '提前提醒时段',     1, '锁定于 01 §6.3'),
    ('conflict_trigger_scene', 'DRIVER_POSTPONE', '司机申请延期',     2, '锁定于 01 §6.3'),
    ('conflict_trigger_scene', 'ADMIN_EDIT',      '管理员修改工单',   3, '锁定于 01 §6.3'),
    ('conflict_trigger_scene', 'ADMIN_DISPATCH',  '管理员重新派单',   4, '锁定于 01 §6.3'),
    ('conflict_status', 'PENDING',   '待处理', 1, '锁定于 01 §6.3'),
    ('conflict_status', 'HANDLED',   '已处置', 2, '锁定于 01 §6.3'),
    ('conflict_status', 'ESCALATED', '已升级', 3, '锁定于 01 §6.3'),
    ('conflict_status', 'CLOSED',    '已关闭', 4, '锁定于 01 §6.3'),
    ('conflict_handle_action', 'REASSIGN',        '改派',           1, '锁定于 01 §6.3'),
    ('conflict_handle_action', 'APPROVE_POSTPONE','延期审批',       2, '锁定于 01 §6.3'),
    ('conflict_handle_action', 'SUSPEND',         '暂停终止',       3, '锁定于 01 §6.3'),
    ('conflict_handle_action', 'NOTIFY',          '再次通知',       4, '锁定于 01 §6.3'),
    ('conflict_handle_action', 'CONTINUE',        '司机确认继续执行', 5, '锁定于 01 §6.3'),
    ('role_code', 'ADMIN',    '管理员', 1, '锁定于 01 §6.3'),
    ('role_code', 'DRIVER',   '司机',   2, '锁定于 01 §6.3'),
    ('role_code', 'CUSTOMER', '客户',   3, '锁定于 01 §6.3'),
    ('notify_channel', 'WECHAT', '微信订阅消息', 1, '锁定于 01 §6.3'),
    ('notify_channel', 'SMS',    '短信',         2, '锁定于 01 §6.3'),
    ('notify_channel', 'INAPP',  '站内',         3, '锁定于 01 §6.3'),
    -- 以下为 01 §6.3 未锁定、按 02/03 拟定值先行落地的字典（二期确认后回填 01）
    ('staff_status', 'IDLE',     '空闲',   1, '拟定值，待确认'),
    ('staff_status', 'BUSY',     '作业中', 2, '拟定值，待确认'),
    ('staff_status', 'AWAY',     '离岗',   3, '拟定值，待确认'),
    ('staff_status', 'RESIGNED', '离职',   4, '拟定值，待确认'),
    ('staff_position', 'DRIVER',  '司机', 1, '拟定值，待确认'),
    ('staff_position', 'EMPLOYEE', '员工', 2, '拟定值，待确认'),
    ('customer_type', 'COMPANY', '公司', 1, '拟定值，待确认'),
    ('customer_type', 'PERSON',   '个人', 2, '拟定值，待确认'),
    ('user_status', 'PENDING_BIND', '待绑定', 1, '拟定值，待确认'),
    ('user_status', 'ACTIVE',       '已激活', 2, '拟定值，待确认'),
    ('user_status', 'FROZEN',       '冻结',   3, '拟定值，待确认'),
    ('user_status', 'DISABLED',     '停用',   4, '拟定值，待确认'),
    ('demand_source', 'CUSTOMER_MP', '客户小程序提报', 1, '拟定值，待确认'),
    ('demand_source', 'PHONE',       '电话下单',       2, '拟定值，待确认'),
    ('demand_source', 'ADMIN',       '管理员录入',     3, '拟定值，待确认'),
    ('customer_visible_status', 'DISPATCHED', '已派单',       1, '拟定值，待确认'),
    ('customer_visible_status', 'ACCEPTED',   '已接单',       2, '拟定值，待确认'),
    ('customer_visible_status', 'STARTED',    '任务开始执行', 3, '拟定值，待确认'),
    ('customer_visible_status', 'PAUSED',     '任务暂停/推迟/终止', 4, '拟定值，待确认'),
    ('customer_visible_status', 'FINISHED',   '任务已完成',   5, '拟定值，待确认'),
    ('customer_visible_status', 'CANCELLED',  '任务已取消',   6, '拟定值，待确认'),
    ('exception_type', 'RAIN',           '下雨',     1, '拟定值，待确认'),
    ('exception_type', 'PARTY_STOP',     '甲方停工', 2, '拟定值，待确认'),
    ('exception_type', 'VEHICLE_FAULT',  '车辆故障', 3, '拟定值，待确认'),
    ('exception_type', 'CUSTOM',         '其他原因', 4, '拟定值，待确认'),
    ('exception_status', 'PENDING',  '待批复', 1, '拟定值，待确认'),
    ('exception_status', 'APPROVED', '已批复', 2, '拟定值，待确认'),
    ('exception_status', 'CLOSED',   '已关闭', 3, '拟定值，待确认'),
    ('exception_instruction', 'CONTINUE',  '继续施工', 1, '拟定值，待确认'),
    ('exception_instruction', 'SUSPEND',   '暂停',     2, '拟定值，待确认'),
    ('exception_instruction', 'TERMINATE', '终止',     3, '拟定值，待确认'),
    ('accept_action', 'ACCEPT', '接单', 1, '拟定值，待确认'),
    ('accept_action', 'REJECT', '拒单', 2, '拟定值，待确认'),
    ('reject_reason_type', 'VEHICLE_FAULT',   '车辆故障', 1, '拟定值，待确认'),
    ('reject_reason_type', 'TIME_CONFLICT',   '时间冲突', 2, '拟定值，待确认'),
    ('reject_reason_type', 'CAPABILITY_MISMATCH', '能力不符', 3, '拟定值，待确认'),
    ('reject_reason_type', 'OTHER',           '其他',     4, '拟定值，待确认')
ON DUPLICATE KEY UPDATE dict_label = VALUES(dict_label);

-- 系统配置参数（02 §6.3 默认值 + 系统品牌信息组，支持后台修改系统名称）
INSERT INTO sys_config (config_key, config_value, value_type, group_name, description) VALUES
    ('system.name',              '济南诚重吊装业务管理系统', 'STRING', 'system', '系统名称，全端展示，后台可改'),
    ('system.short_name',        '诚重吊装',                 'STRING', 'system', '系统简称，移动端标题等场景'),
    ('system.company_name',      '济南诚重吊装有限公司',     'STRING', 'system', '公司名称，隐私政策与页脚展示'),
    ('system.copyright',         '济南诚重吊装有限公司',     'STRING', 'system', '页脚版权文案'),
    ('system.logo_text',         '诚重',                     'STRING', 'system', '无 Logo 图片时的文字标识'),
    ('map.amap.web_key',         '',                         'STRING', 'map',    '高德 Web 服务 Key（后端逆地理/搜索代理用，不下发前端）'),
    ('map.amap.jsapi_key',       '',                         'STRING', 'map',    '高德 JSAPI Key（H5/后台地图渲染用，可下发前端）'),
    ('map.amap.security_jscode', '',                         'STRING', 'map',    '高德 JSAPI 安全密钥（配合 jsapi_key，可下发前端）'),
    ('map.mock',                 'true',                     'BOOLEAN','map',    '地图接口模拟模式：true 返回模拟数据（无 Key 联调），false 调真实高德'),
    ('auth.password_login_enabled', 'true',                  'BOOLEAN','auth',   '小程序/H5 端静态密码登录开关（后台/大屏不受控），实时生效'),
    ('conflict.ahead_window_minutes',        '60',   'INT',     'conflict',   '提前提醒时段 T（分钟）'),
    ('conflict.handle_deadline_minutes',     '15',   'INT',     'conflict',   '司机冲突处置时限（分钟），超时升级'),
    ('attendance.valid_range_meters',        '1000', 'INT',     'attendance', '签到有效范围（米）'),
    ('attendance.location_required',         'false','BOOLEAN', 'attendance', '签到/签退是否强制定位（测试阶段关闭；开启后范围外拒绝且坐标必填）'),
    ('attendance.photo_required',            'false','BOOLEAN', 'attendance', '打卡是否强制现场照片（测试阶段关闭）'),
    ('attendance.overtime_range_meters',     '1000', 'INT',     'attendance', '加班签退有效范围（米）'),
    ('attendance.photo_max_size_mb',         '10',   'INT',     'attendance', '单张附件大小上限（MB）'),
    ('attendance.photo_max_long_edge_px',    '1920', 'INT',     'attendance', '单张长边压缩上限（px）'),
    ('overtime.normal_off_time',             '18:00','STRING',  'overtime',   '正常下班时间节点'),
    ('order.accept_deadline_minutes',        '60',   'INT',     'order',      '接单时限（分钟），超时未确认触发接单报警'),
    ('order.max_pending_orders_per_staff',   '3',    'INT',     'order',      '同一司机同时未接单派单数上限'),
    ('demand.retention_days',                '10',   'INT',     'demand',     '需求单暂存时限（天）'),
    ('vehicle.third_party_remind_hours',     '24',   'INT',     'vehicle',    '第三方派单标记持续提醒阈值（小时）'),
    ('alarm.arrive_grace_minutes',           '15',   'INT',     'alarm',      '未到场报警宽限期（分钟）'),
    ('alarm.checkin_deadline_minutes',       '30',   'INT',     'alarm',      '未签到报警时限（分钟）'),
    ('alarm.vehicle_busy_hours',             '12',   'INT',     'alarm',      '车辆作业中占用报警阈值（小时）'),
    ('alarm.vehicle_third_party_hours',      '24',   'INT',     'alarm',      '车辆第三方派单占用报警阈值（小时）'),
    ('notification.sms_retry_max',           '2',    'INT',     'sms',        '短信失败重试次数上限'),
    ('notification.sms_daily_limit',         '200',  'INT',     'sms',        '短信每日发送上限（待业务确认）'),
    ('notification.sms_monthly_limit',       '3000', 'INT',     'sms',        '短信每月发送上限（待业务确认）'),
    ('notification.sms_dedup_enabled',       'true', 'BOOLEAN', 'sms',        '同工单同节点短信去重开关'),
    ('customer.activity_threshold',          '3',    'INT',     'customer',   '客户合作活跃度阈值（待业务确认）'),
    ('ai.daily_run_time',                    '20:00','STRING',  'ai',         'AI 定时生成执行时刻'),
    ('ai.briefing_retention_days',           '10',   'INT',     'ai',         '简报保留天数，到期清除'),
    ('ai.enabled',                           'false','BOOLEAN', 'ai',         'AI 接入总开关，关闭时所有 AI 功能不调用外部接口'),
    ('ai.api_base_url',                      '',     'STRING',  'ai',         'OpenAI 兼容接口 Base URL，如 https://api.openai.com/v1'),
    ('ai.api_key',                           '',     'STRING',  'ai',         'OpenAI 兼容接口 API Key（后台配置，不下发前端）'),
    ('ai.model',                             '',     'STRING',  'ai',         '模型名称，如 gpt-4o-mini / deepseek-chat'),
    ('storage.usage_alert_percent',          '80',   'INT',     'storage',    '存储使用率告警阈值（%）（待业务确认）')
ON DUPLICATE KEY UPDATE config_value = VALUES(config_value);
