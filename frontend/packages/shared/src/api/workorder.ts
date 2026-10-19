// 工单域接口封装（03 §2 API-WO-001~008）
import { download, request } from './client'
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
  /** 用车种类：CRANE / LIFT / SEMI_TRAILER / HEAVY_TRAILER */
  vehicleType: string | null
  vehicleTonnage: number | null
  /** 举升高度（米），仅举升车有值 */
  vehicleLiftHeight: number | null
  address: string
  longitude: number | null
  latitude: number | null
  cargoName: string | null
  cargoQuantity: number | null
  cargoWeight: number | null
  craneTonnage: number | null
  /** 预计使用时长（天），仅备注提示 */
  estimatedDays: number | null
  /** 结算主体：ENTERPRISE 企业客户主体 / PERSONAL 个人结算 */
  settlementType: string
  /** 结算主体客户 id（企业主体时=归属客户） */
  settlementCustomerId: number | null
  /** 结算主体客户名称（企业主体回填客户名，个人结算为「个人」） */
  settlementCustomerName: string | null
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
  /** 用车种类，须与所选车辆种类一致；缺省按车辆种类 */
  vehicleType?: string
  planStartAt: string
  address: string
  longitude: number
  latitude: number
  /** 选填：货物名称 */
  cargoName?: string
  /** 选填：货物数量 */
  cargoQty?: number
  /** 选填：货物重量（吨） */
  cargoWeightTon?: number
  /** 吊车吨位，仅用车种类为 CRANE 时必填 */
  craneTonnage?: number
  /** 预计使用时长（天），选填，快捷 0.5/1/1.5/2 或自定义，仅备注提示 */
  estimatedDays?: number
  /** 结算主体：ENTERPRISE 企业客户主体（管理员选客户名称）/ PERSONAL 个人结算 */
  settlementType?: string
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
  /** 预计使用时长（天），选填，仅备注提示 */
  estimatedDays?: number
  /** 结算主体：ENTERPRISE 企业主体 / PERSONAL 个人结算，管理员可调整 */
  settlementType?: string
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

/** 工作台工单运行明细 Excel 导出（仅管理员，日期格式 YYYY-MM-DD） */
export function downloadWorkOrderExport(startDate: string, endDate: string) {
  return download('/work-order/orders/export', { query: { startDate, endDate } })
}
