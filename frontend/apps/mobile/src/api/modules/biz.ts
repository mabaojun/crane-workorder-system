// 工单 / 派单 / 打卡 / 异常 / 需求单接口封装
import { request } from '@crane/shared/api/client'
import type { PageResult, WorkOrder, DemandOrder, ExceptionRecord } from '../types'

export interface OrderListQuery {
  pageNum?: number
  pageSize?: number
  status?: string
  customerVisibleStatus?: string
  keyword?: string
}

export function listOrders(query: OrderListQuery = {}) {
  return request<PageResult<WorkOrder>>('/work-order/orders', { query })
}

export function getOrderDetail(id: number | string) {
  return request<WorkOrder>(`/work-order/orders/${id}`)
}

export function submitReview(id: number | string, body: { score: number; content?: string }) {
  return request<void>(`/work-order/orders/${id}/review`, { method: 'POST', body })
}

// ---- 司机端 ----

export function acceptOrder(id: number | string) {
  return request<void>(`/dispatch/orders/${id}/accept`, { method: 'POST', body: {} })
}

export function rejectOrder(id: number | string, body: { reasonType: string; reason: string }) {
  return request<void>(`/dispatch/orders/${id}/reject`, { method: 'POST', body })
}

/** 司机退单（已接单待到场 PENDING_ARRIVE）：工单回退待接单并实时通知管理端 */
export function withdrawOrder(id: number | string, body: { reasonType: string; reason: string }) {
  return request<void>(`/dispatch/orders/${id}/withdraw`, { method: 'POST', body })
}

/** 暂停工单申请继续施工（实时通知管理端） */
export function requestResume(id: number | string) {
  return request<{ requested: boolean }>(`/work-order/orders/${id}/resume-request`, { method: 'POST' })
}

export function checkIn(body: {
  orderId: number | string
  // 定位/照片强制开关关闭（测试阶段）时允许为空
  longitude: number | null
  latitude: number | null
  photoUrls: string[] | null
}) {
  return request<Record<string, unknown>>('/attendance/check-in', { method: 'POST', body })
}

export function dailyCheckout(body: {
  orderId: number | string
  longitude: number
  latitude: number
  photoUrls?: string[]
  isLeftSite?: boolean
  isReturned?: boolean
}) {
  return request<Record<string, unknown>>('/attendance/daily-checkout', { method: 'POST', body })
}

export function taskEnd(body: {
  orderId: number | string
  longitude: number
  latitude: number
  photoUrls: string[]
}) {
  return request<Record<string, unknown>>('/attendance/task-end', { method: 'POST', body })
}

export function createExceptionReport(body: {
  orderId: number | string
  type: string
  typeLabel?: string
  description: string
  imageUrls?: string[]
}) {
  return request<void>('/exception/reports', { method: 'POST', body })
}

export function listExceptionReports(query: { orderId?: number | string; pageNum?: number; pageSize?: number } = {}) {
  return request<PageResult<ExceptionRecord>>('/exception/reports', { query })
}

// ---- 客户端 ----

export function createDemandOrder(body: {
  source: string
  contactName?: string
  contactPhone: string
  address: string
  longitude?: number
  latitude?: number
  description?: string
  imageUrls?: string[]
  cargoWeight?: number
  /** 设备吨位（点选常用档位或直接输入），选填 */
  craneTonnage?: number
  /** 预计使用时长（天），选填，仅备注提示 */
  estimatedDays?: number
  expectedStartAt?: string
  parentDemandId?: number
}) {
  return request<DemandOrder>('/demand/orders', { method: 'POST', body })
}

export function listDemands(query: { pageNum?: number; pageSize?: number; status?: string } = {}) {
  return request<PageResult<DemandOrder>>('/demand/orders', { query })
}
