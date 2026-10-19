// 统一接口客户端（01 §4.2 共享层）：baseURL、鉴权头、统一信封解包、错误提示、幂等键
// 契约见 docs/前置设计/03-接口设计规范（API 契约）.md §1.4

import type { ApiEnvelope } from '../index'

export type ClientType = 'admin' | 'h5' | 'mp' | 'screen'

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  query?: Record<string, string | number | boolean | undefined | null>
  body?: unknown
  /** 写接口幂等键，缺省自动生成 */
  idempotencyKey?: string
  headers?: Record<string, string>
}

export class ApiError extends Error {
  code: number
  traceId: string

  constructor(code: number, message: string, traceId: string) {
    super(message)
    this.code = code
    this.traceId = traceId
  }
}

/** 令牌与存储适配：admin/screen 用 localStorage，小程序用 uni storage，由各端注入 */
export interface TokenAdapter {
  get(): string | null
  set(token: string): void
  clear(): void
}

/**
 * 环境安全令牌存储：优先 localStorage（后台 / 大屏 / H5），
 * 无 localStorage 的运行环境（如小程序未注入适配时）退化为内存存储，避免启动即抛错。
 */
const memoryToken: { value: string | null } = { value: null }

function browserStorage(): Storage | null {
  try {
    return typeof localStorage === 'undefined' ? null : localStorage
  } catch {
    return null
  }
}

export const localStorageAdapter: TokenAdapter = {
  get: () => {
    const storage = browserStorage()
    if (!storage) return memoryToken.value
    try {
      return storage.getItem('crane_token')
    } catch {
      return memoryToken.value
    }
  },
  set: (t) => {
    memoryToken.value = t
    const storage = browserStorage()
    try {
      storage?.setItem('crane_token', t)
    } catch {
      // 隐私模式等存储受限场景仅保留内存令牌
    }
  },
  clear: () => {
    memoryToken.value = null
    const storage = browserStorage()
    try {
      storage?.removeItem('crane_token')
    } catch {
      // 忽略存储清理异常
    }
  }
}

let tokenAdapter: TokenAdapter = localStorageAdapter
let clientType: ClientType = 'admin'
let baseURL = '/api/v1'
let onUnauthorized: (() => void) | null = null

// ---- 传输层适配：浏览器端默认 fetch；小程序运行环境无 fetch，由各端注入 uni.request 适配 ----

export interface RequestAdapterInput {
  url: string
  method: string
  headers: Record<string, string>
  body?: string
  timeoutMs: number
}

export interface RequestAdapterResult {
  status: number
  data: unknown
}

export type RequestAdapter = (input: RequestAdapterInput) => Promise<RequestAdapterResult>

/** 默认浏览器传输：fetch + AbortController 超时，超时/断网给出友好错误 */
const fetchAdapter: RequestAdapter = async ({ url, method, headers, body, timeoutMs }) => {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), timeoutMs)
  try {
    const res = await fetch(url, { method, headers, body, signal: controller.signal })
    return { status: res.status, data: await res.json() }
  } catch (e) {
    if (controller.signal.aborted) {
      throw new ApiError(50008, '请求超时，请检查网络后重试', '')
    }
    throw new ApiError(-1, '网络连接失败，请检查网络后重试', '')
  } finally {
    clearTimeout(timer)
  }
}

let requestAdapter: RequestAdapter = fetchAdapter

export function configureClient(opts: {
  tokenAdapter?: TokenAdapter
  clientType?: ClientType
  baseURL?: string
  requestAdapter?: RequestAdapter
  onUnauthorized?: () => void
}) {
  if (opts.tokenAdapter) tokenAdapter = opts.tokenAdapter
  if (opts.clientType) clientType = opts.clientType
  if (opts.baseURL !== undefined) baseURL = opts.baseURL
  if (opts.requestAdapter) requestAdapter = opts.requestAdapter
  if (opts.onUnauthorized !== undefined) onUnauthorized = opts.onUnauthorized
}

export function getToken(): string | null {
  return tokenAdapter.get()
}

/** 当前端别（admin/h5/mp/screen），附件上传等原生请求需附带 */
export function getClientType(): ClientType {
  return clientType
}

/** 当前接口根路径（/api/v1），附件上传拼接完整地址用 */
export function getBaseURL(): string {
  return baseURL
}

function buildQuery(query?: RequestOptions['query']): string {
  if (!query) return ''
  const parts: string[] = []
  for (const [k, v] of Object.entries(query)) {
    if (v === undefined || v === null || v === '') continue
    parts.push(`${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`)
  }
  return parts.length ? `?${parts.join('&')}` : ''
}

function uuid(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) return crypto.randomUUID()
  return `id-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`
}

/** 默认请求超时（毫秒）：超时或网络失败时抛出友好错误，避免页面长时间空等 */
export const DEFAULT_TIMEOUT_MS = 15000

/** 统一请求：解包信封，code!=0 抛 ApiError；10001/10002/10004 触发未登录回调；超时/网络异常给出友好提示 */
export async function request<T>(path: string, opts: RequestOptions = {}): Promise<T> {
  const method = opts.method ?? 'GET'
  const headers: Record<string, string> = {
    'X-Client': clientType,
    ...(opts.headers ?? {})
  }
  const token = tokenAdapter.get()
  // 调用方显式指定 Authorization（如绑定手机号的 Bind 凭据）时优先，不被本地令牌覆盖
  if (token && headers.Authorization === undefined) headers.Authorization = `Bearer ${token}`
  if (opts.body !== undefined) headers['Content-Type'] = 'application/json'
  if (method !== 'GET') headers['Idempotency-Key'] = opts.idempotencyKey ?? uuid()

  const res = await requestAdapter({
    url: `${baseURL}${path}${buildQuery(opts.query)}`,
    method,
    headers,
    body: opts.body !== undefined ? JSON.stringify(opts.body) : undefined,
    timeoutMs: DEFAULT_TIMEOUT_MS
  })
  const json = res.data as ApiEnvelope<T>
  if (json.code !== 0) {
    if ([10001, 10002, 10004].includes(json.code) && onUnauthorized) onUnauthorized()
    throw new ApiError(json.code, json.message, json.traceId)
  }
  return json.data
}

export async function download(path: string, opts: RequestOptions = {}): Promise<Response> {
  const headers: Record<string, string> = {
    'X-Client': clientType,
    ...(opts.headers ?? {})
  }
  const token = tokenAdapter.get()
  if (token) headers.Authorization = `Bearer ${token}`
  return fetch(`${baseURL}${path}${buildQuery(opts.query)}`, {
    method: opts.method ?? 'GET',
    headers
  })
}
