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
  /** 车辆种类分布：CRANE/LIFT/SEMI_TRAILER/HEAVY_TRAILER → 台数 */
  typeCounts?: Record<string, number>
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

export type StatAlarmType = 'CONFLICT' | 'NOT_ACCEPTED' | 'NOT_ARRIVED' | 'NOT_CHECKED_IN'
  | 'VEHICLE_OCCUPIED' | 'VEHICLE_INSURANCE_EXPIRING' | 'VEHICLE_INSPECTION_EXPIRING'
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
  /** 用车种类：CRANE / LIFT / SEMI_TRAILER / HEAVY_TRAILER */
  vehicleType?: string | null
  /** 车辆规格展示：25吨 / 举升20米 / 板车 / 大件托盘车 */
  vehicleSpec?: string | null
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

/** 工作台状态看板：工单视角 */
export interface StatusBoardOrder {
  id: number
  orderNo: string
  taskName: string
  customerId: number | null
  customerName: string | null
  staffId: number | null
  staffName: string | null
  vehicleId: number | null
  vehiclePlateNo: string | null
  vehicleType: string | null
  tonnage: number | null
  liftHeight: number | null
  status: string
  address: string
  expectedStartAt: string | null
  acceptedAt: string | null
  startedAt: string | null
  completedAt: string | null
  cancelledAt: string | null
  durationMinutes: number | null
  ongoing: boolean
}

/** 最新签到位置 */
export interface StatusBoardCheckIn {
  lng: number | null
  latitude: number | null
  checkedAt: string | null
  distanceMeters: number | null
}

/** 人员视角 */
export interface StatusBoardStaff {
  staffId: number
  name: string
  phone: string
  position: string
  status: string
  currentOrder: StatusBoardOrder | null
  totalMinutes: number
  orders: StatusBoardOrder[]
}

/** 车辆视角 */
export interface StatusBoardVehicle {
  vehicleId: number
  plateNo: string
  type: string
  tonnage: number | null
  liftHeight: number | null
  status: string
  currentOrderId: number | null
  currentOrder: StatusBoardOrder | null
  latestCheckIn: StatusBoardCheckIn | null
  orders: StatusBoardOrder[]
}

export interface StatusBoard {
  date: string
  totalOrders: number
  staff: StatusBoardStaff[]
  vehicles: StatusBoardVehicle[]
  orders: StatusBoardOrder[]
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

/** 工作台状态看板：按日期聚合人员 / 车辆 / 工单 */
export function fetchStatusBoard(date?: string) {
  return request<StatusBoard>('/stat/status-board', { query: { date } })
}
