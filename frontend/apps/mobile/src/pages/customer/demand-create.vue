<script setup lang="ts">
// 客户端需求提报（T-33）：表单提交 → POST /demand/orders
// 位置：自动定位 / 地图选点 / 常用地点下拉（本客户历史工单地点），统一全量提交（经纬度+地址），转工单时管理员二次选点
import { computed, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { createDemandOrder, listDemands } from '../../api/modules/biz'
import { listMyPlaces } from '@crane/shared/api/place'
import type { MyPlace } from '@crane/shared/api/place'
import type { DemandOrder } from '../../api/types'
import MapPicker from '../../components/MapPicker.vue'
import type { MapPickResult } from '../../components/MapPicker.vue'
import RoleTabbar from '../../components/RoleTabbar.vue'
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
const craneTonnage = ref('')
const estimatedDays = ref('')
/** 设备吨位常用档位：点选快捷，也可直接输入其他吨位 */
const TONNAGE_PRESETS = [8, 16, 25, 50, 70, 80, 100, 110, 130, 150, 200, 300]
/** 预计使用时长常用档位（天）：点选快捷，也可直接输入（0.5 步进），仅备注提示 */
const DAYS_PRESETS = [0.5, 1, 1.5, 2, 3]
const expectDate = ref('')
const expectTime = ref('')
const photos = ref<string[]>([])
const parentDemandId = ref<number | undefined>(undefined)

const showPicker = ref(false)

// ---- 常用地点：本客户历史工单使用过的施工地点；下拉菜单式展示，输入即时过滤，一次最多 5 条 ----
const places = ref<MyPlace[]>([])
const filteredPlaces = computed(() => {
  const kw = address.value.trim().toLowerCase()
  const matched = kw
    ? places.value.filter((p) => p.placeName.toLowerCase().includes(kw))
    : places.value
  return matched.slice(0, 5)
})

function pickPlace(p: MyPlace) {
  address.value = p.placeName
  // 常用地点自带经纬度时直接带入，免去重复定位/选点
  if (p.longitude != null && p.latitude != null) {
    coords.value = { longitude: p.longitude, latitude: p.latitude }
  }
}

/** 期望日期快捷：今天 / 明天 / 后天 */
function setQuickDate(offset: number) {
  const d = new Date()
  d.setDate(d.getDate() + offset)
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  expectDate.value = `${y}-${m}-${day}`
}

/** 需求描述常用项：点选追加、再点移除（与后台转工单备注一致） */
const DESC_PRESETS = ['带吊笼', '带顶笼']

function toggleDescPreset(text: string) {
  if (description.value.includes(text)) {
    description.value = description.value
      .split(/[，,]/)
      .map((s) => s.trim())
      .filter((s) => s && s !== text)
      .join('，')
  } else {
    description.value = description.value.trim()
      ? `${description.value.trim().replace(/[，,]$/, '')}，${text}`
      : text
  }
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
    estimatedDays.value = d.estimatedDays != null ? String(d.estimatedDays) : ''
    uni.removeStorageSync('demand_draft')
  }
})

