// 派单域接口封装（03 §2 API-DISP-001~007）
import { request } from './client'
import type { PageData } from '../index'

/** 前置校验项：VEHICLE_IDLE / STAFF_ON_DUTY / STAFF_NO_ACTIVE_ORDER / PENDING_DISPATCH_LIMIT / TIME_VALID */
export interface CheckItem {
  code: string
  pass: boolean
  message: string
}

export interface PrecheckResult {
  pass: boolean
  checks: CheckItem[]
}

export interface PrecheckRequest {
  staffId: number
  vehicleId: number
  planStartAt: string
}

export interface DispatchRequest {
  staffId: number
  vehicleId: number
  planStartAt?: string
  /** 计划时间早于当前时的二次确认 */
  confirmPast?: boolean
  remark?: string
}

/** 待重新派单队列项（后端聚合出参） */
export interface PendingRedispatchItem {
  orderId: number
  orderNo: string
  taskName: string
  /** 原始工单信息：供重新派单决策 */
  customerId: number | null
  customerName: string | null
  address: string | null
  longitude: number | null
  latitude: number | null
  craneTonnage: number | null
  cargoName: string | null
  cargoWeight: number | null
  remark: string | null
  rejectReason: string | null
  rejectReasonType: string | null
  rejectedAt: string | null
  planStartAt: string | null
}

/** 接单拒单记录 */
export interface OrderAcceptLog {
  id: number
  orderId: number
  staffId: number | null
  /** ACCEPT / REJECT */
  action: string
  reasonType: string | null
  reason: string | null
  actedAt: string
  deliverRound: number
}

/** 派单前置校验（五项） */
export function precheckDispatch(body: PrecheckRequest) {
  return request<PrecheckResult>('/dispatch/precheck', { method: 'POST', body })
}

/** 派单 / 改派（工单需处于 PENDING_ACCEPT） */
export function dispatchOrder(workOrderId: number | string, body: DispatchRequest) {
  return request<{ orderId: number; orderNo: string; status: string }>(
    `/dispatch/orders/${workOrderId}/dispatch`,
    { method: 'POST', body }
  )
}

/** 待重新派单队列 */
export function pagePendingRedispatch(query: { keyword?: string; pageNum?: number; pageSize?: number }) {
  return request<PageData<PendingRedispatchItem>>('/dispatch/pending-redispatch', { query })
}

/** 接单拒单记录 */
export function getAcceptLogs(workOrderId: number | string) {
  return request<{ list: OrderAcceptLog[] }>(
    `/dispatch/orders/${workOrderId}/logs`
  )
}
