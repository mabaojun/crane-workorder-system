<script setup lang="ts">
// 地图选点弹层：小程序用原生 map 组件（中心锚点 + regionchange 取中心），H5 用高德 JS API 2.0；
// H5 未配置 map.amap.jsapi_key 时降级为手动经纬度 + regeo 反查。确认统一走 regeo 反查结构化地址。
import { computed, getCurrentInstance, onMounted, ref, watch } from 'vue'
import { getBranding, loadBranding } from '@crane/shared/api/branding'
import { placeText, regeo } from '../api/map'
import type { PoiItem, RegeoResult } from '../api/map'
import { toastError, toastOk } from '../utils'

export interface MapPickResult {
  lng: number
  lat: number
  /** 格式化地址；regeo 失败时为空串，由页面兜底 */
  address: string
  structured: RegeoResult | null
}

const props = defineProps<{ initialLng?: number; initialLat?: number }>()
const emit = defineEmits<{
  (e: 'confirm', payload: MapPickResult): void
  (e: 'close'): void
}>()

// 默认中心：济南市区（无初值时）
const DEFAULT_LNG = 117.1205
const DEFAULT_LAT = 36.6512

const centerLng = ref(Number(props.initialLng) || DEFAULT_LNG)
const centerLat = ref(Number(props.initialLat) || DEFAULT_LAT)
const selLng = ref(centerLng.value)
const selLat = ref(centerLat.value)

const jsApiKey = ref('')
const amapFailed = ref(false)
const showAmap = computed(() => !!jsApiKey.value && !amapFailed.value)

// ---- 关键字搜索（防抖 300ms） ----
const keyword = ref('')
const searching = ref(false)
const results = ref<PoiItem[]>([])
let searchTimer: ReturnType<typeof setTimeout> | null = null

function onSearchInput() {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(doSearch, 300)
}

async function doSearch() {
  const kw = keyword.value.trim()
  if (!kw) {
    results.value = []
    return
  }
  searching.value = true
  try {
    results.value = (await placeText(kw, selLng.value, selLat.value)) ?? []
  } catch (e) {
    toastError(e)
  } finally {
    searching.value = false
  }
}

function choosePoi(p: PoiItem) {
  selLng.value = Number(p.lng)
  selLat.value = Number(p.lat)
  centerLng.value = selLng.value
  centerLat.value = selLat.value
  // #ifdef H5
  if (amapInstance) amapInstance.setZoomAndCenter(16, [selLng.value, selLat.value])
  // #endif
  keyword.value = p.name
  results.value = []
}

// ---- 小程序：原生 map 取中心 ----
// #ifdef MP-WEIXIN
const instance = getCurrentInstance()
let mapCtx: ReturnType<typeof uni.createMapContext> | null = null

function ensureCtx() {
  if (!mapCtx) mapCtx = uni.createMapContext('pickerMap', instance?.proxy as never)
  return mapCtx
}

function onRegionChange(e?: { detail?: { type?: string }; type?: string }) {
  const t = e?.detail?.type ?? e?.type
  if (t !== 'end') return
  ensureCtx().getCenterLocation({
    success: (res: { longitude?: number; latitude?: number }) => {
      if (res.longitude == null || res.latitude == null) return
      selLng.value = res.longitude
      selLat.value = res.latitude
      centerLng.value = res.longitude
      centerLat.value = res.latitude
    }
  })
}
// #endif

// ---- H5：高德 JS API 2.0 ----
let amapInstance: any = null
let amapLoader: Promise<any> | null = null

function loadAmapScript(key: string): Promise<any> {
  if (amapLoader) return amapLoader
  amapLoader = new Promise((resolve, reject) => {
    if ((window as any).AMap) {
      resolve((window as any).AMap)
      return
    }
    const securityCode = getBranding()['map.amap.security_jscode'] ?? ''
    if (securityCode) (window as any)._AMapSecurityConfig = { securityJsCode: securityCode }
    const cbName = '__craneAmapLoaded'
    ;(window as any)[cbName] = () => resolve((window as any).AMap)
    const script = document.createElement('script')
    script.src = `https://webapi.amap.com/maps?v=2.0&key=${encodeURIComponent(key)}&callback=${cbName}`
    script.onerror = () => {
      amapLoader = null
      reject(new Error('高德地图脚本加载失败'))
    }
    document.head.appendChild(script)
  })
  return amapLoader
}

async function initAmap() {
  try {
    const AMap = await loadAmapScript(jsApiKey.value)
    const el = document.getElementById('amap-container')
    if (!el) {
      amapFailed.value = true
      return
    }
    amapInstance = new AMap.Map(el, {
      zoom: Number(props.initialLng) ? 16 : 12,
      center: [centerLng.value, centerLat.value]
    })
    amapInstance.on('moveend', () => {
      const c = amapInstance.getCenter()
      if (!c) return
      selLng.value = c.getLng()
      selLat.value = c.getLat()
    })
    amapInstance.on('click', (e: { lnglat?: { getLng(): number; getLat(): number } }) => {
      if (!e.lnglat) return
      selLng.value = e.lnglat.getLng()
      selLat.value = e.lnglat.getLat()
      amapInstance.setCenter([selLng.value, selLat.value])
    })
  } catch (e) {
    amapFailed.value = true
    toastError(e)
  }
}

