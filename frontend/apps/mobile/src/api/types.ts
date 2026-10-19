// 类型定义：与后端 WorkOrderDetailResponse / DemandOrder / ExceptionRecord 对齐（只取移动端用到的字段）
export interface WorkOrder {
  id: number
  orderNo: string
  taskName: string
  customerId: number
  customerName?: string
  /** 需求单提报联系人（客户下单时填写） */
  contactName?: string
  /** 需求单提报联系电话 */
  contactPhone?: string
  staffId?: number
  staffName?: string
  staffPhone?: string
  vehicleId?: number
  vehiclePlateNo?: string
  /** 用车种类：CRANE / LIFT / SEMI_TRAILER / HEAVY_TRAILER */
  vehicleType?: string
  vehicleTonnage?: number
  vehicleLiftHeight?: number
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
  /** 接单截止时间（超时触发报警） */
  acceptDeadlineAt?: string
  startedAt?: string
  completedAt?: string
  /** 下单备注 */
  remark?: string
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
  /** 用车种类：CRANE 吊车 / LIFT 举升车 / SEMI_TRAILER 板车（半挂）/ HEAVY_TRAILER 大件托盘车 */
  vehicleType?: string
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

/** 登录账号可选身份 */
export interface RoleOption {
  /** ADMIN / DRIVER / CUSTOMER */
  code: string
  name: string
}

export interface MeInfo {
  userId: number
  role: string
  /** 账号可选身份（一人多角色时登录后选择） */
  availableRoles?: RoleOption[]
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
