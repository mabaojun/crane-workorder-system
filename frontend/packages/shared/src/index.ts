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
