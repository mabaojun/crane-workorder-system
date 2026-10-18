<template>
  <el-dialog
    :model-value="visible"
    title="地图选点"
    width="720px"
    :close-on-click-modal="false"
    destroy-on-close
    @open="onOpen"
    @close="onClose"
  >
    <div v-loading="loading" class="picker-body">
      <!-- 降级模式：未配置 JSAPI Key -->
      <template v-if="ready && mode === 'manual'">
        <el-alert type="warning" :closable="false" show-icon class="degrade-tip">
          <template #title>
            未配置高德地图 Key，请前往 系统配置 → map.amap.jsapi_key 填写（申请方式见
            docs/功能说明/地图选点与导航配置指南.md）
          </template>
        </el-alert>
        <el-form label-width="80px" class="manual-form">
          <el-form-item label="经纬度">
            <div class="manual-row">
              <el-input-number
                v-model="manual.lng"
                :precision="7"
                :controls="false"
                placeholder="经度"
                style="width: 100%"
              />
              <el-input-number
                v-model="manual.lat"
                :precision="7"
                :controls="false"
                placeholder="纬度"
                style="width: 100%"
              />
              <el-button :loading="reverseGeocoding" @click="onManualRegeo">查询地址</el-button>
            </div>
          </el-form-item>
          <el-form-item label="地址">
            <el-input v-model="manual.address" maxlength="255" placeholder="可手动填写，或点击「查询地址」反查" />
          </el-form-item>
        </el-form>
      </template>

      <!-- 地图模式 -->
      <template v-else-if="ready && mode === 'map'">
        <el-autocomplete
          v-model="keyword"
          class="search-box"
          :fetch-suggestions="onSearch"
          placeholder="搜索地点关键词，如：济南奥体中心"
          clearable
          :trigger-on-focus="false"
          value-key="label"
          @select="onSelectPoi"
        />
        <div class="map-wrap">
          <div ref="mapRef" class="map-container"></div>
          <div class="crosshair" title="拖动地图取中心点">
            <span class="crosshair-h"></span>
            <span class="crosshair-v"></span>
          </div>
        </div>
        <div class="picked-bar">
          <template v-if="picked.lng != null && picked.lat != null">
            <span>已选坐标：{{ picked.lng.toFixed(7) }}，{{ picked.lat.toFixed(7) }}</span>
            <span v-if="picked.address" class="picked-address">{{ picked.address }}</span>
          </template>
          <span v-else class="picked-address">点击地图取点，或拖动地图使目标位于中心十字处</span>
        </div>
      </template>
    </div>
    <template #footer>
      <el-button @click="closeDialog">取消</el-button>
      <el-button type="primary" :loading="confirming" @click="onConfirm">确认选点</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { nextTick, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  amapPlaceText,
  amapRegeo,
  getAmapPublicKeys,
  type AmapPickResult,
  type PoiItem,
  type RegeoResult
} from '@crane/shared/api/map'

/** 高德 JS API 2.0 官方动态 script 加载（业务必需），这里仅声明所需的最小能力 */
declare global {
  interface Window {
    _AMapSecurityConfig?: { securityJsCode: string }
    AMap?: any
  }
}

const props = defineProps<{
  visible: boolean
  lng?: number
  lat?: number
  address?: string
}>()

const emit = defineEmits<{
  (e: 'update:visible', v: boolean): void
  (e: 'confirm', payload: AmapPickResult): void
  (e: 'cancel'): void
}>()

/** 济南默认中心点，与直建工单表单默认经纬度一致 */
const DEFAULT_CENTER = { lng: 117.1205, lat: 36.6519 }

const loading = ref(false)
const ready = ref(false)
/** map：高德 JSAPI 渲染；manual：Key 未配置 / 加载失败的降级模式 */
const mode = ref<'map' | 'manual'>('map')

const keyword = ref('')
const mapRef = ref<HTMLDivElement | null>(null)
let map: any = null
let marker: any = null

const picked = reactive<{ lng: number | null; lat: number | null; address: string }>({
  lng: null,
  lat: null,
  address: ''
})

const manual = reactive<{ lng: number | null; lat: number | null; address: string }>({
  lng: null,
  lat: null,
  address: ''
})

const confirming = ref(false)
const reverseGeocoding = ref(false)
let confirmed = false

async function onOpen() {
  confirmed = false
  ready.value = false
  loading.value = true
  picked.lng = typeof props.lng === 'number' ? props.lng : null
  picked.lat = typeof props.lat === 'number' ? props.lat : null
  picked.address = props.address ?? ''
  keyword.value = ''
  manual.lng = typeof props.lng === 'number' ? props.lng : null
  manual.lat = typeof props.lat === 'number' ? props.lat : null
  manual.address = props.address ?? ''
  try {
    const keys = await getAmapPublicKeys()
    if (keys.jsapiKey) {
      mode.value = 'map'
      await nextTick()
      try {
        await initMap(keys.jsapiKey, keys.securityJscode)
      } catch (e) {
        ElMessage.error(e instanceof Error ? e.message : '地图加载失败')
        mode.value = 'manual'
      }
    } else {
      mode.value = 'manual'
    }
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '地图配置读取失败')
    mode.value = 'manual'
  } finally {
    ready.value = true
    loading.value = false
  }
}

/** 动态注入高德官方 loader（安全密钥须在脚本加载前注入） */
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
      reject(new Error('高德地图脚本加载失败，请检查网络或 Key 配置'))
    }
    document.head.appendChild(script)
  })
  return amapLoader
}

