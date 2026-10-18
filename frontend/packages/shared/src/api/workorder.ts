// 工单域接口封装（03 §2 API-WO-001~008）
import { request } from './client'
import type { PageData } from '../index'

/** 工单详情 / 列表聚合出参，字段与后端 WorkOrderDetailResponse 一致 */
export interface WorkOrder {
  id: number
  orderNo: string
  taskName: string
  /** DEMAND / DIRECT */
  source: string
  demandId: number | null
  customerId: number | null
  customerName: string | null
  contactId: number | null
  staffId: number | null
  staffName: string | null
  staffPhone: string | null
  vehicleId: number | null
  vehiclePlateNo: string | null
  vehicleTonnage: number | null
  address: string
  longitude: number | null
  latitude: number | null
  cargoName: string | null
  cargoQuantity: number | null
  cargoWeight: number | null
  craneTonnage: number | null
  expectedStartAt: string | null
  acceptedAt: string | null
  startedAt: string | null
  suspendedAt: string | null
  completedAt: string | null
  cancelledAt: string | null
  cancelledReason: string | null
  /** 六态：PENDING_ACCEPT / PENDING_ARRIVE / IN_PROGRESS / SUSPENDED / COMPLETED / CANCELLED */
  status: string
  customerVisibleStatus: string | null
  customerVisibleStatusLabel: string | null
  acceptDeadlineAt: string | null
  timeoutRemindedAt: string | null
  isPinnedAlarm: number | null
  remark: string | null
  requirement: string | null
  createdAt: string
  updatedAt: string
}

/** 状态变更记录 */
export interface WorkOrderStatusLog {
  id: number
  orderId: number
  fromStatus: string
  toStatus: string
  operatorId: number | null
  remark: string | null
  createdAt: string
}

export interface WorkOrderCreateRequest {
  /** 选填：不填时后端按「客户名·吊车吨位·司机姓名」自动生成 */
  taskName?: string
  /** DIRECT */
  source: string
  customerId: number
  contactId?: number
  staffId: number
  vehicleId: number
  planStartAt: string
  address: string
  longitude: number
  latitude: number
  cargoName: string
  cargoQty: number
  /** 选填：货物重量（吨） */
  cargoWeightTon?: number
  craneTonnage: number
  remark?: string
  requirement?: string
  /** 时间早于当前时的二次确认 */
  confirmPast?: boolean
}

/** API-WO-004 修改工单请求体（PATCH，未传字段不变） */
export interface WorkOrderUpdateRequest {
  taskName?: string
  address?: string
  /** 与 latitude 成对 */
  longitude?: number
  /** 与 longitude 成对 */
  latitude?: number
  staffId?: number
  vehicleId?: number
  planStartAt?: string
  craneTonnage?: number
  cargoWeightTon?: number
  remark?: string
  requirement?: string
  /** 计划时间早于当前时的二次确认 */
  confirmPast?: boolean
}

export interface WorkOrderStatusChangeRequest {
  /** CANCEL / SUSPEND / RESUME */
  action: string
  reason?: string
}

export interface WorkOrderListQuery {
  status?: string
  customerVisibleStatus?: string
  source?: string
  customerId?: number
  staffId?: number
  vehicleId?: number
  keyword?: string
  pageNum?: number
  pageSize?: number
}

export function pageWorkOrders(query: WorkOrderListQuery) {
  // 展开为对象字面量以匹配 request 的 Record 约束
  return request<PageData<WorkOrder>>('/work-order/orders', { query: { ...query } })
}

export function getWorkOrder(id: number | string) {
  return request<WorkOrder>(`/work-order/orders/${id}`)
}

/** 管理员直建工单（source=DIRECT） */
export function createWorkOrder(body: WorkOrderCreateRequest) {
  return request<{ orderId: number; orderNo: string; status: string }>(
    '/work-order/orders',
    { method: 'POST', body }
  )
}

/** 修改工单（PATCH，仅传变更字段） */
export function updateOrder(id: number | string, body: WorkOrderUpdateRequest) {
  return request<WorkOrder>(`/work-order/orders/${id}`, { method: 'PATCH', body })
}

/** 状态变更：CANCEL / SUSPEND / RESUME */
export function changeWorkOrderStatus(id: number | string, body: WorkOrderStatusChangeRequest) {
  return request<{ orderId: number; status: string }>(
    `/work-order/orders/${id}/status`,
    { method: 'POST', body }
  )
}

/** 兜底结单 */
export function settleWorkOrder(id: number | string) {
  return request<{ orderId: number; status: string }>(
    `/work-order/orders/${id}/settle`,
    { method: 'POST' }
  )
}

/** 管理员批准暂停工单继续施工（自动关闭未批复异常并实时通知） */
export function resumeWorkOrder(id: number | string) {
  return request<{ orderId: number; status: string; customerVisibleStatus: string }>(
    `/work-order/orders/${id}/resume`,
    { method: 'POST' }
  )
}

/** 司机对暂停工单申请继续施工（SSE 实时通知管理端） */
export function requestResumeWorkOrder(id: number | string) {
  return request<{ requested: boolean }>(
    `/work-order/orders/${id}/resume-request`,
    { method: 'POST' }
  )
}

/** 状态变更记录 */
export function getWorkOrderStatusLogs(id: number | string, pageNum = 1, pageSize = 50) {
  return request<PageData<WorkOrderStatusLog>>(
    `/work-order/orders/${id}/status-logs`,
    { query: { pageNum, pageSize } }
  )
}
