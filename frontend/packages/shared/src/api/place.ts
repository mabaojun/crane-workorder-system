// 常用施工地点域接口封装（管理端增删 + 移动端/管理端读取）
import { request } from './client'

/** 常用施工地点，字段与后端 CommonPlace 一致 */
export interface CommonPlace {
  id: number
  placeName: string
  /** 经度（GCJ-02），可空 */
  longitude: number | null
  /** 纬度（GCJ-02），可空 */
  latitude: number | null
  sortOrder: number
  createdAt: string
  updatedAt: string
}

export interface CommonPlaceUpsertRequest {
  placeName: string
  longitude?: number
  latitude?: number
  sortOrder?: number
}

/** 全量地点列表（keyword 可选模糊过滤），按 sort_order 降序 */
export function listCommonPlaces(keyword?: string) {
  return request<CommonPlace[]>('/place/common-places', {
    method: 'GET',
    query: keyword ? { keyword } : undefined,
  })
}

/** 客户端常用地点：本客户历史工单中使用过的施工地点（含经纬度，最近使用在前） */
export interface MyPlace {
  placeName: string
  longitude: number | null
  latitude: number | null
}

/** 我的常用地点（客户身份调用，仅含本客户提交过的地点） */
export function listMyPlaces() {
  return request<MyPlace[]>('/place/my-places')
}

/** 新增地点（仅管理员） */
export function createCommonPlace(data: CommonPlaceUpsertRequest) {
  return request<CommonPlace>('/place/common-places', {
    method: 'POST',
    body: data,
  })
}

/** 删除地点（仅管理员） */
export function deleteCommonPlace(id: number) {
  return request<void>(`/place/common-places/${id}`, {
    method: 'DELETE',
  })
}