async function initMap(jsapiKey: string, securityJscode: string) {
  const AMap = await loadAmapScript(jsapiKey, securityJscode)
  if (!mapRef.value) return
  map = new AMap.Map(mapRef.value, {
    zoom: 15,
    center: [
      picked.lng ?? DEFAULT_CENTER.lng,
      picked.lat ?? DEFAULT_CENTER.lat
    ]
  })
  // 交互一：点击取点
  map.on('click', (e: any) => {
    pick(e.lnglat.getLng(), e.lnglat.getLat())
  })
  // 交互二：拖拽取中心锚点
  map.on('dragend', () => {
    const center = map.getCenter()
    pick(center.getLng(), center.getLat())
  })
  if (picked.lng != null && picked.lat != null) {
    placeMarker(picked.lng, picked.lat)
  }
}

function pick(lng: number, lat: number) {
  picked.lng = lng
  picked.lat = lat
  if (mode.value === 'map') placeMarker(lng, lat)
}

function placeMarker(lng: number, lat: number) {
  const AMap = window.AMap
  if (!map || !AMap) return
  if (!marker) {
    marker = new AMap.Marker({ position: [lng, lat], map })
  } else {
    marker.setPosition([lng, lat])
  }
}

/** 交互三：关键词搜索（经后端代理 /system/amap/place/text） */
async function onSearch(query: string, callback: (results: Array<{ label: string; poi: PoiItem }>) => void) {
  if (!query.trim()) {
    callback([])
    return
  }
  try {
    const list = await amapPlaceText(query.trim(), picked.lng ?? undefined, picked.lat ?? undefined)
    callback(
      (list || []).map((poi) => ({
        label: `${poi.name}（${poi.address || poi.district || ''}）`,
        poi
      }))
    )
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '地点搜索失败')
    callback([])
  }
}

function onSelectPoi(item: { label: string; poi: PoiItem }) {
  const { lng, lat } = item.poi
  picked.lng = lng
  picked.lat = lat
  picked.address = item.poi.address ? `${item.poi.name}（${item.poi.address}）` : item.poi.name
  if (map) {
    map.setCenter([lng, lat])
    placeMarker(lng, lat)
  }
}

/** 降级模式：手动坐标反查地址（后端 mock 会返回模拟地址） */
async function onManualRegeo() {
  if (manual.lng == null || manual.lat == null) {
    ElMessage.warning('请先填写经纬度')
    return
  }
  reverseGeocoding.value = true
  try {
    const re = await amapRegeo(manual.lng, manual.lat)
    manual.address = re.formattedAddress
    picked.address = re.formattedAddress
    ElMessage.success('地址反查成功')
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '地址反查失败')
  } finally {
    reverseGeocoding.value = false
  }
}

async function regeo(lng: number, lat: number): Promise<RegeoResult | null> {
  try {
    return await amapRegeo(lng, lat)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '逆地理编码失败')
    return null
  }
}

async function onConfirm() {
  // 统一取当前选点：地图模式取 picked，降级模式取 manual
  const lng = mode.value === 'map' ? picked.lng : manual.lng
  const lat = mode.value === 'map' ? picked.lat : manual.lat
  if (lng == null || lat == null) {
    ElMessage.warning('请先选择坐标（点击地图 / 拖动地图 / 搜索，或手动输入经纬度）')
    return
  }
  confirming.value = true
  try {
    let address = mode.value === 'map' ? picked.address : manual.address
    let structured: AmapPickResult['structured'] | undefined
    const re = await regeo(lng, lat)
    if (re) {
      address = re.formattedAddress || address
      structured = {
        province: re.province,
        city: re.city,
        district: re.district,
        township: re.township,
        adcode: re.adcode
      }
    } else if (!address) {
      address = ''
    }
    confirmed = true
    emit('confirm', { lng, lat, address, structured })
    emit('update:visible', false)
  } finally {
    confirming.value = false
  }
}

function closeDialog() {
  emit('update:visible', false)
}

function onClose() {
  if (!confirmed) emit('cancel')
  destroyMap()
  ready.value = false
}

function destroyMap() {
  if (map) {
    map.destroy?.()
    map = null
  }
  marker = null
}
</script>

<style scoped>
.picker-body {
  min-height: 200px;
}
.degrade-tip {
  margin-bottom: 12px;
}
.manual-form {
  margin-top: 4px;
}
.manual-row {
  display: flex;
  gap: 8px;
  width: 100%;
}
.search-box {
  width: 100%;
  margin-bottom: 8px;
}
.map-wrap {
  position: relative;
}
.map-container {
  width: 100%;
  height: 380px;
  border-radius: 4px;
  overflow: hidden;
}
.crosshair {
  position: absolute;
  left: 50%;
  top: 50%;
  width: 28px;
  height: 28px;
  transform: translate(-50%, -50%);
  pointer-events: none;
  z-index: 10;
}
.crosshair-h,
.crosshair-v {
  position: absolute;
  background: #f56c6c;
  box-shadow: 0 0 2px rgba(255, 255, 255, 0.9);
}
.crosshair-h {
  left: 0;
  right: 0;
  top: 50%;
  height: 2px;
  transform: translateY(-50%);
}
.crosshair-v {
  top: 0;
  bottom: 0;
  left: 50%;
  width: 2px;
  transform: translateX(-50%);
}
.picked-bar {
  margin-top: 8px;
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
  font-size: 13px;
  color: var(--sub, #909399);
}
.picked-address {
  color: var(--sub, #909399);
}
</style>
