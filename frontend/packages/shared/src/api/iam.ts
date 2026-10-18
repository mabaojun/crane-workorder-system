// 账号（iam）与系统配置 / 字典域接口封装（API-SYS / API-IAM）
import { request } from './client'
import type { PageData } from '../index'

/** 账号实体，password 不出参 */
export interface SysUser {
  id: number
  phone: string
  nickName: string | null
  avatarUrl: string | null
  /** 固定映射：1=ADMIN、2=DRIVER、3=CUSTOMER */
  roleId: number
  staffId: number | null
  customerId: number | null
  /** PENDING_BIND / ACTIVE / FROZEN / DISABLED */
  status: string
  boundAt: string | null
  lastLoginAt: string | null
  forceLogoutAt: string | null
  createdAt: string
  updatedAt: string
}

export interface SysConfig {
  id: number
  configKey: string
  configValue: string
  valueType: string
  groupName: string
  isEncrypted: number
  description: string | null
  updatedAt: string
}

export interface SysDict {
  id: number
  dictType: string
  dictCode: string
  dictLabel: string
  sort: number
  isEnabled: number
  remark: string | null
}

export function pageUsers(query: { roleCode?: string; pageNum?: number; pageSize?: number }) {
  return request<PageData<SysUser>>('/iam/users', { query })
}

/** 重置账号密码（≥8 位由前端校验） */
export function resetUserPassword(id: number | string, password: string) {
  return request<void>(`/iam/users/${id}/password`, { method: 'PUT', body: { password } })
}

/** 变更账号状态：ACTIVE / FROZEN / DISABLED */
export function updateUserStatus(id: number | string, status: string) {
  return request<void>(`/iam/users/${id}/status`, { method: 'PUT', body: { status } })
}

/** 配置项查询，group 缺省返回全部 */
export function listConfigs(group?: string) {
  return request<SysConfig[]>('/system/configs', { query: { group } })
}

/** 更新配置项（写穿透缓存，system.name 修改后全端生效） */
export function updateConfig(key: string, configValue: string) {
  return request<void>(`/system/configs/${key}`, { method: 'PUT', body: { configValue } })
}

/** 字典查询，dictType 缺省返回全部 */
export function listDictionaries(dictType?: string) {
  return request<SysDict[]>('/system/dictionaries', { query: { dictType } })
}
