<template>
  <ScreenPanel title="中央车辆分布图" class="map-panel">
    <template #extra>
      <span class="map-badge num">在图 {{ map ? map.onMap : 0 }} / 总 {{ map ? map.total : 0 }}</span>
    </template>
    <div v-if="mode === 'amap'" ref="amapEl" class="map-chart"></div>
    <div v-else ref="chartEl" class="map-chart"></div>
    <div v-if="mode === 'amap'" class="map-legend">
      <span v-for="g in GROUPS" :key="g.name" class="legend-item">
        <i :style="{ background: g.color }"></i>{{ g.name }}
      </span>
      <span class="legend-item"><i class="alarm"></i>异常高亮</span>
    </div>
    <div v-if="mode === 'echarts'" class="map-fallback">高德地图未配置，已降级为点位示意图</div>
    <div v-if="!points.length" class="map-empty">暂无在图点位</div>
  </ScreenPanel>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { WORK_ORDER_STATUS_LABEL } from '@crane/shared'
import type { VehicleMapPoint } from '@crane/shared/api/stat'
import { getAmapPublicKeys } from '@crane/shared/api/map'
import { statStore } from '../store/stat'
import ScreenPanel from './ScreenPanel.vue'
import echarts from '../charts/echarts'

// 优先加载高德 JSAPI 2.0 渲染真实底图；Key 未配置或加载失败时降级为 ECharts 点位示意图（06 §8.3）
const mode = ref<'amap' | 'echarts' | null>(null)
const amapEl = ref<HTMLElement>()
const chartEl = ref<HTMLElement>()
let chart: echarts.ECharts | null = null
let amap: any = null
let infoWindow: any = null
let markers: any[] = []

/** 默认中心点：公司驻地（济南），无点位时展示 */
const DEFAULT_CENTER = { lng: 116.467703, lat: 36.189255 }

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

function escapeHtml(val: string | null | undefined): string {
  return String(val ?? '').replace(/[&<>"]/g, (ch) => {
    return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[ch] || ch
  })
}

/** 高德官方 script 动态加载（安全密钥须在脚本加载前注入） */
declare global {
  interface Window {
    _AMapSecurityConfig?: { securityJsCode: string }
    AMap?: any
  }
}

let amapLoader: Promise<any> | null = null
function loadAmapScript(jsapiKey: string, securityJscode: string): Promise<any> {
  if (window.AMap) return Promise.resolve(window.AMap)
  if (amapLoader) return amapLoader
  if (securityJscode) window._AMapSecurityConfig = { securityJsCode: securityJscode }
  amapLoader = new Promise((resolve, reject) => {
    const script = document.createElement('script')
    script.src = `https://webapi.amap.com/maps?v=2.0&key=${encodeURIComponent(jsapiKey)}`
    script.onload = () => {
      if (window.AMap) resolve(window.AMap)
      else reject(new Error('高德地图脚本加载异常'))
    }
    script.onerror = () => {
      amapLoader = null
      reject(new Error('高德地图脚本加载失败'))
    }
    document.head.appendChild(script)
  })
  return amapLoader
}

async function initAmap(jsapiKey: string, securityJscode: string) {
  const AMap = await loadAmapScript(jsapiKey, securityJscode)
  if (!amapEl.value) throw new Error('地图容器未就绪')
  amap = new AMap.Map(amapEl.value, {
    zoom: 11,
    center: [DEFAULT_CENTER.lng, DEFAULT_CENTER.lat],
    mapStyle: 'amap://styles/dark',
    viewMode: '2D'
  })
  infoWindow = new AMap.InfoWindow({ offset: new AMap.Pixel(0, -14) })
}

