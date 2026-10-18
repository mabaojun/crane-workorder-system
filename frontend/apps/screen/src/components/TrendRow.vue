<template>
  <div class="trend-row">
    <ScreenPanel title="近 7 日工单趋势" class="trend-panel">
      <div ref="trendEl" class="trend-chart"></div>
    </ScreenPanel>
    <ScreenPanel title="当日异常类型分布" class="trend-panel">
      <div v-if="exceptions.length" ref="exEl" class="trend-chart"></div>
      <div v-else class="trend-empty">当日无异常记录</div>
    </ScreenPanel>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { statStore } from '../store/stat'
import ScreenPanel from './ScreenPanel.vue'
import echarts from '../charts/echarts'

const trendEl = ref<HTMLElement>()
const exEl = ref<HTMLElement>()
let trendChart: echarts.ECharts | null = null
let exChart: echarts.ECharts | null = null

const exceptions = computed(() => statStore.dashboard?.exceptionDistribution ?? [])

function baseAxisStyle() {
  return {
    axisLine: { lineStyle: { color: 'rgba(64,156,255,.35)' } },
    axisTick: { show: false },
    axisLabel: { color: '#7d93b5', fontSize: 11 },
    splitLine: { lineStyle: { color: 'rgba(64,156,255,.12)', type: 'dashed' } }
  }
}

function renderTrend() {
  if (!trendChart) return
  const trend = statStore.dashboard?.trend7d ?? []
  trendChart.setOption({
    grid: { left: 36, right: 12, top: 24, bottom: 24 },
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(13,28,54,.92)',
      borderColor: 'rgba(64,156,255,.35)',
      textStyle: { color: '#dbe7f5', fontSize: 12 }
    },
    xAxis: { type: 'category', data: trend.map((t) => t.date), ...baseAxisStyle(), splitLine: { show: false } },
    yAxis: { type: 'value', minInterval: 1, ...baseAxisStyle() },
    series: [
      {
        type: 'bar',
        data: trend.map((t) => t.count),
        barWidth: 14,
        itemStyle: {
          borderRadius: [3, 3, 0, 0],
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#38bdf8' },
            { offset: 1, color: 'rgba(56,189,248,.15)' }
          ])
        }
      }
    ]
  })
}

function renderException() {
  if (!exChart) return
  const list = [...exceptions.value].reverse()
  exChart.setOption({
    grid: { left: 76, right: 30, top: 10, bottom: 24 },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      backgroundColor: 'rgba(13,28,54,.92)',
      borderColor: 'rgba(64,156,255,.35)',
      textStyle: { color: '#dbe7f5', fontSize: 12 }
    },
    xAxis: { type: 'value', minInterval: 1, ...baseAxisStyle() },
    yAxis: {
      type: 'category',
      data: list.map((e) => e.label),
      ...baseAxisStyle(),
      splitLine: { show: false }
    },
    series: [
      {
        type: 'bar',
        data: list.map((e) => e.count),
        barWidth: 10,
        itemStyle: {
          borderRadius: [0, 3, 3, 0],
          color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
            { offset: 0, color: 'rgba(255,171,64,.2)' },
            { offset: 1, color: '#ffab40' }
          ])
        },
        label: { show: true, position: 'right', color: '#7d93b5', fontSize: 11 }
      }
    ]
  })
}

onMounted(() => {
  if (trendEl.value) {
    trendChart = echarts.init(trendEl.value)
    renderTrend()
  }
  if (exEl.value) {
    exChart = echarts.init(exEl.value)
    renderException()
  }
})

watch(
  () => statStore.dashboard,
  () => {
    renderTrend()
    // 异常分布从空到有切换了 DOM（v-if），需要重建实例
    if (exceptions.value.length && !exChart && exEl.value) {
      exChart = echarts.init(exEl.value)
    }
    renderException()
  },
  { deep: true }
)

onUnmounted(() => {
  trendChart?.dispose()
  exChart?.dispose()
  trendChart = null
  exChart = null
})
</script>

<style scoped>
.trend-row {
  height: 230px;
  flex: none;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  min-height: 0;
}

.trend-panel {
  min-width: 0;
}

.trend-chart {
  width: 100%;
  height: 100%;
}

.trend-empty {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--sub);
  font-size: 13px;
}
</style>
