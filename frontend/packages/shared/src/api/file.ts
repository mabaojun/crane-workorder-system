// 附件域接口封装：上传、鉴权下载、工单图片聚合
import { getBaseURL, getClientType, getToken, request } from './client'

/** 上传成功返回：url 为鉴权下载地址，fileName 为原始文件名 */
export interface UploadedFile {
  id: number
  fileName: string
  url: string
  size: number
  contentType: string | null
}

/** 工单图片聚合项 */
export interface OrderPhoto {
  /** DEMAND / CHECK_IN / DAILY_CHECKOUT / TASK_END / EXCEPTION */
  stage: string
  stageLabel: string
  fileName: string
  url: string
  time: string | null
}

/** 浏览器端上传附件（管理后台） */
export async function uploadAttachment(file: Blob, fileName: string): Promise<UploadedFile> {
  const form = new FormData()
  form.append('file', file, fileName)
  const headers: Record<string, string> = { 'X-Client': getClientType() }
  const token = getToken()
  if (token) headers.Authorization = `Bearer ${token}`
  const res = await fetch(`${getBaseURL()}/files/upload`, { method: 'POST', headers, body: form })
  const json = await res.json()
  if (json.code !== 0) throw new Error(json.message || '上传失败')
  return json.data as UploadedFile
}

/** 工单图片聚合（管理员/该单司机/所属客户可查） */
export function orderPhotos(orderId: number | string) {
  return request<OrderPhoto[]>(`/files/order/${orderId}`)
}

/** 从下载地址还原文件名（兼容旧占位路径） */
export function fileNameFromUrl(url: string | null | undefined): string {
  if (!url) return ''
  const path = url.split('?')[0]
  const name = path.substring(path.lastIndexOf('/') + 1)
  try {
    return decodeURIComponent(name)
  } catch {
    return name
  }
}

/** 是否为浏览器可直接预览的图片附件 */
export function isImageAttachment(url: string, fileName?: string): boolean {
  const name = fileName || fileNameFromUrl(url)
  const ext = name.split('.').pop()?.toLowerCase() || ''
  return ['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp', 'svg', 'avif'].includes(ext)
}

/** 鉴权拉取附件生成 Blob URL，调用方使用后需自行 revoke */
async function fetchAttachmentObjectUrl(url: string): Promise<string> {
  const headers: Record<string, string> = { 'X-Client': getClientType() }
  const token = getToken()
  if (token) headers.Authorization = `Bearer ${token}`
  const res = await fetch(url, { headers })
  if (!res.ok) throw new Error(`附件加载失败（HTTP ${res.status}）`)
  const blob = await res.blob()
  return URL.createObjectURL(blob)
}

/** 鉴权拉取图片附件，返回可用于弹窗预览的 Blob URL（使用后需 revoke） */
export function loadImagePreviewUrl(url: string): Promise<string> {
  return fetchAttachmentObjectUrl(url)
}

/**
 * 鉴权下载附件：先确认再以 Blob 拉取，触发浏览器保存（避免内联预览浪费流量）。
 * 返回 false 表示用户取消。
 */
export async function downloadAttachment(url: string, fileName?: string): Promise<boolean> {
  const objectUrl = await fetchAttachmentObjectUrl(url)
  const anchor = document.createElement('a')
  anchor.href = objectUrl
  anchor.download = fileName || fileNameFromUrl(url) || 'attachment'
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  setTimeout(() => URL.revokeObjectURL(objectUrl), 1000)
  return true
}