function openInfo(p: VehicleMapPoint) {
  if (!amap || !infoWindow) return
  infoWindow.setContent(
    `<div style="padding:6px 8px;font-size:12px;line-height:1.7;color:#1f2d3d;white-space:nowrap;">` +
      `<b>${escapeHtml(p.plateNo)}</b><br/>` +
      `车型：${escapeHtml(p.vehicleSpec || '--')}<br/>` +
      `司机：${escapeHtml(p.staffName || '--')}<br/>` +
      `工单：${escapeHtml(p.orderNo || '--')}<br/>` +
      `状态：${escapeHtml(statusLabel(p.status))}</div>`
  )
  infoWindow.open(amap, [p.lng, p.latitude])
}

/** 高德点位：普通点位三色圆点，异常点位叠加红色光圈 */
function renderAmap() {
  const AMap = window.AMap
  if (!amap || !AMap) return
  markers.forEach((m) => m.setMap(null))
  markers = []

  for (const p of points.value) {
    const color = p.alarm ? '#fb4d63' : groupOf(p).color
    if (p.alarm) {
      const halo = new AMap.CircleMarker({
        center: [p.lng, p.latitude],
        radius: 14,
        strokeColor: color,
        strokeOpacity: 0.45,
        strokeWeight: 1,
        fillColor: color,
        fillOpacity: 0.18,
        zIndex: 100
      })
      halo.setMap(amap)
      markers.push(halo)
    }
    const marker = new AMap.CircleMarker({
      center: [p.lng, p.latitude],
      radius: p.alarm ? 8 : 6,
      strokeColor: '#0a1526',
      strokeWeight: 1.5,
      fillColor: color,
      fillOpacity: 1,
      cursor: 'pointer',
      zIndex: 110
    })
    marker.on('click', () => openInfo(p))
    marker.setMap(amap)
    markers.push(marker)
  }

  if (points.value.length) {
    amap.setFitView(markers, false, [60, 60, 60, 60])
  } else {
    amap.setZoomAndCenter(11, [DEFAULT_CENTER.lng, DEFAULT_CENTER.lat])
  }
}

// ---- ECharts 降级渲染 ----
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
      vehicleType: p.vehicleType,
      vehicleSpec: p.vehicleSpec,
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
            `车型：${d.vehicleSpec || '--'}`,
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

async function initFallbackChart() {
  mode.value = 'echarts'
  await nextTick()
  if (chartEl.value) {
    chart = echarts.init(chartEl.value)
    render()
  }
}

onMounted(async () => {
  try {
    const keys = await getAmapPublicKeys()
    if (keys.jsapiKey) {
      mode.value = 'amap'
      await nextTick()
      await initAmap(keys.jsapiKey, keys.securityJscode)
      renderAmap()
      return
    }
  } catch (e) {
    console.warn('高德地图加载失败，降级为 ECharts 点位图', e)
    amap?.destroy()
    amap = null
    infoWindow = null
  }
  await initFallbackChart()
})

watch(
  points,
  () => {
    if (mode.value === 'amap') renderAmap()
    else render()
  },
  { deep: true }
)

onUnmounted(() => {
  markers.forEach((m) => m.setMap(null))
  markers = []
  infoWindow?.close()
  infoWindow = null
  amap?.destroy()
  amap = null
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

.map-legend {
  position: absolute;
  left: 14px;
  bottom: 12px;
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 5px 10px;
  border-radius: 6px;
  background: rgba(6, 12, 26, 0.66);
  border: 1px solid rgba(64, 156, 255, 0.22);
  font-size: 12px;
  color: var(--sub);
  pointer-events: none;
}
.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}
.legend-item i {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  display: inline-block;
}
.legend-item i.alarm {
  background: #fb4d63;
  box-shadow: 0 0 0 3px rgba(251, 77, 99, 0.28);
}

.map-fallback {
  position: absolute;
  top: 8px;
  left: 50%;
  transform: translateX(-50%);
  padding: 3px 10px;
  border-radius: 10px;
  background: rgba(251, 77, 99, 0.16);
  border: 1px solid rgba(251, 77, 99, 0.35);
  color: #ffb3be;
  font-size: 12px;
  pointer-events: none;
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
