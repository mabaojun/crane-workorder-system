// 客户档案与联系人域接口封装（管理端专用）
import { request } from './client'
import type { PageData } from '../index'

/** 客户档案，字段与后端 Customer 一致 */
export interface Customer {
  id: number
  customerNo: string
  name: string
  /** COMPANY / PERSON */
  type: string
  creditCode: string | null
  industry: string | null
  address: string | null
  phone: string
  /** PENDING / APPROVED / REJECTED */
  auditStatus: string
  /** ACTIVE / DISABLED */
  status: string
  /** 绑定登录账号 sys_user.id，null 表示未创建 */
  userId: number | null
  remark: string | null
  createdAt: string
  updatedAt: string
}

export interface CustomerContact {
  id: number
  customerId: number
  name: string
  gender: string | null
  position: string | null
  phone: string
  wechat: string | null
  email: string | null
  /** 1 默认 / 0 普通 */
  isDefault: number
  remark: string | null
  createdAt: string
  updatedAt: string
}

export interface CustomerUpsertRequest {
  name: string
  /** COMPANY / PERSON */
  type: string
  creditCode?: string
  industry?: string
  address?: string
  phone: string
  remark?: string
  /** 创建时是否顺带创建登录账号 */
  createAccount?: boolean
}

export interface ContactUpsertRequest {
  customerId: number
  name: string
  phone: string
  gender?: string
  position?: string
  wechat?: string
  email?: string
  isDefault?: number
  remark?: string
}

export function pageCustomers(query: { name?: string; phone?: string; status?: string; pageNum?: number; pageSize?: number }) {
  return request<PageData<Customer>>('/customer/customers', { query })
}

export function createCustomer(body: CustomerUpsertRequest) {
  return request<Customer>('/customer/customers', { method: 'POST', body })
}

export function updateCustomer(id: number | string, body: CustomerUpsertRequest) {
  return request<Customer>(`/customer/customers/${id}`, { method: 'PUT', body })
}

export function deleteCustomer(id: number | string) {
  return request<void>(`/customer/customers/${id}`, { method: 'DELETE' })
}

export function listContacts(customerId: number | string) {
  return request<CustomerContact[]>(`/customer/customers/${customerId}/contacts`)
}

export function createContact(body: ContactUpsertRequest) {
  return request<CustomerContact>('/customer/contacts', { method: 'POST', body })
}

export function updateContact(id: number | string, body: ContactUpsertRequest) {
  return request<CustomerContact>(`/customer/contacts/${id}`, { method: 'PUT', body })
}

export function deleteContact(id: number | string) {
  return request<void>(`/customer/contacts/${id}`, { method: 'DELETE' })
}
