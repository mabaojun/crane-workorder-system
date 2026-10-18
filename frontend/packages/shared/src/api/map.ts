// 地图域接口封装（API-SYS 扩展：高德代理逆地理编码 / POI 关键词搜索 / 前端渲染密钥读取）
// 契约：GET /system/amap/regeo、GET /system/amap/place/text（登录态）；mock 模式后端返回模拟数据
import { request } from './client'

/** 逆地理编码结果（结构化地址） */
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

/** POI 关键词搜索结果项 */
export interface PoiItem {
  name: string
  address: string
  lng: number
  lat: number
  province: string
  city: string
  district: string
}

/** 地图选点确认结果（供表单回填） */
export interface AmapPickResult {
  lng: number
  lat: number
  address: string
  structured?: {
    province: string
    city: string
    district: string
    township: string
    adcode: string
  }
}

/** 逆地理编码：经纬度反查结构化地址 */
export function amapRegeo(lng: number | string, lat: number | string) {
  return request<RegeoResult>('/system/amap/regeo', { query: { lng, lat } })
}

/** POI 关键词搜索：keywords 必填，lng/lat 可选（用于按位置加权） */
export function amapPlaceText(keywords: string, lng?: number | string, lat?: number | string) {
  return request<PoiItem[]>('/system/amap/place/text', { query: { keywords, lng, lat } })
}

/** 前端地图渲染所需的公开配置（经 GET /system/public-config 下发） */
export interface AmapPublicKeys {
  jsapiKey: string
  securityJscode: string
  mock: boolean
}

/** 从公开配置读取高德 JSAPI Key / 安全密钥 / mock 开关（键缺失视为未配置） */
export async function getAmapPublicKeys(): Promise<AmapPublicKeys> {
  const cfg = await request<Record<string, string>>('/system/public-config')
  return {
    jsapiKey: cfg['map.amap.jsapi_key'] || '',
    securityJscode: cfg['map.amap.security_jscode'] || '',
    mock: cfg['map.mock'] === 'true'
  }
}
