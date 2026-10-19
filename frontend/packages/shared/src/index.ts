// @crane/shared 统一出口：类型、枚举、接口客户端、权限位、主题令牌
// 枚举字面量与 01 §6.3 / 03 §1.4 逐字一致，禁止各端自定义
export const WORK_ORDER_STATUS = {
  PENDING_ACCEPT: 'PENDING_ACCEPT',
  PENDING_ARRIVE: 'PENDING_ARRIVE',
  IN_PROGRESS: 'IN_PROGRESS',
  SUSPENDED: 'SUSPENDED',
  COMPLETED: 'COMPLETED',
  CANCELLED: 'CANCELLED'
} as const

export const WORK_ORDER_STATUS_LABEL: Record<string, string> = {
  PENDING_ACCEPT: '待接单',
  PENDING_ARRIVE: '待到场',
  IN_PROGRESS: '进行中',
  SUSPENDED: '施工受阻',
  COMPLETED: '已完成',
  CANCELLED: '已取消'
}

export const DEMAND_ORDER_STATUS_LABEL: Record<string, string> = {
  PENDING: '待处理',
  REVIEWING: '待复核',
  CONVERTED: '已转工单',
  REJECTED: '已驳回',
  CLOSED: '已关闭'
}

export const VEHICLE_STATUS = {
  IDLE: 'IDLE',
  BUSY: 'BUSY',
  BUSY_THIRD: 'BUSY_THIRD',
  MAINTENANCE: 'MAINTENANCE',
  DISABLED: 'DISABLED'
} as const

export const VEHICLE_STATUS_LABEL: Record<string, string> = {
  IDLE: '空闲',
  BUSY: '忙碌（系统派单）',
  BUSY_THIRD: '忙碌（第三方派单）',
  MAINTENANCE: '维保',
  DISABLED: '停用'
}

/** 用车/车辆种类（01 §6.3 扩展） */
export const VEHICLE_TYPE = {
  CRANE: 'CRANE',
  LIFT: 'LIFT',
  SEMI_TRAILER: 'SEMI_TRAILER',
  HEAVY_TRAILER: 'HEAVY_TRAILER'
} as const

export const VEHICLE_TYPE_LABEL: Record<string, string> = {
  CRANE: '吊车',
  LIFT: '举升车',
  SEMI_TRAILER: '板车（半挂）',
  HEAVY_TRAILER: '大件托盘车'
}

/** 可选用车种类（客户下单、转工单、直建工单共用） */
export const VEHICLE_TYPE_OPTIONS = [
  { value: VEHICLE_TYPE.CRANE, label: VEHICLE_TYPE_LABEL.CRANE },
  { value: VEHICLE_TYPE.LIFT, label: VEHICLE_TYPE_LABEL.LIFT },
  { value: VEHICLE_TYPE.SEMI_TRAILER, label: VEHICLE_TYPE_LABEL.SEMI_TRAILER },
  { value: VEHICLE_TYPE.HEAVY_TRAILER, label: VEHICLE_TYPE_LABEL.HEAVY_TRAILER }
]

/** 是否吊车类型（空值按历史数据视为吊车） */
export function isCraneType(type?: string | null): boolean {
  return !type || type === VEHICLE_TYPE.CRANE
}

export const ROLE_CODE = {
  ADMIN: 'ADMIN',
  DRIVER: 'DRIVER',
  CUSTOMER: 'CUSTOMER'
} as const

export type RoleCode = (typeof ROLE_CODE)[keyof typeof ROLE_CODE]

// 统一响应信封（03 §1.4）
export interface ApiEnvelope<T> {
  code: number
  message: string
  data: T
  traceId: string
}

// 分页出参（03 §1.4）
export interface PageData<T> {
  total: number
  list: T[]
}
