<template>
  <ScreenPanel title="工单状态分布" class="ring-panel">
    <div ref="chartEl" class="ring-chart"></div>
  </ScreenPanel>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { WORK_ORDER_STATUS_LABEL } from '@crane/shared'
import { statStore } from '../store/stat'
import ScreenPanel from './ScreenPanel.vue'
import echarts from '../charts/echarts'

// 六态语义色（06 §6.1/§6.2）
const STATUS_COLORS: Record<string, string> = {
  PENDING_ACCEPT: '#7d93b5',
  PENDING_ARRIVE: '#ffd54f',
  IN_PROGRESS: '#38bdf8',
  SUSPENDED: '#fb4d63',
  COMPLETED: '#3ddc97',
  CANCELLED: '#55647f'
}
const STATUS_ORDER = [
  'PENDING_ACCEPT',
  'PENDING_ARRIVE',
  'IN_PROGRESS',
  'SUSPENDED',
  'COMPLETED',
  'CANCELLED'
]

const chartEl = ref<HTMLElement>()
let chart: echarts.ECharts | null = null

function render() {
  if (!chart) return
  const dist = statStore.dashboard?.order.statusDistribution ?? []
  const byStatus = new Map(dist.map((d) => [d.status, d.count]))
  const data = STATUS_ORDER.map((status) => ({
    name: WORK_ORDER_STATUS_LABEL[status] ?? status,
    value: byStatus.get(status) ?? 0,
    itemStyle: { color: STATUS_COLORS[status] }
  }))
  const total = data.reduce((s, d) => s + d.value, 0)

  chart.setOption({
    tooltip: {
      trigger: 'item',
      backgroundColor: 'rgba(13,28,54,.92)',
      borderColor: 'rgba(64,156,255,.35)',
      textStyle: { color: '#dbe7f5', fontSize: 12 },
      formatter: '{b}：{c}（{d}%）'
    },
    legend: {
      bottom: 0,
      left: 'center',
      icon: 'circle',
      itemWidth: 8,
      itemHeight: 8,
      itemGap: 12,
      textStyle: { color: '#7d93b5', fontSize: 12 },
      formatter: (name: string) => {
        const item = data.find((d) => d.name === name)
        return `${name} ${item ? item.value : 0}`
      }
    },
    graphic: [
      {
        type: 'text',
        left: 'center',
        top: '34%',
        style: {
          text: String(total),
          fill: '#dbe7f5',
          fontSize: 28,
          fontWeight: 700,
          fontFamily: 'PingFang SC, Microsoft YaHei, sans-serif'
        }
      },
      {
        type: 'text',
        left: 'center',
        top: '46%',
        style: { text: '工单总数', fill: '#7d93b5', fontSize: 12 }
      }
    ],
    series: [
      {
        type: 'pie',
        radius: ['52%', '72%'],
        center: ['50%', '44%'],
        label: { show: false },
        labelLine: { show: false },
        itemStyle: { borderColor: 'rgba(6,12,26,.9)', borderWidth: 2 },
        data
      }
    ]
  })
}

onMounted(() => {
  if (chartEl.value) {
    chart = echarts.init(chartEl.value)
    render()
  }
})

watch(() => statStore.dashboard?.order.statusDistribution, render, { deep: true })

onUnmounted(() => {
  chart?.dispose()
  chart = null
})
</script>

<style scoped>
.ring-chart {
  width: 100%;
  height: 100%;
}
</style>