// ---- H5 无 key 降级：手动经纬度 ----
const manualLng = ref(props.initialLng != null ? String(props.initialLng) : '')
const manualLat = ref(props.initialLat != null ? String(props.initialLat) : '')
const manualAddress = ref('')
let manualStructured: RegeoResult | null = null

watch([manualLng, manualLat], () => {
  const lng = Number(manualLng.value)
  const lat = Number(manualLat.value)
  if (Number.isFinite(lng) && Number.isFinite(lat)) {
    selLng.value = lng
    selLat.value = lat
  }
})

async function manualRegeo() {
  const lng = Number(manualLng.value)
  const lat = Number(manualLat.value)
  if (!Number.isFinite(lng) || !Number.isFinite(lat)) {
    uni.showToast({ title: '请先输入合法的经纬度', icon: 'none' })
    return
  }
  try {
    const r = await regeo(lng, lat)
    manualStructured = r
    manualAddress.value = r.formattedAddress
    toastOk('已反查到地址')
  } catch (e) {
    toastError(e)
  }
}

// ---- 确认选点：统一 regeo 反查 ----
const confirming = ref(false)

async function onConfirm() {
  const lng = selLng.value
  const lat = selLat.value
  // 手动输入模式下必须先填合法坐标
  if (!showAmap.value && (!Number.isFinite(Number(manualLng.value)) || !Number.isFinite(Number(manualLat.value)))) {
    uni.showToast({ title: '请先输入经纬度', icon: 'none' })
    return
  }
  // 手动模式已反查成功：直接复用结果，避免重复请求再失败
  if (manualStructured && manualAddress.value) {
    emit('confirm', { lng, lat, address: manualAddress.value, structured: manualStructured })
    return
  }
  confirming.value = true
  try {
    const r = await regeo(lng, lat)
    emit('confirm', { lng, lat, address: r.formattedAddress, structured: r })
  } catch (e) {
    // Key 未配且非 mock 等 regeo 失败场景：允许仅经纬度确认，地址由页面兜底
    const msg = e instanceof Error ? e.message : '地址反查失败'
    uni.showModal({
      title: '未获取到地址',
      content: `${msg}，是否仅提交经纬度？`,
      success: (m) => {
        if (m.confirm) emit('confirm', { lng, lat, address: '', structured: null })
      }
    })
  } finally {
    confirming.value = false
  }
}

onMounted(async () => {
  await loadBranding(true)
  jsApiKey.value = getBranding()['map.amap.jsapi_key'] ?? ''
  // #ifdef H5
  if (jsApiKey.value) await initAmap()
  // #endif
})
</script>

<template>
  <view class="picker-mask">
    <view class="picker">
      <view class="head">
        <text class="title">地图选点</text>
        <text class="hint">拖动地图使中心锚点对准目标位置</text>
        <view class="close" @tap="emit('close')">关闭</view>
      </view>

      <view class="search-wrap">
        <input
          v-model="keyword"
          class="search-input"
          placeholder="搜索地点关键词，如工地、小区名"
          placeholder-class="ph"
          confirm-type="search"
          @input="onSearchInput"
        />
        <view v-if="searching" class="searching">搜索中...</view>
        <view v-if="results.length" class="poi-list">
          <view v-for="(p, i) in results" :key="i" class="poi" @tap="choosePoi(p)">
            <text class="poi-name">{{ p.name }}</text>
            <text class="poi-addr">{{ p.address }}</text>
          </view>
        </view>
      </view>

      <!-- #ifdef MP-WEIXIN -->
      <view class="map-area">
        <map
          id="pickerMap"
          class="native-map"
          :latitude="centerLat"
          :longitude="centerLng"
          :scale="16"
          @regionchange="onRegionChange"
        />
        <view class="crosshair"><view class="ch-v" /><view class="ch-h" /></view>
      </view>
      <!-- #endif -->

      <!-- #ifdef H5 -->
      <view v-if="showAmap" class="map-area">
        <view id="amap-container" class="native-map" />
        <view class="crosshair"><view class="ch-v" /><view class="ch-h" /></view>
      </view>
      <view v-else class="map-area manual">
        <text class="manual-tip">H5 地图需在后台配置 map.amap.jsapi_key，当前可手动输入经纬度选点</text>
        <view class="manual-row">
          <text class="k">经度</text>
          <input v-model="manualLng" class="manual-input" type="digit" placeholder="如 117.120500" placeholder-class="ph" />
        </view>
        <view class="manual-row">
          <text class="k">纬度</text>
          <input v-model="manualLat" class="manual-input" type="digit" placeholder="如 36.651200" placeholder-class="ph" />
        </view>
        <view class="regeo-btn" @tap="manualRegeo">反查地址</view>
        <text v-if="manualAddress" class="manual-addr">地址：{{ manualAddress }}</text>
      </view>
      <!-- #endif -->

      <view class="foot">
        <text class="coords">当前点位：{{ selLng.toFixed(6) }}, {{ selLat.toFixed(6) }}</text>
        <button class="confirm-btn" :disabled="confirming" @tap="onConfirm">确认选点</button>
      </view>
    </view>
  </view>
