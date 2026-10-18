// 异常上报与批复域接口封装（API-EXC-001~004）
import { request } from './client'
import type { PageData } from '../index'

/** 异常记录，字段与后端 ExceptionRecord 一致 */
export interface ExceptionRecord {
  id: number
  orderId: number
  staffId: number | null
  /** RAIN / PARTY_STOP / VEHICLE_FAULT / CUSTOM */
  type: string
  typeLabel: string | null
  description: string | null
  /** JSON 数组字符串 */
  imageUrls: string | null
  /** PENDING / APPROVED / CLOSED */
  status: string
  /** 批复指令：CONTINUE / SUSPEND / TERMINATE */
  instruction: string | null
  orderStatusBefore: string | null
  orderStatusAfter: string | null
  isPublicToCustomer: number | null
  reportedAt: string
  repliedAt: string | null
  createdAt: string
  updatedAt: string
}

export interface ExceptionReplyRequest {
  /** true 按指令执行，false 仅记录驳回 */
  approve: boolean
  /** CONTINUE / SUSPEND / TERMINATE */
  instruction?: string
  opinion?: string
}

export function pageExceptions(query: {
  status?: string
  orderId?: number
  staffId?: number
  pageNum?: number
  pageSize?: number
}) {
  return request<PageData<ExceptionRecord>>('/exception/reports', { query })
}

export function getException(id: number | string) {
  return request<ExceptionRecord>(`/exception/reports/${id}`)
}

/** 异常批复（管理员） */
export function replyException(id: number | string, body: ExceptionReplyRequest) {
  return request<ExceptionRecord>(`/exception/reports/${id}/reply`, { method: 'POST', body })
}
