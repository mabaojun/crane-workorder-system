// 下单常用档位：管理端与客户端共用，保证日期、时间、吨位预设一致
export const TONNAGE_PRESETS = [8, 12, 16, 25, 50, 70, 80, 100, 110, 130, 150, 200, 260, 300]

export const DAYS_PRESETS = [0.5, 1, 1.5, 2, 3]

/** 常用施工开始时间（HH:mm） */
export const TIME_PRESETS = ['06:00', '06:30', '07:00', '07:30', '08:00', '12:30', '13:00', '13:30']

/** 日期快捷选项：今天 / 明天 / 后天 */
export const DATE_QUICKS = [
  { label: '今天', offset: 0 },
  { label: '明天', offset: 1 },
  { label: '后天', offset: 2 }
]

/** 按偏移天数生成 yyyy-MM-dd */
export function dateByOffset(offset: number): string {
  const d = new Date()
  d.setDate(d.getDate() + offset)
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}
