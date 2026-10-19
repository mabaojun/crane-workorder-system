// 类型定义：与后端 WorkOrderDetailResponse / DemandOrder / ExceptionRecord 对齐（只取移动端用到的字段）
export interface WorkOrder {
  id: number
  orderNo: string
  taskName: string
  customerId: number
  customerName?: string
  staffId?: number
  staffName?: string
  staffPhone?: string
  vehicleId?: number
  vehiclePlateNo?: string
  address: string
  longitude?: number
  latitude?: number
  cargoName?: string
  cargoWeight?: number
  craneTonnage?: number
  /** 预计使用时长（天），仅备注提示 */
  estimatedDays?: number
  expectedStartAt?: string
  acceptedAt?: string
  startedAt?: string
  completedAt?: string
  status: string
  customerVisibleStatus: string
  customerVisibleStatusLabel: string
  createdAt?: string
}

export interface DemandOrder {
  id: number
  demandNo: string
  source: string
  contactName?: string
  contactPhone: string
  address: string
  longitude?: number
  latitude?: number
  description?: string
  craneTonnage?: number
  cargoWeight?: number
  /** 预计使用时长（天），客户自估仅备注提示 */
  estimatedDays?: number
  expectedStartAt?: string
  status: string
  reviewRemark?: string
  createdAt?: string
}

export interface ExceptionRecord {
  id: number
  orderId: number
  type: string
  typeLabel?: string
  description: string
  status: string
  isPublicToCustomer?: number
  reportedAt?: string
  repliedAt?: string
}

export interface MeInfo {
  userId: number
  role: string
  name: string
  phone: string
  staffId?: number
  customerId?: number
  dataScope?: string
  permissions?: string[]
}

export interface PageResult<T> {
  total: number
  list: T[]
}
