// 认证域接口：验证码 / 短信直登 / 微信授权登录与手机号绑定 / 当前用户
import { request } from '@crane/shared/api/client'
import type { MeInfo, RoleOption } from '../types'

export interface LoginResult {
  tokenType: string
  accessToken: string
  expiresIn: number
  role: string
  /** 账号可选身份（一人多角色时登录后选择） */
  availableRoles?: RoleOption[]
  permissions?: string[]
  bound: boolean
  /** 微信登录未绑定手机号时返回的短时效绑定凭据 */
  bindTicket?: string
}

export interface SmsCodeResult {
  expiresIn: number
  interval: number
  /** 仅后端 MOCK 短信通道返回；生产不带此字段 */
  debugCode?: string
}

/** 验证码场景：LOGIN 登录 / BIND_PHONE 微信授权后绑定手机号 */
export type SmsScene = 'LOGIN' | 'BIND_PHONE'

export function sendSmsCode(phone: string, scene: SmsScene = 'LOGIN') {
  return request<SmsCodeResult>('/auth/sms-code', {
    method: 'POST',
    body: { phone, scene }
  })
}

export function smsLogin(phone: string, smsCode: string) {
  return request<LoginResult>('/auth/sms-login', {
    method: 'POST',
    body: { phone, smsCode }
  })
}

/** 静态密码登录（30015=管理员已关闭密码登录，由调用方 toast 后端 message） */
export function passwordLogin(username: string, password: string) {
  return request<LoginResult>('/auth/password-login', {
    method: 'POST',
    body: { username, password }
  })
}

export function fetchMe() {
  return request<MeInfo>('/auth/me')
}

/** 多角色账号选择本次进入身份，返回使用新角色签发的令牌 */
export function selectRole(role: string) {
  return request<LoginResult>('/auth/select-role', {
    method: 'POST',
    body: { role }
  })
}

export function logout() {
  return request<void>('/auth/logout', { method: 'POST' })
}

/**
 * 微信授权登录（仅小程序通道调用）：前端先用 uni.login 取 code。
 * 已绑定账号直接返回令牌；未绑定返回 bindTicket，需继续调用 bindPhone 绑定预留手机号。
 */
export function wechatLogin(code: string, client = 'mp') {
  return request<LoginResult>('/auth/wechat-login', {
    method: 'POST',
    body: { code, client }
  })
}

/** 绑定手机号：携带 bindTicket 时走微信绑定通道（Authorization: Bind <ticket>） */
export function bindPhone(phone: string, smsCode: string, bindTicket?: string) {
  return request<LoginResult>('/auth/bind-phone', {
    method: 'POST',
    body: { phone, smsCode },
    headers: bindTicket ? { Authorization: `Bind ${bindTicket}` } : undefined
  })
}