onShow(() => {
  if (auth.me) {
    contactName.value = contactName.value || auth.me.name || ''
    contactPhone.value = contactPhone.value || auth.me.phone || ''
  }
  // 常用地点（本客户历史工单地点）静默加载（失败不影响表单）
  if (!places.value.length) {
    listMyPlaces()
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
  // 位置统一全量提交：经纬度+地址文本（转工单时管理员可地图二次选点校准）
  const submitAddress = address.value.trim()
  const submitLng = coords.value?.longitude
  const submitLat = coords.value?.latitude
  if (!submitAddress) {
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
      // 设备吨位：点选常用档位或直接输入
      craneTonnage: craneTonnage.value ? Number(craneTonnage.value) : undefined,
      // 预计使用时长：点选常用档位或直接输入，仅备注提示不作换算
      estimatedDays: estimatedDays.value ? Number(estimatedDays.value) : undefined,
      expectedStartAt,
      parentDemandId: parentDemandId.value
    })
    uni.hideLoading()
    toastOk('需求已提交，管理员将尽快处置')
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
          v-model="address"
          class="input"
          placeholder="输入关键字选择常用地点，或直接填写地址"
          placeholder-class="ph"
        />
        <!-- 常用地点下拉：本客户历史工单地点，输入即时过滤，一次最多 5 条，点击回填地址与坐标 -->
        <view v-if="filteredPlaces.length" class="place-list">
          <view v-for="p in filteredPlaces" :key="p.placeName" class="place-item" @tap="pickPlace(p)">
            {{ p.placeName }}
          </view>
        </view>
        <view class="locate-row">
          <view class="locate" @tap="locate">{{ locating ? '定位中...' : '自动定位' }}</view>
          <view class="locate" @tap="showPicker = true">地图选点</view>
          <text v-if="coords" class="located">已定位（{{ coords.longitude.toFixed(4) }}, {{ coords.latitude.toFixed(4) }}）</text>
        </view>
      </view>
      <view class="field col">
        <text class="label">需求描述</text>
        <view class="desc-chips">
          <view
            v-for="t in DESC_PRESETS"
            :key="t"
            class="desc-chip"
            :class="{ active: description.includes(t) }"
            @tap="toggleDescPreset(t)"
          >{{ t }}</view>
        </view>
        <textarea v-model="description" class="textarea" placeholder="吊装任务内容、现场条件等" :maxlength="500" />
      </view>
      <view class="field">
        <text class="label">货物重量(吨)</text>
        <input v-model="cargoWeight" class="input" type="digit" placeholder="选填，如 8" placeholder-class="ph" />
      </view>
      <view class="field">
        <text class="label">设备吨位(吨)</text>
        <input v-model="craneTonnage" class="input" type="digit" placeholder="点选下方常用吨位，或直接输入" placeholder-class="ph" />
        <view class="tonnage-chips">
          <view
            v-for="t in TONNAGE_PRESETS"
            :key="t"
            class="tonnage-chip"
            :class="{ active: craneTonnage === String(t) }"
            @tap="craneTonnage = craneTonnage === String(t) ? '' : String(t)"
          >{{ t }}</view>
        </view>
      </view>
      <view class="field">
        <text class="label">预计使用时长(天)</text>
        <input v-model="estimatedDays" class="input" type="digit" placeholder="选填，点选下方常用时长，或直接输入" placeholder-class="ph" />
        <view class="tonnage-chips">
          <view
            v-for="d in DAYS_PRESETS"
            :key="d"
            class="tonnage-chip"
            :class="{ active: estimatedDays === String(d) }"
            @tap="estimatedDays = estimatedDays === String(d) ? '' : String(d)"
          >{{ d }}天</view>
        </view>
        <text class="hint-line">仅作备注提示，帮助司机与调度安排，不作为结算依据</text>
      </view>
      <view class="field half-wrap">
        <view class="half">
          <text class="label">期望开始日期</text>
          <picker mode="date" :value="expectDate" @change="(e: any) => (expectDate = e.detail.value)">
            <view class="picker-value">{{ expectDate || '选择日期' }}</view>
          </picker>
          <view class="quick-row">
            <view class="quick" @tap="setQuickDate(0)">今天</view>
            <view class="quick" @tap="setQuickDate(1)">明天</view>
            <view class="quick" @tap="setQuickDate(2)">后天</view>
          </view>
          <text class="field-hint">点击日期框可自选任意日期</text>
        </view>
        <view class="half">
          <text class="label">期望开始时间</text>
          <picker mode="time" :value="expectTime" @change="(e: any) => (expectTime = e.detail.value)">
            <view class="picker-value">{{ expectTime || '选择时间' }}</view>
          </picker>
          <view class="quick-row">
            <view class="quick" @tap="expectTime = '07:00'">上午 7:00</view>
            <view class="quick" @tap="expectTime = '13:00'">下午 1:00</view>
          </view>
          <text class="field-hint">点击时间框可自选任意时间</text>
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

    <RoleTabbar role="customer" current="pages/customer/demand-create" />

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
  overflow: hidden;
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
.quick-row {
  display: flex;
  gap: 8px;
}
.quick {
  flex: 1;
  min-height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  font-size: 13px;
  color: var(--txt);
  background: var(--bg);
}
.quick:active {
  border-color: var(--brand);
  color: var(--brand);
  background: var(--brand-weak);
}
.field-hint {
  font-size: 11px;
  color: var(--sub);
}
.tonnage-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}
.tonnage-chip {
  min-width: 52px;
  min-height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  font-size: 13px;
  color: var(--txt);
  background: var(--bg);
}
.tonnage-chip.active {
  border-color: var(--brand);
  color: var(--brand);
  background: var(--brand-weak);
  font-weight: 600;
}
.hint-line {
  font-size: 12px;
  color: var(--sub);
  margin-top: 6px;
}
.desc-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.desc-chip {
  padding: 6px 16px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  font-size: 13px;
  color: var(--txt);
  background: var(--bg);
}
.desc-chip.active {
  border-color: var(--brand);
  color: var(--brand);
  background: var(--brand-weak);
  font-weight: 600;
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
