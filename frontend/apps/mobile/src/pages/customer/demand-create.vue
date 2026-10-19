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
import { chooseImages, getLocation, toastError, toastOk, uploadLocalFile } from '../../utils'
import { DATE_QUICKS, DAYS_PRESETS, TONNAGE_PRESETS, dateByOffset } from '../../utils/order-presets'
import { VEHICLE_TYPE_OPTIONS, isCraneType } from '@crane/shared'

const auth = useAuthStore()

const contactName = ref('')
const contactPhone = ref('')
const address = ref('')
const coords = ref<{ longitude: number; latitude: number } | null>(null)
const locating = ref(false)
const description = ref('')
/** 用车种类：客户必选，默认吊车 */
const vehicleType = ref('CRANE')
const isCraneVehicle = computed(() => isCraneType(vehicleType.value))
const cargoWeight = ref('')
const craneTonnage = ref('')
const estimatedDays = ref('')
const expectDate = ref('')
const expectTime = ref('')
/** 期望开始时间预选项：7 点 / 7 点半 / 13 点 / 13 点半 */
const TIME_PRESETS = ['07:00', '07:30', '13:00', '13:30']
const photos = ref<string[]>([])
const parentDemandId = ref<number | undefined>(undefined)
/** 结算主体：企业联系人默认按所属企业结算，可手动切换为个人用车 */
const settlementType = ref<'ENTERPRISE' | 'PERSONAL'>('ENTERPRISE')
/** 仅企业客户展示结算主体切换（个人客户只能个人结算） */
const isCompanyCustomer = computed(() => auth.me?.customerType === 'COMPANY')

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
  expectDate.value = dateByOffset(offset)
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
    settlementType.value = d.settlementType === 'PERSONAL' ? 'PERSONAL' : 'ENTERPRISE'
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
    // 现场照片先上传到文件服务，提交需求单时只携带可鉴权下载的地址
    let imageUrls: string[] | undefined
    if (photos.value.length) {
      uni.showLoading({ title: '上传照片中' })
      imageUrls = await Promise.all(photos.value.map(uploadLocalFile))
    }
    uni.showLoading({ title: '提交中' })
    const expectedStartAt = expectDate.value && expectTime.value ? `${expectDate.value}T${expectTime.value}:00` : undefined
    await createDemandOrder({
      source: 'CUSTOMER_MP',
      contactName: contactName.value.trim(),
      contactPhone: contactPhone.value.trim(),
      address: submitAddress,
      longitude: submitLng,
      latitude: submitLat,
      description: description.value.trim() || undefined,
      imageUrls,
      vehicleType: vehicleType.value,
      cargoWeight: cargoWeight.value ? Number(cargoWeight.value) : undefined,
      // 设备吨位：仅吊车类型需要，点选常用档位或直接输入
      craneTonnage: isCraneVehicle.value && craneTonnage.value ? Number(craneTonnage.value) : undefined,
      // 预计使用时长：点选常用档位或直接输入，仅备注提示不作换算
      estimatedDays: estimatedDays.value ? Number(estimatedDays.value) : undefined,
      // 企业联系人默认按所属企业结算，可切换个人用车；个人客户由后端强制个人主体
      settlementType: isCompanyCustomer.value ? settlementType.value : undefined,
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
        <textarea v-model="description" class="textarea" placeholder="用车任务内容、现场条件等" :maxlength="500" />
      </view>
      <view class="field">
        <text class="label">用车种类</text>
        <view class="type-chips">
          <view
            v-for="opt in VEHICLE_TYPE_OPTIONS"
            :key="opt.value"
            class="type-chip"
            :class="{ active: vehicleType === opt.value }"
            @tap="vehicleType = opt.value"
          >{{ opt.label }}</view>
        </view>
      </view>
      <view class="field">
        <text class="label">货物重量(吨)</text>
        <input v-model="cargoWeight" class="input" type="digit" placeholder="选填，如 8" placeholder-class="ph" />
      </view>
      <view v-if="isCraneVehicle" class="field">
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
            <view
              v-for="q in DATE_QUICKS"
              :key="q.label"
              class="quick"
              :class="{ active: expectDate === dateByOffset(q.offset) }"
              @tap="setQuickDate(q.offset)"
            >{{ q.label }}</view>
          </view>
          <text class="field-hint">点击日期框可自选任意日期</text>
        </view>
        <view class="half">
          <text class="label">期望开始时间</text>
          <picker mode="time" :value="expectTime" @change="(e: any) => (expectTime = e.detail.value)">
            <view class="picker-value">{{ expectTime || '选择时间' }}</view>
          </picker>
          <view class="time-chips">
            <view
              v-for="t in TIME_PRESETS"
              :key="t"
              class="time-chip"
              :class="{ active: expectTime === t }"
              @tap="expectTime = t"
            >{{ t }}</view>
          </view>
          <text class="field-hint">点击时间框可自选任意时间</text>
        </view>
      </view>
      <view v-if="isCompanyCustomer" class="field col">
        <text class="label">结算主体</text>
        <view class="desc-chips">
          <view
            class="desc-chip"
            :class="{ active: settlementType === 'ENTERPRISE' }"
            @tap="settlementType = 'ENTERPRISE'"
          >企业主体{{ auth.me?.customerName ? `（${auth.me.customerName}）` : '' }}</view>
          <view
            class="desc-chip"
            :class="{ active: settlementType === 'PERSONAL' }"
            @tap="settlementType = 'PERSONAL'"
          >个人用车（个人结算）</view>
        </view>
        <text class="hint-line">默认归属所属企业结算；切换为个人用车后以您个人名义结算</text>
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
  padding: 16px 16px calc(100px + env(safe-area-inset-bottom));
  box-sizing: border-box;
}
.form {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.field {
  background: var(--panel);
  border: 1px solid var(--line);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow);
  padding: 12px 16px;
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 16px;
}
.field.col {
  flex-direction: column;
  align-items: stretch;
  gap: 8px;
}
.label {
  flex: none;
  color: var(--sub);
  font-size: 14px;
  width: 112px;
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
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow);
  padding: 12px 16px;
}
.half .label {
  width: auto;
}
.input {
  flex: 1;
  font-size: 16px;
  min-height: 32px;
}
.ph {
  color: var(--sub);
}
.textarea {
  width: 100%;
  box-sizing: border-box;
  min-height: 96px;
  padding: 10px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  background: #f8fafc;
  font-size: 15px;
}
.locate-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.place-list {
  background: var(--bg);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  overflow: hidden;
}
.place-item {
  min-height: 48px;
  display: flex;
  align-items: center;
  padding: 0 12px;
  font-size: 15px;
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
  min-height: 36px;
  display: inline-flex;
  align-items: center;
  padding: 0 12px;
  border: 1px solid var(--line);
  border-radius: 999px;
  font-size: 14px;
  font-weight: 600;
  color: var(--brand);
  background: #fff;
}
.located {
  font-size: 13px;
  color: var(--success);
}
.quick-row {
  display: flex;
  gap: 8px;
}
.quick {
  flex: 1;
  min-height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  font-size: 14px;
  color: var(--txt);
  background: var(--bg);
}
.quick:active {
  border-color: var(--brand);
  color: var(--brand);
  background: var(--brand-weak);
}
.quick.active {
  border-color: var(--brand);
  color: var(--brand);
  background: var(--brand-weak);
  font-weight: 600;
}
.time-chips {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 6px;
  margin-top: 8px;
}
.time-chip {
  min-height: 38px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  font-size: 13px;
  color: var(--txt);
  background: var(--bg);
  font-variant-numeric: tabular-nums;
}
.time-chip.active {
  border-color: var(--brand);
  color: var(--brand);
  background: var(--brand-weak);
  font-weight: 600;
}
.field-hint {
  font-size: 12px;
  color: var(--sub);
}
.tonnage-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}
.tonnage-chip {
  min-width: 56px;
  min-height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  font-size: 14px;
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
  font-size: 13px;
  color: var(--sub);
  margin-top: 6px;
}
.desc-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.desc-chip {
  min-height: 40px;
  display: inline-flex;
  align-items: center;
  padding: 6px 18px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  font-size: 14px;
  color: var(--txt);
  background: var(--bg);
}
.desc-chip.active {
  border-color: var(--brand);
  color: var(--brand);
  background: var(--brand-weak);
  font-weight: 600;
}
.type-chips {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}
.type-chip {
  min-height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 6px 10px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  font-size: 14px;
  color: var(--txt);
  background: var(--bg);
}
.type-chip.active {
  border-color: var(--brand);
  color: var(--brand);
  background: var(--brand-weak);
  font-weight: 600;
}
.picker-value {
  font-size: 16px;
  color: var(--txt);
  min-height: 32px;
  display: flex;
  align-items: center;
}
.photo-row {
  display: flex;
  gap: 10px;
}
.photo {
  width: 72px;
  height: 72px;
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
  font-size: 14px;
}
.submit {
  margin-top: 8px;
  height: 52px;
  line-height: 52px;
  border-radius: var(--radius);
  background: linear-gradient(135deg, #1a6bff, #0b4fd6);
  color: #fff;
  font-size: 18px;
  font-weight: 700;
  box-shadow: 0 6px 16px rgba(26, 107, 255, 0.22);
}
.submit::after {
  border: none;
}
</style>
