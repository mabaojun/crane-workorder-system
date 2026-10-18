// 统计域大屏接口封装（06 §3/§4，仅管理员可见；字段以后端实测契约为准）
import { request } from './client'

export interface StatusDistItem {
  status: string
  count: number
}

export interface TrendPoint {
  date: string
  count: number
}

export interface ExceptionDistItem {
  type: string
  label: string
  count: number
}

export interface PendingDemandItem {
  demandNo: string
  title: string
  contactName: string
  status: string
  createdAt: string
}

export interface DashboardStaffRank {
  staffId: number
  name: string
  orderCount: number
  completedCount: number
}

export interface StatDashboardOrder {
  today: number
  week: number
  month: number
  inProgress: number
  suspended: number
  completed: number
  pendingAccept: number
  pendingArrive: number
  cancelled: number
  statusDistribution: StatusDistItem[]
}

export interface StatDashboardVehicle {
  total: number
  idle: number
  busy: number
  busyThird: number
  maintenance: number
  disabled: number
  externalOccupyVehicles: number
  externalOccupyMinutesToday: number
}

export interface StatDashboardStaff {
  total: number
  onDuty: number
  busy: number
  idle: number
  away: number
}

export interface StatDashboardCustomer {
  newThisMonth: number
  demandMonth: number
  demandPending: number
}

export interface StatDashboard {
  order: StatDashboardOrder
  trend7d: TrendPoint[]
  exceptionDistribution: ExceptionDistItem[]
  vehicle: StatDashboardVehicle
  staff: StatDashboardStaff
  customer: StatDashboardCustomer
  pendingDemands: PendingDemandItem[]
  staffRank: DashboardStaffRank[]
}

export type StatAlarmType = 'CONFLICT' | 'NOT_ARRIVED' | 'NOT_CHECKED_IN' | 'VEHICLE_OCCUPIED'
export type StatAlarmLevel = 'HIGH' | 'MEDIUM'

export interface StatAlarmItem {
  alarmNo: string
  type: StatAlarmType
  level: StatAlarmLevel
  targetType: string
  orderId: number | null
  orderNo: string | null
  vehicleId: number | null
  plateNo: string | null
  staffId: number | null
  staffName: string | null
  summary: string
  triggeredAt: string
  durationMinutes: number | null
  pinned: boolean
}

export interface StatAlarms {
  count: number
  list: StatAlarmItem[]
}

export interface VehicleMapPoint {
  vehicleId: number
  plateNo: string
  staffName: string | null
  orderNo: string | null
  status: string
  lng: number
  latitude: number
  alarm: boolean
  updatedAt: string
}

export interface StatVehicleMap {
  total: number
  onMap: number
  points: VehicleMapPoint[]
}

export interface StaffPerformanceItem {
  staffId: number
  name: string
  orderCount: number
  completedCount: number
  completionRate: number
}

/** 驾驶舱聚合指标（60s 刷新） */
export function fetchStatDashboard() {
  return request<StatDashboard>('/stat/dashboard')
}

/** 报警清单（5s 短轮询，后端已按置顶规则排序） */
export function fetchStatAlarms() {
  return request<StatAlarms>('/stat/alarms')
}

/** 车辆分布点位（15s 刷新） */
export function fetchStatVehicleMap() {
  return request<StatVehicleMap>('/stat/vehicle-map')
}

/** 人效排名（页面加载 + 每日 1 次） */
export function fetchStaffPerformance(period: 'WEEK' | 'MONTH' = 'MONTH') {
  return request<StaffPerformanceItem[]>('/stat/performance/staff', { query: { period } })
}
