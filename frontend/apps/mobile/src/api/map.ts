// 地图域接口：高德逆地理编码 / 关键字搜索（均为登录态请求，走统一客户端）
import { request } from '@crane/shared/api/client'

/** 逆地理编码结果（GET /system/amap/regeo） */
export interface RegeoResult {
  formattedAddress: string
  province: string
  city: string
  district: string
  township: string
  adcode: string
  lng: number
  lat: number
}

/** 关键字搜索结果项（GET /system/amap/place/text） */
export interface PoiItem {
  name: string
  address: string
  lng: number
  lat: number
  province?: string
  city?: string
  district?: string
}

/** 坐标反查结构化地址 */
export function regeo(lng: number | string, lat: number | string) {
  return request<RegeoResult>('/system/amap/regeo', { query: { lng, lat } })
}

/** 关键字搜索兴趣点（可携带当前坐标做附近优先） */
export function placeText(keywords: string, lng?: number, lat?: number) {
  return request<PoiItem[]>('/system/amap/place/text', { query: { keywords, lng, lat } })
}
