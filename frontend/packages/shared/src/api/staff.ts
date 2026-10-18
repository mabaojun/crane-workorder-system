// 员工与车辆档案域接口封装（管理端专用）
import { request } from './client'
import type { PageData } from '../index'

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
  remark: string | null
  createdAt: string
  updatedAt: string
}

/** 车辆档案，字段与后端 Vehicle 一致 */
export interface Vehicle {
  id: number
  plateNo: string
  model: string | null
  /** 吊车吨位（吨） */
  tonnage: number
  defaultStaffId: number | null
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
  remark?: string
}

export interface VehicleUpsertRequest {
  plateNo: string
  model?: string
  tonnage: number
  defaultStaffId?: number
  status?: string
  remark?: string
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