</template>

<style scoped>
.picker-mask {
  position: fixed;
  inset: 0;
  z-index: 1200;
  background: rgba(15, 23, 42, 0.45);
}
.picker {
  width: 100%;
  height: 100%;
  background: var(--panel);
  display: flex;
  flex-direction: column;
}
.head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 16px;
  border-bottom: 1px solid var(--line);
  padding-top: calc(14px + env(safe-area-inset-top));
}
.title {
  font-size: 17px;
  font-weight: 600;
}
.hint {
  flex: 1;
  font-size: 12px;
  color: var(--sub);
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.close {
  flex: none;
  font-size: 13px;
  color: var(--sub);
  padding: 2px 4px;
}
.search-wrap {
  position: relative;
  padding: 10px 16px;
  border-bottom: 1px solid var(--line);
  z-index: 5;
}
.search-input {
  background: var(--bg);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  padding: 8px 12px;
  font-size: 14px;
}
.ph {
  color: var(--sub);
}
.searching {
  position: absolute;
  right: 28px;
  top: 20px;
  font-size: 12px;
  color: var(--sub);
}
.poi-list {
  position: absolute;
  left: 16px;
  right: 16px;
  top: 52px;
  max-height: 220px;
  overflow-y: auto;
  background: var(--panel);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
}
.poi {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 10px 12px;
  border-bottom: 1px solid var(--line);
}
.poi:last-child {
  border-bottom: none;
}
.poi-name {
  font-size: 14px;
  color: var(--txt);
}
.poi-addr {
  font-size: 12px;
  color: var(--sub);
}
.map-area {
  position: relative;
  flex: 1;
  min-height: 0;
  background: var(--bg);
}
.native-map {
  width: 100%;
  height: 100%;
}
.crosshair {
  position: absolute;
  left: 50%;
  top: 50%;
  width: 30px;
  height: 30px;
  margin: -15px 0 0 -15px;
  pointer-events: none;
}
.ch-v {
  position: absolute;
  left: 50%;
  top: 0;
  width: 2px;
  height: 100%;
  margin-left: -1px;
  background: rgba(29, 78, 216, 0.85);
}
.ch-h {
  position: absolute;
  top: 50%;
  left: 0;
  height: 2px;
  width: 100%;
  margin-top: -1px;
  background: rgba(29, 78, 216, 0.85);
}
.crosshair::after {
  content: '';
  position: absolute;
  left: 50%;
  top: 50%;
  width: 8px;
  height: 8px;
  margin: -4px 0 0 -4px;
  border-radius: 50%;
  background: var(--brand);
  border: 2px solid #fff;
  box-sizing: border-box;
}
.manual {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px;
  box-sizing: border-box;
}
.manual-tip {
  font-size: 13px;
  color: var(--warning);
  background: #fef3c7;
  border-radius: var(--radius);
  padding: 8px 12px;
}
.manual-row {
  display: flex;
  align-items: center;
  gap: 12px;
  background: var(--panel);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  padding: 10px 16px;
}
.manual-row .k {
  flex: none;
  font-size: 14px;
  color: var(--sub);
  width: 32px;
}
.manual-input {
  flex: 1;
  font-size: 15px;
  min-height: 24px;
}
.regeo-btn {
  height: 40px;
  line-height: 40px;
  text-align: center;
  border-radius: var(--radius);
  border: 1px solid var(--brand);
  color: var(--brand);
  font-size: 15px;
  background: var(--panel);
}
.manual-addr {
  font-size: 13px;
  color: var(--success);
}
.foot {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 16px calc(10px + env(safe-area-inset-bottom));
  border-top: 1px solid var(--line);
}
.coords {
  flex: 1;
  font-size: 12px;
  color: var(--sub);
  font-variant-numeric: tabular-nums;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.confirm-btn {
  flex: none;
  width: 132px;
  height: 44px;
  line-height: 44px;
  border-radius: var(--radius);
  background: var(--brand);
  color: #fff;
  font-size: 16px;
  font-weight: 600;
  padding: 0;
  margin: 0;
}
.confirm-btn::after {
  border: none;
}
.confirm-btn[disabled] {
  opacity: 0.6;
  color: #fff;
  background: var(--brand);
}
</style>
