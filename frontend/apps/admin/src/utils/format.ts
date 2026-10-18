// 展示格式化工具：时间、图片字段解析
import dayjs from 'dayjs'

/** 统一时间显示：YYYY-MM-DD HH:mm */
export function fmtTime(val?: string | null): string {
  if (!val) return '-'
  const d = dayjs(val)
  return d.isValid() ? d.format('YYYY-MM-DD HH:mm') : '-'
}

/** 日期显示：YYYY-MM-DD */
export function fmtDate(val?: string | null): string {
  if (!val) return '-'
  const d = dayjs(val)
  return d.isValid() ? d.format('YYYY-MM-DD') : '-'
}

/** imageUrls 为 JSON 数组字符串，解析失败返回空数组 */
export function parseImageUrls(val?: string | null): string[] {
  if (!val) return []
  try {
    const arr = JSON.parse(val)
    return Array.isArray(arr) ? arr.filter((x) => typeof x === 'string') : []
  } catch {
    return []
  }
}

/** 提交用 ISO 时间：yyyy-MM-ddTHH:mm:ss */
export function isoDateTime(val?: string | null): string | undefined {
  if (!val) return undefined
  const d = dayjs(val)
  return d.isValid() ? d.format('YYYY-MM-DDTHH:mm:ss') : undefined
}
