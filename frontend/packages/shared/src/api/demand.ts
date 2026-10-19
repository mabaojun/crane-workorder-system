// 需求单域接口封装（03 §2 API-DEMAND-*）
import { request } from './client'
import type { PageData } from '../index'

/** 需求单实体，字段与后端 DemandOrder 一致 */
export interface DemandOrder {
  id: number
  demandNo: string
  /** 来源：CUSTOMER_MP / PHONE / ADMIN */
  source: string
  customerId: number | null
  contactId: number | null
  contactName: string
  contactPhone: string
  address: string
  longitude: number | null
  latitude: number | null
  description: string
  /** JSON 数组字符串 */
  imageUrls: string | null
  intendedVehicleId: number | null
  /** 用车种类：CRANE 吊车 / LIFT 举升车 / SEMI_TRAILER 板车（半挂）/ HEAVY_TRAILER 大件托盘车 */
  vehicleType: string | null
  /** 吊车吨位，仅 CRANE 类型有值 */
  craneTonnage: number | null
  cargoWeight: number | null
  /** 预计使用时长（天），客户自估仅备注提示 */
  estimatedDays: number | null
  /** 结算主体：ENTERPRISE 企业主体 / PERSONAL 个人主体 */
  settlementType: string
  expectedStartAt: string | null
  /** PENDING / REVIEWING / CONVERTED / REJECTED / CLOSED */
  status: string
  reviewRemark: string | null
  rejectCount: number | null
  parentDemandId: number | null
  convertedOrderId: number | null
  expiresAt: string | null
  closedAt: string | null
  createdAt: string
  updatedAt: string
}

export interface DemandReviewRequest {
  approve: boolean
  reviewRemark?: string
}

export interface DemandConvertRequest {
  staffId: number
  vehicleId: number
  /** 客户联系人：企业客户可改选该公司下具体联系人，缺省沿用需求单提报人 */
  contactId?: number
  /** ISO：yyyy-MM-ddTHH:mm:ss */
  planStartAt: string
  /** 用车种类，缺省沿用需求单提报值；须与所选车辆种类一致 */
  vehicleType?: string
  /** 吊车吨位，仅 CRANE 类型填 */
  craneTonnage?: number
  /** 实际吊装货物重量（吨），客户未提报时允许为空 */
  cargoWeightTon?: number
  /** 预计使用时长（天），选填，缺省带入需求单客户自估值 */
  estimatedDays?: number
  /** 结算主体：ENTERPRISE 企业客户主体（可改选企业客户名）/ PERSONAL 个人结算 */
  settlementType?: string
  remark: string
}

/** 分页查询需求单 */
export function pageDemands(query: { status?: string; pageNum?: number; pageSize?: number }) {
  return request<PageData<DemandOrder>>('/demand/orders', { query })
}

/** 需求单详情 */
export function getDemand(id: number | string) {
  return request<DemandOrder>(`/demand/orders/${id}`)
}

/** 复核：approve=true 置 REVIEWING，false 驳回 */
export function reviewDemand(id: number | string, body: DemandReviewRequest) {
  return request<DemandOrder>(`/demand/orders/${id}/review`, { method: 'POST', body })
}

/** 转工单，convertedOrderId 指向新工单 */
export function convertDemand(id: number | string, body: DemandConvertRequest) {
  return request<DemandOrder>(`/demand/orders/${id}/convert`, { method: 'POST', body })
}
