<script setup lang="ts">
// 客户端需求提报（T-33）：表单提交 → POST /demand/orders
// 位置支持：自动定位 / 地图选点（MapPicker）；提交模式：全量（经纬度+地址，默认）或仅经纬度（野外无地址场景）
import { computed, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { createDemandOrder, listDemands } from '../../api/modules/biz'
import { listCommonPlaces } from '@crane/shared/api/place'
import type { CommonPlace } from '@crane/shared/api/place'
import type { DemandOrder } from '../../api/types'
import MapPicker from '../../components/MapPicker.vue'
import type { MapPickResult } from '../../components/MapPicker.vue'
import { useAuthStore } from '../../stores/auth'
import { chooseImages, getLocation, toastError, toastOk } from '../../utils'

const auth = useAuthStore()

const contactName = ref('')
const contactPhone = ref('')
const address = ref('')
const coords = ref<{ longitude: number; latitude: number } | null>(null)
const locating = ref(false)
const description = ref('')
const cargoWeight = ref('')
const expectDate = ref('')
const expectTime = ref('')
const photos = ref<string[]>([])
const parentDemandId = ref<number | undefined>(undefined)

// ---- 位置提交模式：full=全量（经纬度+地址文本）；coord=仅经纬度（地址自动生成占位文本满足必填） ----
const showPicker = ref(false)
const locationMode = ref<'full' | 'coord'>('full')

// ---- 常用施工地点：默认下拉全量展示，输入关键字即时模糊过滤（纯前端过滤，响应<100ms），点击回填地址 ----
const places = ref<CommonPlace[]>([])
const filteredPlaces = computed(() => {
  const kw = address.value.trim().toLowerCase()
  if (!kw) return places.value
  return places.value.filter((p) => p.placeName.toLowerCase().includes(kw))
})

function pickPlace(p: CommonPlace) {
  address.value = p.placeName
}

onLoad((opts) => {
  // 「再次提报」：读取驳回单草稿（由需求列表页写入）
  const draftRaw = uni.getStorageSync('demand_draft')
  if (opts?.parentDemandId && draftRaw) {
    const d = draftRaw as DemandOrder
    parentDemandId.value = Number(opts.parentDemandId)
    contactName.value = d.contactName ?? ''
    contactPhone.value = d.contactPhone ?? ''
    address.value = d.address ?? ''
    description.value = d.description ?? ''
    cargoWeight.value = d.cargoWeight != null ? String(d.cargoWeight) : ''
    uni.removeStorageSync('demand_draft')
  }
})

onShow(() => {
  if (auth.me) {
    contactName.value = contactName.value || auth.me.name || ''
    contactPhone.value = contactPhone.value || auth.me.phone || ''
  }
  // 常用地点列表静默加载（失败不影响表单）
  if (!places.value.length) {
    listCommonPlaces()
      .then((list) => (places.value = list))
      .catch(() => {})
  }
})

async function locate() {
  locating.value = true
  try {
    coords.value = await getLocation()
    toastOk('已定位')
  } catch {
    toastError(new Error('定位失败，请检查定位权限'))
  } finally {
    locating.value = false
  }
}

/** 地图选点确认：回填经纬度；无地址（regeo 失败）时生成占位文本兜底 */
function onPicked(p: MapPickResult) {
  coords.value = { longitude: p.lng, latitude: p.lat }
  address.value = p.address || `自定义点位（经度${p.lng}，纬度${p.lat}）`
  showPicker.value = false
  toastOk('已选择点位')
}

async function takePhoto() {
  try {
    const paths = await chooseImages(1)
    photos.value = [...photos.value, ...paths].slice(0, 3)
  } catch {
    // 用户取消
  }
}

async function submit() {
  if (!contactPhone.value.trim()) {
    uni.showToast({ title: '请填写联系电话', icon: 'none' })
    return
  }
  if (!/^1\d{10}$/.test(contactPhone.value.trim())) {
    uni.showToast({ title: '联系电话须为 11 位手机号', icon: 'none' })
    return
  }
  // 位置按提交模式组装：coord 模式地址自动生成占位文本以满足后端必填
  let submitAddress = address.value.trim()
  const submitLng = coords.value?.longitude
  const submitLat = coords.value?.latitude
  if (locationMode.value === 'coord') {
    if (submitLng == null || submitLat == null) {
      uni.showToast({ title: '请先地图选点或自动定位获取坐标', icon: 'none' })
      return
    }
    submitAddress = `自定义点位（经度${submitLng}，纬度${submitLat}）`
  } else if (!submitAddress) {
    uni.showToast({ title: '请填写施工地址', icon: 'none' })
    return
  }
  try {
    uni.showLoading({ title: '提交中' })
    const expectedStartAt = expectDate.value && expectTime.value ? `${expectDate.value}T${expectTime.value}:00` : undefined
    // TODO(二期): 对接文件上传，photos 目前仅本地预览
    await createDemandOrder({
      source: 'CUSTOMER_MP',
      contactName: contactName.value.trim(),
      contactPhone: contactPhone.value.trim(),
      address: submitAddress,
      longitude: submitLng,
      latitude: submitLat,
      description: description.value.trim() || undefined,
      imageUrls: photos.value.length ? ['mock://local'] : undefined,
      cargoWeight: cargoWeight.value ? Number(cargoWeight.value) : undefined,
      expectedStartAt,
      parentDemandId: parentDemandId.value
    })
    uni.hideLoading()
    toastOk('需求已提交，等待复核')
    setTimeout(() => {
      uni.redirectTo({ url: '/pages/customer/demands' })
    }, 800)
  } catch (e) {
    uni.hideLoading()
    toastError(e)
  }
}
</script>

<template>
  <view class="page">
    <view class="form">
      <view class="field">
        <text class="label">联系人</text>
        <input v-model="contactName" class="input" placeholder="请输入联系人姓名" placeholder-class="ph" />
      </view>
      <view class="field">
        <text class="label">联系电话</text>
        <input v-model="contactPhone" class="input" type="number" maxlength="11" placeholder="请输入联系电话" placeholder-class="ph" />
      </view>
      <view class="field col">
        <text class="label">施工地址</text>
        <input
          v-if="locationMode === 'full'"
          v-model="address"
          class="input"
          placeholder="输入关键字选择常用地点，或直接填写地址"
          placeholder-class="ph"
        />
        <!-- 常用地点下拉：默认全量展示，输入即时模糊过滤，点击回填地址 -->
        <view v-if="locationMode === 'full' && filteredPlaces.length" class="place-list">
          <view v-for="p in filteredPlaces" :key="p.id" class="place-item" @tap="pickPlace(p)">
            {{ p.placeName }}
          </view>
        </view>
        <view class="locate-row">
          <view class="locate" @tap="locate">{{ locating ? '定位中...' : '自动定位' }}</view>
          <view class="locate" @tap="showPicker = true">地图选点</view>
          <text v-if="coords" class="located">已定位（{{ coords.longitude.toFixed(4) }}, {{ coords.latitude.toFixed(4) }}）</text>
        </view>
        <text class="wild-hint">野外无地址时可直接拖动地图中心锚点选点</text>
      </view>
      <view class="field col">
        <text class="label">位置信息提交模式</text>
        <view class="mode-row">
          <view class="mode-opt" :class="{ active: locationMode === 'full' }" @tap="locationMode = 'full'">全量（经纬度+地址）</view>
          <view class="mode-opt" :class="{ active: locationMode === 'coord' }" @tap="locationMode = 'coord'">仅经纬度</view>
        </view>
        <text class="mode-tip">
          {{ locationMode === 'full' ? '模式一：同时提交经纬度与地址文本，适用于有明确地址的现场' : '模式二：仅提交经纬度，提交时地址自动记为自定义点位，适用于野外无地址场景' }}
        </text>
      </view>
      <view class="field col">
        <text class="label">需求描述</text>
        <textarea v-model="description" class="textarea" placeholder="吊装任务内容、现场条件等" :maxlength="500" />
      </view>
      <view class="field">
        <text class="label">货物重量(吨)</text>
        <input v-model="cargoWeight" class="input" type="digit" placeholder="选填，如 8" placeholder-class="ph" />
      </view>
      <view class="field half-wrap">
        <view class="half">
          <text class="label">期望开始日期</text>
          <picker mode="date" :value="expectDate" @change="(e: any) => (expectDate = e.detail.value)">
            <view class="picker-value">{{ expectDate || '选择日期' }}</view>
          </picker>
        </view>
        <view class="half">
          <text class="label">期望开始时间</text>
          <picker mode="time" :value="expectTime" @change="(e: any) => (expectTime = e.detail.value)">
            <view class="picker-value">{{ expectTime || '选择时间' }}</view>
          </picker>
        </view>
      </view>
      <view class="field col">
        <text class="label">现场照片</text>
        <view class="photo-row">
          <view v-for="(p, i) in photos" :key="i" class="photo"><image :src="p" mode="aspectFill" /></view>
          <view v-if="photos.length < 3" class="photo add" @tap="takePhoto">拍照</view>
        </view>
      </view>

      <button class="submit" @tap="submit">提交需求</button>
    </view>

    <!-- 地图选点弹层：带回显当前坐标 -->
    <MapPicker
      v-if="showPicker"
      :initial-lng="coords?.longitude"
      :initial-lat="coords?.latitude"
      @confirm="onPicked"
      @close="showPicker = false"
    />
  </view>
</template>

<style scoped>
.page {
  min-height: 100vh;
  background: var(--bg);
  padding: 16px 16px 96px;
  box-sizing: border-box;
}
.form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.field {
  background: var(--panel);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  padding: 10px 16px;
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 15px;
}
.field.col {
  flex-direction: column;
  align-items: stretch;
  gap: 8px;
}
.label {
  flex: none;
  color: var(--sub);
  font-size: 13px;
  width: 96px;
}
.field.col .label {
  width: auto;
}
.half-wrap {
  display: flex;
  gap: 12px;
}
.half {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
  background: var(--panel);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  padding: 10px 16px;
}
.half .label {
  width: auto;
}
.input {
  flex: 1;
  font-size: 15px;
  min-height: 24px;
}
.ph {
  color: var(--sub);
}
.textarea {
  width: 100%;
  box-sizing: border-box;
  min-height: 88px;
  font-size: 14px;
}
.locate-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.place-list {
  background: var(--bg);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  max-height: 180px;
  overflow-y: auto;
}
.place-item {
  min-height: 44px;
  display: flex;
  align-items: center;
  padding: 0 12px;
  font-size: 14px;
  color: var(--txt);
  border-bottom: 1px solid var(--line);
}
.place-item:last-child {
  border-bottom: none;
}
.place-item:active {
  background: var(--brand-weak);
  color: var(--brand);
}
.locate {
  font-size: 13px;
  color: var(--brand);
}
.located {
  font-size: 12px;
  color: var(--success);
}
.wild-hint {
  font-size: 12px;
  color: var(--sub);
  background: var(--brand-weak);
  border-radius: var(--radius);
  padding: 6px 10px;
}
.mode-row {
  display: flex;
  gap: 8px;
}
.mode-opt {
  padding: 6px 14px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  font-size: 14px;
  color: var(--txt);
  background: var(--panel);
}
.mode-opt.active {
  border-color: var(--brand);
  color: var(--brand);
  background: var(--brand-weak);
}
.mode-tip {
  font-size: 12px;
  color: var(--sub);
}
.picker-value {
  font-size: 15px;
  color: var(--txt);
  min-height: 24px;
}
.photo-row {
  display: flex;
  gap: 8px;
}
.photo {
  width: 64px;
  height: 64px;
  border-radius: var(--radius);
  overflow: hidden;
  background: var(--bg);
}
.photo image {
  width: 100%;
  height: 100%;
}
.photo.add {
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px dashed var(--line);
  color: var(--sub);
  font-size: 13px;
}
.submit {
  margin-top: 8px;
  height: 48px;
  line-height: 48px;
  border-radius: var(--radius);
  background: var(--brand);
  color: #fff;
  font-size: 17px;
  font-weight: 600;
}
.submit::after {
  border: none;
}
</style>
