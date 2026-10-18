// 大屏展示状态 Store：轻量 reactive，不引 pinia（06 §2.2）
// 数据失败时保留上次快照不清空（06 §8.3 降级），错误位仅用于展示陈旧/异常提示
import { reactive } from 'vue'
import type {
  StaffPerformanceItem,
  StatAlarmItem,
  StatDashboard,
  StatVehicleMap
} from '@crane/shared/api/stat'

export type MetricKey = 'dashboard' | 'alarms' | 'map' | 'performance'

export const statStore = reactive({
  dashboard: null as StatDashboard | null,
  alarms: { count: 0, list: [] as StatAlarmItem[] },
  vehicleMap: null as StatVehicleMap | null,
  staffPerformance: [] as StaffPerformanceItem[],
  /** 最近一次任一接口成功取数时刻（页头「数据更新于」） */
  lastUpdateAt: 0,
  errors: {
    dashboard: false,
    alarms: false,
    map: false,
    performance: false
  }
})

function touch() {
  statStore.lastUpdateAt = Date.now()
}

export function setDashboard(d: StatDashboard) {
  statStore.dashboard = d
  statStore.errors.dashboard = false
  touch()
}

export function setAlarms(a: { count: number; list: StatAlarmItem[] }) {
  statStore.alarms = a
  statStore.errors.alarms = false
  touch()
}

export function setVehicleMap(m: StatVehicleMap) {
  statStore.vehicleMap = m
  statStore.errors.map = false
  touch()
}

export function setStaffPerformance(list: StaffPerformanceItem[]) {
  statStore.staffPerformance = list
  statStore.errors.performance = false
  touch()
}

export function markError(key: MetricKey) {
  statStore.errors[key] = true
}
