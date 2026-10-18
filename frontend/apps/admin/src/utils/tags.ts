// 状态标签映射：语义色对齐设计令牌（11 §2），标签文案取自 @crane/shared 字典
// tagOf 返回可直接 v-bind 到 el-tag 的属性；solid 为自定义色（需配 tag-solid 类）

export interface TagProps {
  label: string
  type?: 'primary' | 'success' | 'warning' | 'info' | 'danger'
  color?: string
  solid?: boolean
}

function t(label: string, type: TagProps['type']): TagProps {
  return { label, type }
}
function solid(label: string, color: string): TagProps {
  return { label, color, solid: true }
}

import {
  WORK_ORDER_STATUS_LABEL,
  DEMAND_ORDER_STATUS_LABEL,
  VEHICLE_STATUS_LABEL
} from '@crane/shared'

export function workOrderTag(status?: string | null): TagProps {
  switch (status) {
    case 'PENDING_ACCEPT': return t(WORK_ORDER_STATUS_LABEL[status], 'info')
    case 'PENDING_ARRIVE': return t(WORK_ORDER_STATUS_LABEL[status], 'warning')
    case 'IN_PROGRESS': return t(WORK_ORDER_STATUS_LABEL[status], 'primary')
    case 'SUSPENDED': return solid(WORK_ORDER_STATUS_LABEL[status] ?? '施工受阻', '#d97706')
    case 'COMPLETED': return t(WORK_ORDER_STATUS_LABEL[status], 'success')
    case 'CANCELLED': return t(WORK_ORDER_STATUS_LABEL[status], 'danger')
    default: return { label: status || '-' }
  }
}

export function demandTag(status?: string | null): TagProps {
  switch (status) {
    case 'PENDING': return t(DEMAND_ORDER_STATUS_LABEL[status], 'primary')
    case 'REVIEWING': return t(DEMAND_ORDER_STATUS_LABEL[status], 'warning')
    case 'CONVERTED': return t(DEMAND_ORDER_STATUS_LABEL[status], 'success')
    case 'REJECTED': return t(DEMAND_ORDER_STATUS_LABEL[status], 'danger')
    case 'CLOSED': return t(DEMAND_ORDER_STATUS_LABEL[status], 'info')
    default: return { label: status || '-' }
  }
}

export function vehicleTag(status?: string | null): TagProps {
  switch (status) {
    case 'IDLE': return t(VEHICLE_STATUS_LABEL[status], 'success')
    case 'BUSY': return t(VEHICLE_STATUS_LABEL[status], 'primary')
    case 'BUSY_THIRD': return solid(VEHICLE_STATUS_LABEL[status] ?? '第三方派单', '#7c3aed')
    case 'MAINTENANCE': return t(VEHICLE_STATUS_LABEL[status], 'warning')
    case 'DISABLED': return t(VEHICLE_STATUS_LABEL[status], 'danger')
    default: return { label: status || '-' }
  }
}

export const STAFF_STATUS_LABEL: Record<string, string> = {
  IDLE: '在岗',
  BUSY: '作业中',
  AWAY: '请假',
  RESIGNED: '离职'
}

export function staffTag(status?: string | null): TagProps {
  switch (status) {
    case 'IDLE': return t(STAFF_STATUS_LABEL[status], 'success')
    case 'BUSY': return t(STAFF_STATUS_LABEL[status], 'primary')
    case 'AWAY': return t(STAFF_STATUS_LABEL[status], 'warning')
    case 'RESIGNED': return t(STAFF_STATUS_LABEL[status], 'info')
    default: return { label: status || '-' }
  }
}

export const USER_STATUS_LABEL: Record<string, string> = {
  PENDING_BIND: '待绑定',
  ACTIVE: '正常',
  FROZEN: '已冻结',
  DISABLED: '已停用'
}

export function userTag(status?: string | null): TagProps {
  switch (status) {
    case 'PENDING_BIND': return t(USER_STATUS_LABEL[status], 'info')
    case 'ACTIVE': return t(USER_STATUS_LABEL[status], 'success')
    case 'FROZEN': return t(USER_STATUS_LABEL[status], 'warning')
    case 'DISABLED': return t(USER_STATUS_LABEL[status], 'danger')
    default: return { label: status || '-' }
  }
}

export const ROLE_LABEL: Record<string, string> = {
  ADMIN: '管理员',
  DRIVER: '司机',
  CUSTOMER: '客户'
}

export const POSITION_LABEL: Record<string, string> = {
  DRIVER: '司机',
  EMPLOYEE: '员工'
}

export const DEMAND_SOURCE_LABEL: Record<string, string> = {
  CUSTOMER_MP: '小程序提报',
  PHONE: '电话下单',
  ADMIN: '管理员录入'
}

export const ORDER_SOURCE_LABEL: Record<string, string> = {
  DEMAND: '需求单转化',
  DIRECT: '直接创建'
}

export const EXCEPTION_TYPE_LABEL: Record<string, string> = {
  RAIN: '下雨',
  PARTY_STOP: '甲方停工',
  VEHICLE_FAULT: '车辆故障',
  CUSTOM: '自定义'
}

export const EXCEPTION_STATUS_LABEL: Record<string, string> = {
  PENDING: '待批复',
  APPROVED: '已批复',
  CLOSED: '已关闭'
}

export function exceptionTag(status?: string | null): TagProps {
  switch (status) {
    case 'PENDING': return t(EXCEPTION_STATUS_LABEL[status], 'warning')
    case 'APPROVED': return t(EXCEPTION_STATUS_LABEL[status], 'success')
    case 'CLOSED': return t(EXCEPTION_STATUS_LABEL[status], 'info')
    default: return { label: status || '-' }
  }
}

export const INSTRUCTION_LABEL: Record<string, string> = {
  CONTINUE: '继续施工',
  SUSPEND: '暂停',
  TERMINATE: '终止'
}

export const REJECT_REASON_LABEL: Record<string, string> = {
  VEHICLE_FAULT: '车辆故障',
  TIME_CONFLICT: '时间冲突',
  ABILITY_MISMATCH: '能力不匹配',
  OTHER: '其他'
}

/** 前置校验项中文名 */
export const CHECK_CODE_LABEL: Record<string, string> = {
  VEHICLE_IDLE: '车辆空闲',
  STAFF_ON_DUTY: '司机在岗',
  STAFF_NO_ACTIVE_ORDER: '司机无进行中工单',
  PENDING_DISPATCH_LIMIT: '待接单上限',
  TIME_VALID: '时间有效'
}
