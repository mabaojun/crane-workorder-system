// 工作台状态看板公共格式化：状态文案、持续时长、当日时间轴定位
import dayjs from 'dayjs'

export const STAFF_STATUS_LABEL: Record<string, string> = {
  IDLE: '空闲',
  BUSY: '作业中',
  AWAY: '外出',
  RESIGNED: '离职'
}

export const STAFF_POSITION_LABEL: Record<string, string> = {
  DRIVER: '司机',
  EMPLOYEE: '员工'
}

export const VEHICLE_STATUS_LABEL: Record<string, string> = {
  IDLE: '空闲',
  BUSY: '作业中',
  BUSY_THIRD: '第三方派单',
  MAINTENANCE: '维保',
  DISABLED: '停用'
}

export const ORDER_STATUS_COLOR: Record<string, string> = {
  PENDING_ACCEPT: '#909399',
  PENDING_ARRIVE: '#409eff',
  IN_PROGRESS: '#67c23a',
  SUSPENDED: '#e6a23c',
  COMPLETED: '#2f9e8f',
  CANCELLED: '#c0c4cc'
}

/** 分钟转「x小时y分」，空值显示 — */
export function fmtDuration(minutes?: number | null): string {
  if (minutes == null) return '—'
  if (minutes < 60) return `${minutes} 分钟`
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  return m ? `${h} 小时 ${m} 分` : `${h} 小时`
}

/** HH:mm */
export function fmtClock(value?: string | null): string {
  if (!value) return '--:--'
  const d = dayjs(value)
  return d.isValid() ? d.format('HH:mm') : '--:--'
}

/** 时间区间文案：计划/开工时间 → 完工、已取消或当前状态 */
export function fmtRange(order: {
  status?: string
  startedAt?: string | null
  expectedStartAt?: string | null
  completedAt?: string | null
}): string {
  const start = fmtClock(order.startedAt || order.expectedStartAt)
  let end = '进行中'
  if (order.completedAt) {
    end = fmtClock(order.completedAt)
  } else if (order.status === 'CANCELLED') {
    end = '已取消'
  } else if (order.status === 'PENDING_ACCEPT' || order.status === 'PENDING_ARRIVE') {
    end = '待开工'
  }
  return `${start} → ${end}`
}

/** 持续时长文案：未开工显示待开工，开工后显示实际时长 */
export function fmtOrderDuration(order: { durationMinutes?: number | null }): string {
  return order.durationMinutes != null ? fmtDuration(order.durationMinutes) : '待开工'
}

/** 当前任务时长标签：持续 x 小时，未开工则待开工 */
export function fmtDurationLabel(order: { durationMinutes?: number | null }): string {
  return order.durationMinutes != null ? `持续 ${fmtDuration(order.durationMinutes)}` : '待开工'
}

export function todayStr(): string {
  return dayjs().format('YYYY-MM-DD')
}

export function shiftDate(date: string, days: number): string {
  return dayjs(date).add(days, 'day').format('YYYY-MM-DD')
}

/** 日期亲和文案：今天 / 明天 / 后天 / 周几 */
export function dateHint(date: string): string {
  const diff = dayjs(date).startOf('day').diff(dayjs().startOf('day'), 'day')
  if (diff === 0) return '今天'
  if (diff === 1) return '明天'
  if (diff === 2) return '后天'
  if (diff === -1) return '昨天'
  return weekdayText(date)
}

export function weekdayText(date: string): string {
  return `周${'日一二三四五六'[dayjs(date).day()]}`
}

/** 工单在所选日期的 24 小时时间轴位置（百分比） */
export interface TimelineRect {
  left: number
  width: number
  pending: boolean
}

export function timelineRect(
  order: {
    status?: string
    startedAt?: string | null
    expectedStartAt?: string | null
    completedAt?: string | null
    cancelledAt?: string | null
  },
  date: string
): TimelineRect | null {
  const source = order.startedAt || order.expectedStartAt
  if (!source) return null
  const dayStart = dayjs(date).startOf('day')
  const dayEnd = dayStart.add(1, 'day')
  const start = dayjs(source)
  if (!start.isValid()) return null
  let end = order.completedAt
    ? dayjs(order.completedAt)
    : order.status === 'CANCELLED' && order.cancelledAt
      ? dayjs(order.cancelledAt)
      : dayjs()
  if (end.isBefore(start)) end = start.add(30, 'minute')
  const clampedStart = start.isBefore(dayStart) ? dayStart : start
  const clampedEnd = end.isAfter(dayEnd) ? dayEnd : end
  if (clampedEnd.isBefore(dayStart) || clampedStart.isAfter(dayEnd)) return null
  const left = (clampedStart.diff(dayStart, 'minute') / 1440) * 100
  const width = Math.max((clampedEnd.diff(clampedStart, 'minute') / 1440) * 100, 0.7)
  // 未开工的工单只画计划起点标记，不臆造持续时间
  const pending = !order.startedAt && !order.completedAt
  return { left, width, pending }
}
