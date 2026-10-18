<script setup lang="ts">
// 地图选点弹层：小程序用原生 map 组件（卫星图层默认开，中心锚点 + regionchange 取中心）；
// H5 与 admin 选点器同一套高德 JSAPI 2.0 加载方式（getAmapPublicKeys + 同步 script），
// 卫星图层默认叠加、可一键切换；不再提供手动经纬度输入（地图不可用时用关键词搜索选点）。
import { getCurrentInstance, nextTick, onMounted, ref } from 'vue'
import { getAmapPublicKeys } from '@crane/shared/api/map'
import { placeText, regeo } from '../api/map'
import type { PoiItem, RegeoResult } from '../api/map'
import { getLocation, toastError } from '../utils'

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

// 默认中心：公司驻地（无定位权限时的回落点）
const DEFAULT_LNG = 116.467703
const DEFAULT_LAT = 36.189255

const centerLng = ref(Number(props.initialLng) || DEFAULT_LNG)
const centerLat = ref(Number(props.initialLat) || DEFAULT_LAT)
const selLng = ref(centerLng.value)
const selLat = ref(centerLat.value)

const showAmap = ref(false)
const amapFailed = ref(false)
/** 卫星图层默认开启，可切换标准图 */
const satelliteOn = ref(true)

// ---- 关键字搜索（防抖 300ms，走后端代理，不依赖前端地图加载成功） ----
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

// ---- 小程序：原生 map 取中心（卫星图层默认开） ----
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

// ---- H5：高德 JS API 2.0（与 admin AMapPicker 同款加载，卫星图层默认叠加） ----
let amapInstance: any = null
let amapLoader: Promise<any> | null = null
let satelliteLayer: any = null

function loadAmapScript(jsapiKey: string, securityJscode: string): Promise<any> {
  if ((window as any).AMap) return Promise.resolve((window as any).AMap)
  if (amapLoader) return amapLoader
  if (securityJscode) (window as any)._AMapSecurityConfig = { securityJsCode: securityJscode }
  amapLoader = new Promise((resolve, reject) => {
    const script = document.createElement('script')
    script.src = `https://webapi.amap.com/maps?v=2.0&key=${encodeURIComponent(jsapiKey)}`
    script.onload = () => {
      if ((window as any).AMap) resolve((window as any).AMap)
      else reject(new Error('高德地图脚本加载异常'))
    }
    script.onerror = () => {
      amapLoader = null
      reject(new Error('高德地图脚本加载失败，请检查网络或 Key 配置'))
    }
    document.head.appendChild(script)
  })
  return amapLoader
}

function toggleSatellite() {
  satelliteOn.value = !satelliteOn.value
  if (!satelliteLayer) return
  if (satelliteOn.value) satelliteLayer.show()
  else satelliteLayer.hide()
}

async function initAmap(jsapiKey: string, securityJscode: string) {
  try {
    const AMap = await loadAmapScript(jsapiKey, securityJscode)
    await nextTick()
    const el = document.getElementById('amap-container')
    if (!el) {
      amapFailed.value = true
      return
    }
    amapInstance = new AMap.Map(el, {
      zoom: Number(props.initialLng) ? 16 : 12,
      center: [centerLng.value, centerLat.value]
    })
  } catch (e) {
    amapFailed.value = true
    toastError(e)
    return
  }
  // 卫星图层独立降级：叠加失败不影响地图本体可用
  try {
    satelliteOn.value = true
    satelliteLayer = new (window as any).AMap.TileLayer.Satellite({ zIndex: 1 })
    amapInstance.add(satelliteLayer)
  } catch {
    satelliteLayer = null
  }
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
}

// ---- 确认选点：统一 regeo 反查 ----
const confirming = ref(false)

async function onConfirm() {
  const lng = selLng.value
  const lat = selLat.value
  confirming.value = true
  try {
    const r = await regeo(lng, lat)
    emit('confirm', { lng, lat, address: r.formattedAddress, structured: r })
  } catch (e) {
    // 地址反查失败：允许仅经纬度确认，地址由页面兜底
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

/** 无初值时把地图中心设为当前位置；拒绝授权/失败保持默认中心 */
function moveCenterTo(lng: number, lat: number) {
  centerLng.value = lng
  centerLat.value = lat
  selLng.value = lng
  selLat.value = lat
  // #ifdef H5
  amapInstance?.setCenter([lng, lat])
  // #endif
}

function locateCenter() {
  getLocation()
    .then((loc) => moveCenterTo(loc.longitude, loc.latitude))
    .catch(() => {
      // 无定位权限：保持默认中心（公司驻地），不阻断选点
    })
}

onMounted(async () => {
  try {
    const keys = await getAmapPublicKeys()
    if (keys.jsapiKey) {
      showAmap.value = true
      // #ifdef H5
      await initAmap(keys.jsapiKey, keys.securityJscode)
      // #endif
    } else {
      amapFailed.value = true
    }
  } catch {
    amapFailed.value = true
  }
  // 无传入坐标时自动定位到当前位置作为中心（失败回落默认中心）
  if (!Number(props.initialLng)) locateCenter()
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
          :enable-satellite="satelliteOn"
          @regionchange="onRegionChange"
        />
        <view class="layer-toggle" @tap="satelliteOn = !satelliteOn">{{ satelliteOn ? '标准图' : '卫星图' }}</view>
        <view class="crosshair"><view class="ch-v" /><view class="ch-h" /></view>
      </view>
      <!-- #endif -->

      <!-- #ifdef H5 -->
      <view v-if="showAmap && !amapFailed" class="map-area">
        <view id="amap-container" class="native-map" />
        <view class="layer-toggle" @tap="toggleSatellite">{{ satelliteOn ? '标准图' : '卫星图' }}</view>
        <view class="crosshair"><view class="ch-v" /><view class="ch-h" /></view>
      </view>
      <view v-else class="map-area no-map">
        <text class="map-tip">{{ amapFailed ? '地图加载失败，可通过关键词搜索选择地点，或返回使用自动定位' : '地图加载中...' }}</text>
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
  /* 保证地图容器非零高度，避免 JSAPI 初始化时读到 0 尺寸导致空白 */
  min-height: 300px;
  background: var(--bg);
}
.native-map {
  width: 100%;
  height: 100%;
}
.layer-toggle {
  position: absolute;
  right: 12px;
  top: 12px;
  z-index: 20;
  padding: 6px 14px;
  border-radius: var(--radius);
  background: rgba(255, 255, 255, 0.95);
  border: 1px solid var(--line);
  font-size: 13px;
  color: var(--txt);
  box-shadow: var(--shadow);
}
.no-map {
  display: flex;
  align-items: center;
  justify-content: center;
}
.map-tip {
  font-size: 13px;
  color: var(--sub);
  padding: 0 24px;
  text-align: center;
}
.crosshair {
  position: absolute;
  left: 50%;
  top: 50%;
  width: 30px;
  height: 30px;
  margin: -15px 0 0 -15px;
  pointer-events: none;
  z-index: 15;
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
