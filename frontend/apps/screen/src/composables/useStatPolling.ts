// 刷新调度（06 §8）：按频率分组轮询、同一指标单在途请求、失败指数退避（1/2/4/8/16s 上限 30s）、
// 失败恢复后立即全量刷新一次；页面隐藏暂停、恢复可见立即刷新；组件卸载清理定时器
import { onMounted, onUnmounted } from 'vue'
import {
  fetchStatAlarms,
  fetchStatDashboard,
  fetchStatVehicleMap,
  fetchStaffPerformance
} from '@crane/shared/api/stat'
import {
  markError,
  setAlarms,
  setDashboard,
  setStaffPerformance,
  setVehicleMap
} from '../store/stat'

interface Metric {
  key: 'dashboard' | 'alarms' | 'map' | 'performance'
  interval: number
  fetch: () => Promise<void>
  timer: number | null
  inFlight: boolean
  failCount: number
}

const RETRY_STEPS = [1000, 2000, 4000, 8000, 16000]
const RETRY_CAP = 30000

let paused = false
let metrics: Metric[] = []

function backoffDelay(failCount: number): number {
  const step = RETRY_STEPS[Math.min(failCount - 1, RETRY_STEPS.length - 1)] ?? RETRY_CAP
  return Math.min(step, RETRY_CAP)
}

function schedule(m: Metric, delay?: number) {
  if (m.timer !== null) {
    clearTimeout(m.timer)
    m.timer = null
  }
  if (paused) return
  const d = delay ?? (m.failCount > 0 ? backoffDelay(m.failCount) : m.interval)
  m.timer = window.setTimeout(() => {
    m.timer = null
    exec(m)
  }, d)
}

async function exec(m: Metric) {
  if (m.inFlight) return
  m.inFlight = true
  const hadFailed = m.failCount > 0
  try {
    await m.fetch()
    m.failCount = 0
    // 失败恢复后立即全量刷新一次，避免停留在过期数据（06 §8.2）
    if (hadFailed) fullRefresh(m)
  } catch {
    m.failCount++
    markError(m.key)
  } finally {
    m.inFlight = false
  }
  schedule(m)
}

function fullRefresh(trigger: Metric) {
  for (const m of metrics) {
    if (m !== trigger) exec(m)
  }
}

function pauseAll() {
  paused = true
  for (const m of metrics) {
    if (m.timer !== null) {
      clearTimeout(m.timer)
      m.timer = null
    }
  }
}

function resumeAll() {
  paused = false
  for (const m of metrics) exec(m)
}

function onVisibilityChange() {
  if (document.hidden) pauseAll()
  else resumeAll()
}

/** 在驾驶舱组件 setup 中调用一次；随组件卸载自动清理 */
export function useStatPolling() {
  onMounted(() => {
    paused = false
    metrics = [
      {
        key: 'alarms',
        interval: 5000,
        timer: null,
        inFlight: false,
        failCount: 0,
        fetch: async () => setAlarms(await fetchStatAlarms())
      },
      {
        key: 'map',
        interval: 15000,
        timer: null,
        inFlight: false,
        failCount: 0,
        fetch: async () => setVehicleMap(await fetchStatVehicleMap())
      },
      {
        key: 'dashboard',
        interval: 60000,
        timer: null,
        inFlight: false,
        failCount: 0,
        fetch: async () => setDashboard(await fetchStatDashboard())
      },
      {
        key: 'performance',
        interval: 24 * 60 * 60 * 1000,
        timer: null,
        inFlight: false,
        failCount: 0,
        fetch: async () => setStaffPerformance(await fetchStaffPerformance('MONTH'))
      }
    ]
    document.addEventListener('visibilitychange', onVisibilityChange)
    for (const m of metrics) exec(m)
  })

  onUnmounted(() => {
    document.removeEventListener('visibilitychange', onVisibilityChange)
    for (const m of metrics) {
      if (m.timer !== null) clearTimeout(m.timer)
    }
    metrics = []
  })
}
