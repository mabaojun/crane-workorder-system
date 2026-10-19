// 员工与车辆档案域接口封装（管理端专用）
import { request } from './client'
import type { PageData } from '../index'
import { VEHICLE_TYPE_LABEL } from '../index'

/** 员工档案，字段与后端 Staff 一致 */
export interface Staff {
  id: number
  staffNo: string
  name: string
  phone: string
  idCard: string | null
  /** DRIVER / EMPLOYEE */
  position: string
  /** IDLE / BUSY / AWAY / RESIGNED */
  status: string
  licenseType: string | null
  specialCertNo: string | null
  specialCertPhotoUrl: string | null
  emergencyName: string | null
  emergencyPhone: string | null
  hireDate: string | null
  /** 绑定登录账号 sys_user.id，null 表示未创建账号 */
  userId: number | null
  /** 是否具有管理员角色（司机/员工可一人多角色） */
  adminRole?: boolean
  remark: string | null
  createdAt: string
  updatedAt: string
}

/** 车辆档案，字段与后端 Vehicle 一致 */
export interface Vehicle {
  id: number
  plateNo: string
  model: string | null
  /** 车辆种类：CRANE 吊车 / LIFT 举升车 / SEMI_TRAILER 板车（半挂）/ HEAVY_TRAILER 大件托盘车 */
  type: string
  /** 吊车吨位（吨），仅 CRANE 有值 */
  tonnage: number | null
  /** 举升高度（米），仅 LIFT 有值，常用 20/30 */
  liftHeight: number | null
  defaultStaffId: number | null
  /** 车架号 VIN */
  vin: string | null
  /** 车险购买日期（yyyy-MM-dd），保险到期 = 购买日 + 1 年 */
  insurancePurchaseDate: string | null
  /** 最近审车日期（yyyy-MM-dd） */
  inspectionDate: string | null
  /** 审车周期：HALF_YEAR 半年 / YEAR 一年 */
  inspectionCycle: string | null
  /** IDLE / BUSY / BUSY_THIRD / MAINTENANCE / DISABLED */
  status: string
  currentOrderId: number | null
  thirdPartyMarkedAt: string | null
  isAway: number | null
  remark: string | null
  createdAt: string
  updatedAt: string
}

export interface StaffUpsertRequest {
  name: string
  phone: string
  idCard?: string
  /** DRIVER / EMPLOYEE */
  position: string
  status?: string
  licenseType?: string
  specialCertNo?: string
  hireDate?: string
  /** 创建时是否顺带创建登录账号 */
  createAccount?: boolean
  /** 是否赋予管理员角色（一人多角色，登录后在小程序选择身份） */
  adminRole?: boolean
  /** 登录账号初始密码（仅创建账号时生效，选填，≥6 位） */
  initialPassword?: string
  remark?: string
}

export interface VehicleUpsertRequest {
  plateNo: string
  model?: string
  /** CRANE 吊车 / LIFT 举升车 / SEMI_TRAILER 板车（半挂）/ HEAVY_TRAILER 大件托盘车，缺省 CRANE */
  type?: string
  /** 选填 */
  tonnage?: number
  /** 选填，常用 20/30 米 */
  liftHeight?: number
  defaultStaffId?: number
  status?: string
  remark?: string
  /** 车架号 VIN（选填，第 10 位年款码用于推算车龄与审车周期） */
  vin?: string
  /** 车险购买日期 yyyy-MM-dd */
  insurancePurchaseDate?: string
  /** 最近审车日期 yyyy-MM-dd */
  inspectionDate?: string
  /** 审车周期 HALF_YEAR / YEAR，留空按 VIN 车龄自动带出 */
  inspectionCycle?: string
}

export function pageStaffs(query: { name?: string; phone?: string; status?: string; pageNum?: number; pageSize?: number }) {
  return request<PageData<Staff>>('/staff/staffs', { query })
}

export function createStaff(body: StaffUpsertRequest) {
  return request<Staff>('/staff/staffs', { method: 'POST', body })
}

export function updateStaff(id: number | string, body: StaffUpsertRequest) {
  return request<Staff>(`/staff/staffs/${id}`, { method: 'PUT', body })
}

export function deleteStaff(id: number | string) {
  return request<void>(`/staff/staffs/${id}`, { method: 'DELETE' })
}

export function pageVehicles(query: { plateNo?: string; status?: string; pageNum?: number; pageSize?: number }) {
  return request<PageData<Vehicle>>('/staff/vehicles', { query })
}

export function createVehicle(body: VehicleUpsertRequest) {
  return request<Vehicle>('/staff/vehicles', { method: 'POST', body })
}

export function updateVehicle(id: number | string, body: VehicleUpsertRequest) {
  return request<Vehicle>(`/staff/vehicles/${id}`, { method: 'PUT', body })
}

export function deleteVehicle(id: number | string) {
  return request<void>(`/staff/vehicles/${id}`, { method: 'DELETE' })
}

/** 管理员手动变更车辆状态：IDLE/BUSY_THIRD/MAINTENANCE/DISABLED（BUSY 由工单驱动） */
export function changeVehicleStatus(id: number | string, status: string) {
  return request<Vehicle>(`/staff/vehicles/${id}/status`, { method: 'PATCH', body: { status } })
}

/** 司机常用设备：本人为默认司机的车辆列表 */
export function listMyVehicles() {
  return request<Vehicle[]>('/staff/my-vehicles')
}

/** 司机变更常用设备状态：仅 IDLE/BUSY_THIRD/MAINTENANCE */
export function driverChangeVehicleStatus(id: number | string, status: string) {
  return request<Vehicle>(`/staff/vehicles/${id}/driver-status`, { method: 'POST', body: { status } })
}

/** 车辆规格展示文案：吊车「25吨」/ 举升车「举升20米」/ 板车 / 大件托盘车 */
export function vehicleSpecLabel(vehicle: Pick<Vehicle, 'type' | 'tonnage' | 'liftHeight'>): string {
  if (vehicle.type === 'LIFT') {
    return vehicle.liftHeight != null ? `举升${vehicle.liftHeight}米` : '举升车'
  }
  if (vehicle.type === 'SEMI_TRAILER' || vehicle.type === 'HEAVY_TRAILER') {
    return VEHICLE_TYPE_LABEL[vehicle.type] || vehicle.type
  }
  return vehicle.tonnage != null ? `${vehicle.tonnage}吨` : '吊车'
}

/** 车辆种类中文标签（空值按吊车） */
export function vehicleTypeLabel(type?: string | null): string {
  return VEHICLE_TYPE_LABEL[type || 'CRANE'] || '吊车'
}
