<template>
  <ScreenPanel title="中央车辆分布图" class="map-panel">
    <template #extra>
      <span class="map-badge num">在图 {{ map ? map.onMap : 0 }} / 总 {{ map ? map.total : 0 }}</span>
    </template>
    <div ref="chartEl" class="map-chart"></div>
    <div v-if="!points.length" class="map-empty">暂无在图点位</div>
  </ScreenPanel>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { WORK_ORDER_STATUS_LABEL } from '@crane/shared'
import type { VehicleMapPoint } from '@crane/shared/api/stat'
import { statStore } from '../store/stat'
import ScreenPanel from './ScreenPanel.vue'
import echarts from '../charts/echarts'

// 无高德 Key（二期接入），以 ECharts 深色画布 scatter 绘制预设点位（06 §8.3 底图降级）
const chartEl = ref<HTMLElement>()
let chart: echarts.ECharts | null = null

const map = computed(() => statStore.vehicleMap)
const points = computed(() => statStore.vehicleMap?.points ?? [])

// 三态图例（06 §6.2 语义色映射）
const GROUPS = [
  { name: '已到场', color: '#ffd54f', match: (s: string) => ['PENDING_ACCEPT', 'PENDING_ARRIVE'].includes(s) },
  { name: '进行中', color: '#38bdf8', match: (s: string) => s === 'IN_PROGRESS' },
  { name: '受阻', color: '#fb4d63', match: (s: string) => s === 'SUSPENDED' }
]

function groupOf(p: VehicleMapPoint) {
  return GROUPS.find((g) => g.match(p.status)) ?? GROUPS[0]
}

function statusLabel(s: string): string {
  return WORK_ORDER_STATUS_LABEL[s] ?? s
}

function axisRange(values: number[], fallback: [number, number]): [number, number] {
  if (!values.length) return fallback
  const min = Math.min(...values)
  const max = Math.max(...values)
  const pad = Math.max((max - min) * 0.25, 0.008)
  return [Number((min - pad).toFixed(4)), Number((max + pad).toFixed(4))]
}

function render() {
  if (!chart) return
  const pts = points.value
  const normal = pts.filter((p) => !p.alarm)
  const alarmed = pts.filter((p) => p.alarm)

  const toData = (list: VehicleMapPoint[]) =>
    list.map((p) => ({
      value: [p.lng, p.latitude],
      name: p.plateNo,
      plateNo: p.plateNo,
      staffName: p.staffName,
      orderNo: p.orderNo,
      statusText: statusLabel(p.status),
      updatedAt: p.updatedAt
    }))

  const series: Record<string, unknown>[] = GROUPS.map((g) => ({
    name: g.name,
    type: 'scatter',
    symbolSize: 12,
    itemStyle: { color: g.color, opacity: 0.95 },
    data: toData(normal.filter((p) => groupOf(p) === g))
  }))

  // 异常点位：effectScatter 橙红闪烁 + markPoint 放大标记
  series.push({
    name: '异常高亮',
    type: 'effectScatter',
    symbolSize: 18,
    rippleEffect: { scale: 2.8, brushType: 'stroke' },
    itemStyle: { color: '#fb4d63' },
    zlevel: 2,
    data: toData(alarmed),
    markPoint: {
      symbol: 'pin',
      symbolSize: 22,
      itemStyle: { color: '#fb4d63' },
      label: { show: false },
      data: alarmed.map((p) => ({ coord: [p.lng, p.latitude] }))
    }
  })

  const lngRange = axisRange(pts.map((p) => p.lng), [116.9, 117.25])
  const latRange = axisRange(pts.map((p) => p.latitude), [36.55, 36.85])

  chart.setOption(
    {
      animation: false,
      backgroundColor: 'rgba(6,12,26,.5)',
      tooltip: {
        trigger: 'item',
        backgroundColor: 'rgba(13,28,54,.92)',
        borderColor: 'rgba(64,156,255,.35)',
        textStyle: { color: '#dbe7f5', fontSize: 12 },
        formatter: (params: { data: Record<string, string> }) => {
          const d = params.data
          const lines = [
            `<b>${d.plateNo}</b>`,
            `司机：${d.staffName || '--'}`,
            `工单：${d.orderNo || '--'}`,
            `状态：${d.statusText}`
          ]
          return lines.join('<br/>')
        }
      },
      legend: {
        top: 4,
        right: 8,
        icon: 'circle',
        itemWidth: 8,
        itemHeight: 8,
        textStyle: { color: '#7d93b5', fontSize: 12 },
        data: [...GROUPS.map((g) => g.name), '异常高亮']
      },
      grid: { left: 48, right: 20, top: 32, bottom: 28 },
      xAxis: {
        type: 'value',
        min: lngRange[0],
        max: lngRange[1],
        axisLine: { lineStyle: { color: 'rgba(64,156,255,.35)' } },
        axisLabel: { color: '#7d93b5', fontSize: 11 },
        splitLine: { lineStyle: { color: 'rgba(64,156,255,.12)', type: 'dashed' } }
      },
      yAxis: {
        type: 'value',
        min: latRange[0],
        max: latRange[1],
        axisLine: { lineStyle: { color: 'rgba(64,156,255,.35)' } },
        axisLabel: { color: '#7d93b5', fontSize: 11 },
        splitLine: { lineStyle: { color: 'rgba(64,156,255,.12)', type: 'dashed' } }
      },
      series
    },
    { notMerge: true }
  )
}

onMounted(() => {
  if (chartEl.value) {
    chart = echarts.init(chartEl.value)
    render()
  }
})

watch(points, render, { deep: true })

onUnmounted(() => {
  chart?.dispose()
  chart = null
})
</script>

<style scoped>
.map-chart {
  width: 100%;
  height: 100%;
}

.map-badge {
  color: var(--cyan2);
  font-size: 13px;
}

.map-empty {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--sub);
  font-size: 13px;
  pointer-events: none;
}
</style>
