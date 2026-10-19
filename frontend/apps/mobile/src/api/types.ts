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
  /** 结算主体：ENTERPRISE 企业主体 / PERSONAL 个人主体 */
  settlementType?: string
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
  /** 客户联系人账号绑定的联系人 id（企业多联系人提报身份） */
  contactId?: number
  /** 所属客户名称（企业主体展示） */
  customerName?: string
  /** 所属客户类型：COMPANY / PERSON */
  customerType?: string
  dataScope?: string
  permissions?: string[]
}

export interface PageResult<T> {
  total: number
  list: T[]
}
