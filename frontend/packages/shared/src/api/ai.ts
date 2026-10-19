// AI 报告域接口封装（管理端工作台 / 大屏共用，均需管理员登录）
import { request } from './client'

/** AI 报告实体，字段与后端 AiDtos.ReportVO 一致 */
export interface AiReport {
  id: number
  briefingNo: string
  /** DAILY 日报 / WEEKLY 周报 / MONTHLY 月报 */
  type: string
  periodStart: string | null
  periodEnd: string | null
  /** 报告正文 Markdown */
  contentMarkdown: string
  /** SUCCESS 成功 / FAILED 失败 / SKIPPED 跳过 */
  callStatus: string
  failReason: string | null
  generatedAt: string
  expiresAt: string | null
}

export interface AiLatestReport {
  latest: AiReport | null
}

export interface AiGenerateResult {
  report: AiReport
  /** 生成时的输入快照（业务数据摘要），供核对参数传递 */
  promptDigest: string
}

export interface AiDiagnose {
  enabled: boolean
  mock: boolean
  baseUrlConfigured: boolean
  apiKeyConfigured: boolean
  modelConfigured: boolean
  model: string
  ok: boolean
  latencyMs: number
  reason: string
  /** 近 5 次生成状态序列，观察响应稳定性 */
  recentStatuses: string[]
}

/** 一句话下工单解析结果 */
export interface VoiceOrderParseResult {
  rawText: string
  /** CREATE_ORDER 新建工单 / DEMAND_CONVERT 需求转工单 */
  intent: string
  /** LLM 大模型 / RULE 本地规则 */
  parseMode: string
  demandId: number | null
  demandNo: string | null
  customerId: number | null
  customerName: string | null
  staffId: number | null
  staffName: string | null
  vehicleId: number | null
  plateNo: string | null
  address: string | null
  longitude: number | null
  latitude: number | null
  planStartAt: string | null
  craneTonnage: number | null
  cargoWeight: number | null
  cargoName: string | null
  remark: string | null
  missingFields: string[]
  warnings: string[]
}

/** 手动生成今日 AI 日报（失败时后端落 FAILED 记录并抛出原因） */
export function generateAiReport() {
  return request<AiGenerateResult>('/ai/reports/generate', { method: 'POST' })
}

/** 最近一条 AI 报告（latest 为 null 表示尚未生成过） */
export function getLatestAiReport() {
  return request<AiLatestReport>('/ai/reports/latest')
}

/** AI 连通性诊断（接口校验报告数据源） */
export function diagnoseAi() {
  return request<AiDiagnose>('/ai/diagnose')
}

/** 一句话下工单：自然语言拆解并与客户/司机/车辆/需求单匹配 */
export function parseOrderByText(text: string) {
  return request<VoiceOrderParseResult>('/ai/parse-order', {
    method: 'POST',
    body: { text }
  })
}
