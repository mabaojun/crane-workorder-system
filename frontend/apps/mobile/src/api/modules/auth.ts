// 认证域接口：验证码 / 短信直登 / 当前用户 / 微信登录占位
import { request } from '@crane/shared/api/client'
import type { MeInfo } from '../types'

export interface LoginResult {
  tokenType: string
  accessToken: string
  expiresIn: number
  role: string
  permissions?: string[]
  bound: boolean
}

export interface SmsCodeResult {
  expiresIn: number
  interval: number
  /** 仅后端 MOCK 短信通道返回；生产不带此字段 */
  debugCode?: string
}

export function sendSmsCode(phone: string) {
  return request<SmsCodeResult>('/auth/sms-code', {
    method: 'POST',
    body: { phone, scene: 'LOGIN' }
  })
}

export function smsLogin(phone: string, smsCode: string) {
  return request<LoginResult>('/auth/sms-login', {
    method: 'POST',
    body: { phone, smsCode }
  })
}

export function fetchMe() {
  return request<MeInfo>('/auth/me')
}

export function logout() {
  return request<void>('/auth/logout', { method: 'POST' })
}

/**
 * 微信授权登录（仅小程序通道调用）。
 * TODO(二期): 调 uni.login 拿 code，POST /auth/wechat-login { code, phone?, smsCode? }，
 * 未绑定手机号时按 03 契约走绑定流程。MVP 后端未配置微信通道返回 10006。
 */
export function wechatLogin(code: string) {
  return request<LoginResult>('/auth/wechat-login', {
    method: 'POST',
    body: { code }
  })
}
