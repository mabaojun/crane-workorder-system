// 系统名称等公开配置的获取与缓存（支持后台修改，全端动态展示）
import { request } from './client'

export interface SystemBranding {
  'system.name': string
  'system.short_name': string
  'system.company_name': string
  'system.copyright': string
  'system.logo_text': string
  [key: string]: string
}

const FALLBACK: SystemBranding = {
  'system.name': '吊装业务管理系统',
  'system.short_name': '吊装系统',
  'system.company_name': '',
  'system.copyright': '',
  'system.logo_text': '吊装'
}

let cache: SystemBranding | null = null

export async function loadBranding(force = false): Promise<SystemBranding> {
  if (cache && !force) return cache
  try {
    cache = await request<SystemBranding>('/system/public-config')
  } catch {
    cache = FALLBACK
  }
  return cache
}

export function getBranding(): SystemBranding {
  return cache ?? FALLBACK
}

export function systemName(): string {
  return getBranding()['system.name'] ?? FALLBACK['system.name']
}
