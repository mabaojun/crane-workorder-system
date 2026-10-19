<script setup lang="ts">
// 管理员新建工单：选择客户/司机/车辆，地图或常用地点取点后直接下发
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import dayjs from 'dayjs'
import MapPicker from '../../components/MapPicker.vue'
import type { MapPickResult } from '../../components/MapPicker.vue'
import { createWorkOrder } from '@crane/shared/api/workorder'
import {
  pageCustomers,
  listContacts,
  type Customer,
  type CustomerContact
} from '@crane/shared/api/customer'
import {
  pageStaffs,
  pageVehicles,
  vehicleSpecLabel,
  type Staff,
  type Vehicle
} from '@crane/shared/api/staff'
import { listCommonPlaces, type CommonPlace } from '@crane/shared/api/place'
import { VEHICLE_STATUS_LABEL, VEHICLE_TYPE_OPTIONS, isCraneType } from '@crane/shared'
import { DATE_QUICKS, TIME_PRESETS, TONNAGE_PRESETS, dateByOffset } from '../../utils/order-presets'
import { toastError, toastOk } from '../../utils'

const loading = ref(false)
const submitting = ref(false)
const errors = ref('')

const customers = ref<Customer[]>([])
const staffList = ref<Staff[]>([])
const vehicleList = ref<Vehicle[]>([])
const places = ref<CommonPlace[]>([])

const customerIndex = ref(-1)
const staffIndex = ref(-1)
const vehicleIndex = ref(-1)

// 企业客户联系人：选定公司后可再选具体联系人，随工单带给司机
const contacts = ref<CustomerContact[]>([])
const contactIndex = ref(-1)
const contactsLoading = ref(false)

const address = ref('')
const coords = ref<{ longitude: number; latitude: number } | null>(null)
const showMap = ref(false)

const planDate = ref(dayjs().add(1, 'day').format('YYYY-MM-DD'))
const planTime = ref('08:00')
/** 用车种类：直建时先选种类，再按种类筛选车辆 */
const vehicleType = ref('CRANE')
const isCraneVehicle = computed(() => isCraneType(vehicleType.value))
const craneTonnage = ref('')
const cargoName = ref('')
const cargoWeight = ref('')
const remark = ref('')

const customerLabels = computed(() => customers.value.map((c) => `${c.name} · ${c.phone}`))
const selectedCustomer = computed(() => customers.value[customerIndex.value])
const contactLabels = computed(() =>
  contacts.value.map((c) => `${c.name} · ${c.phone}${c.isDefault === 1 ? ' · 默认' : ''}`)
)
const staffLabels = computed(() => staffList.value.map((s) => `${s.name} · ${s.phone}`))
/** 按用车种类筛选可选车辆 */
const filteredVehicles = computed(() =>
  vehicleList.value.filter((v) => (v.type || 'CRANE') === vehicleType.value)
)
const vehicleLabels = computed(() =>
  filteredVehicles.value.map(
    (v) => `${v.plateNo} · ${vehicleSpecLabel(v)} · ${VEHICLE_STATUS_LABEL[v.status] || v.status}`
  )
)
const placeLabels = computed(() => places.value.map((p) => p.placeName))

interface VoiceOrderPrefill {
  customerId?: number | null
  customerName?: string | null
  staffId?: number | null
  staffName?: string | null
  vehicleId?: number | null
  plateNo?: string | null
  address?: string | null
  longitude?: number | null
  latitude?: number | null
  planStartAt?: string | null
  craneTonnage?: number | null
  cargoWeight?: number | null
  cargoName?: string | null
  remark?: string | null
}

async function loadOptions() {
  loading.value = true
  errors.value = ''
  try {
    const [customerPage, staffPage, vehiclePage, placeList] = await Promise.all([
      pageCustomers({ status: 'ACTIVE', pageNum: 1, pageSize: 100 }),
      pageStaffs({ pageNum: 1, pageSize: 100 }),
      pageVehicles({ pageNum: 1, pageSize: 100 }),
      listCommonPlaces()
    ])
    customers.value = customerPage.list ?? []
    staffList.value = (staffPage.list ?? []).filter(
      (s) => s.position === 'DRIVER' && !['RESIGNED', 'AWAY'].includes(s.status)
    )
    vehicleList.value = (vehiclePage.list ?? []).filter(
      (v) => !['MAINTENANCE', 'DISABLED'].includes(v.status)
    )
    places.value = placeList ?? []
  } catch (e) {
    errors.value = e instanceof Error ? e.message : '基础数据加载失败'
  } finally {
    loading.value = false
  }
}

function onCustomerChange(e: { detail: { value: string } }) {
  customerIndex.value = Number(e.detail.value)
  loadContacts()
}
/** 加载所选企业客户下的联系人，默认选中标记为默认的联系人 */
async function loadContacts() {
  contacts.value = []
  contactIndex.value = -1
  const customer = selectedCustomer.value
  if (!customer || customer.type !== 'COMPANY') return
  contactsLoading.value = true
  try {
    contacts.value = await listContacts(customer.id)
    const defaultIdx = contacts.value.findIndex((c) => c.isDefault === 1)
    contactIndex.value = defaultIdx >= 0 ? defaultIdx : contacts.value.length ? 0 : -1
  } catch {
    contacts.value = []
  } finally {
    contactsLoading.value = false
  }
}
function onContactChange(e: { detail: { value: string } }) {
  contactIndex.value = Number(e.detail.value)
}
function onStaffChange(e: { detail: { value: string } }) {
  staffIndex.value = Number(e.detail.value)
}
function onVehicleChange(e: { detail: { value: string } }) {
  vehicleIndex.value = Number(e.detail.value)
}
/** 切换用车种类后清空已选车辆 */
function onVehicleTypeChange(value: string) {
  vehicleType.value = value
  vehicleIndex.value = -1
}
function onDateChange(e: { detail: { value: string } }) {
  planDate.value = e.detail.value
}
function onTimeChange(e: { detail: { value: string } }) {
  planTime.value = e.detail.value
}

/** 吨位快捷档位：点选填入，再点取消 */
function toggleTonnage(value: number) {
  craneTonnage.value = craneTonnage.value === String(value) ? '' : String(value)
}
function onPlaceChange(e: { detail: { value: string } }) {
  const place = places.value[Number(e.detail.value)]
  if (!place) return
  address.value = place.placeName
  if (place.longitude != null && place.latitude != null) {
    coords.value = { longitude: place.longitude, latitude: place.latitude }
  }
}

function onPicked(p: MapPickResult) {
  coords.value = { longitude: p.lng, latitude: p.lat }
  address.value = p.address || `自定义点位（${p.lng.toFixed(6)}, ${p.lat.toFixed(6)}）`
  showMap.value = false
  toastOk('已选择点位')
}

/** 应用一句话下工单解析结果（仅消费一次） */
function applyVoicePrefill() {
  const prefill = uni.getStorageSync('voice_order_prefill') as VoiceOrderPrefill | ''
  if (!prefill) return
  uni.removeStorageSync('voice_order_prefill')

  let idx = prefill.customerId != null
    ? customers.value.findIndex((c) => c.id === prefill.customerId)
    : -1
  if (idx < 0 && prefill.customerName) {
    idx = customers.value.findIndex((c) => c.name === prefill.customerName)
  }
  if (idx >= 0) customerIndex.value = idx
  // 语音回填客户后同步带出企业联系人，默认选中默认联系人
  if (idx >= 0) loadContacts()

  idx = prefill.staffId != null ? staffList.value.findIndex((s) => s.id === prefill.staffId) : -1
  if (idx < 0 && prefill.staffName) {
    idx = staffList.value.findIndex((s) => s.name === prefill.staffName)
  }
  if (idx >= 0) staffIndex.value = idx

  const matchedVehicle = prefill.vehicleId != null
    ? vehicleList.value.find((v) => v.id === prefill.vehicleId)
    : prefill.plateNo
      ? vehicleList.value.find((v) => v.plateNo === prefill.plateNo)
      : undefined
  if (matchedVehicle) {
    // 语音结果先同步用车种类，再在对应种类列表里定位车辆
    vehicleType.value = matchedVehicle.type || 'CRANE'
    const idx = filteredVehicles.value.findIndex((v) => v.id === matchedVehicle.id)
    if (idx >= 0) vehicleIndex.value = idx
  }

  if (prefill.address) address.value = prefill.address
  if (prefill.longitude != null && prefill.latitude != null) {
    coords.value = { longitude: prefill.longitude, latitude: prefill.latitude }
  }
  if (prefill.planStartAt) {
    const d = dayjs(prefill.planStartAt)
    if (d.isValid()) {
      planDate.value = d.format('YYYY-MM-DD')
      planTime.value = d.format('HH:mm')
    }
  }
  if (prefill.craneTonnage != null) craneTonnage.value = String(prefill.craneTonnage)
  if (prefill.cargoWeight != null) cargoWeight.value = String(prefill.cargoWeight)
  if (prefill.cargoName) cargoName.value = prefill.cargoName
  if (prefill.remark) remark.value = prefill.remark
  toastOk('已按语音解析结果填充，请核对')
}

async function submit() {
  if (submitting.value) return
  const customer = customers.value[customerIndex.value]
  const contact = contacts.value[contactIndex.value]
  const staff = staffList.value[staffIndex.value]
  const vehicle = filteredVehicles.value[vehicleIndex.value]
  if (!customer) {
    uni.showToast({ title: '请选择客户', icon: 'none' })
    return
  }
  if (!staff) {
    uni.showToast({ title: '请选择执行司机', icon: 'none' })
    return
  }
  if (!vehicle) {
    uni.showToast({ title: '请选择施工车辆', icon: 'none' })
    return
  }
  if (!address.value.trim()) {
    uni.showToast({ title: '请填写施工地址或地图选点', icon: 'none' })
    return
  }
  if (!coords.value) {
    uni.showToast({ title: '请通过常用地点或地图选点获取经纬度', icon: 'none' })
    return
  }
  const tonnage = Number(craneTonnage.value)
  if (isCraneVehicle.value && (!tonnage || tonnage <= 0)) {
    uni.showToast({ title: '请填写吊车吨位', icon: 'none' })
    return
  }
  const planStartAt = `${planDate.value}T${planTime.value}:00`
  if (dayjs(planStartAt).isBefore(dayjs())) {
    uni.showToast({ title: '计划开始时间不能早于当前时间', icon: 'none' })
    return
  }
  submitting.value = true
  try {
    uni.showLoading({ title: '建单中' })
    const res = await createWorkOrder({
      source: 'DIRECT',
      customerId: customer.id,
      contactId: contact?.id,
      staffId: staff.id,
      vehicleId: vehicle.id,
      vehicleType: vehicleType.value,
      planStartAt,
      address: address.value.trim(),
      longitude: coords.value.longitude,
      latitude: coords.value.latitude,
      cargoName: cargoName.value.trim() || undefined,
      cargoWeightTon: cargoWeight.value ? Number(cargoWeight.value) : undefined,
      craneTonnage: isCraneVehicle.value ? tonnage : undefined,
      remark: remark.value.trim() || undefined
    })
    uni.hideLoading()
    toastOk('工单已创建并下发')
    setTimeout(() => {
      uni.redirectTo({ url: `/pages/admin/order-detail?id=${res.orderId}` })
    }, 500)
  } catch (e) {
    uni.hideLoading()
    toastError(e)
  } finally {
    submitting.value = false
  }
}

onShow(async () => {
  await loadOptions()
  applyVoicePrefill()
})

/** 语音说单：作为新建工单的辅助录入方式，解析后仍回到本表单核对下发 */
function goVoice() {
  uni.navigateTo({ url: '/pages/admin/voice-create' })
}
</script>

<template>
  <view class="adm-page">
    <view class="adm-hero">
      <text class="adm-hero-kicker">NEW WORK ORDER</text>
      <text class="adm-hero-title">新建工单</text>
      <text class="adm-hero-sub">选择客户、司机与车辆，取点后直接下发到司机端</text>
    </view>

    <!-- 语音说单：辅助录入入口，弱化展示 -->
    <view class="voice-shortcut" @tap="goVoice">
      <uni-icons type="mic" size="18" color="#0e7490" />
      <view class="voice-shortcut-copy">
        <text class="voice-shortcut-title">一句话语音下单</text>
        <text class="voice-shortcut-desc">说出客户、时间、地址，自动拆解回填本表单</text>
      </view>
      <uni-icons type="right" size="16" color="#94a3b8" />
    </view>

    <view v-if="errors" class="adm-error">{{ errors }}</view>

    <view class="adm-section">
      <view class="adm-form">
        <view class="adm-field">
          <text class="adm-label">客户</text>
          <picker mode="selector" :range="customerLabels" :value="customerIndex" @change="onCustomerChange">
            <view class="adm-picker">
              <text :class="{ placeholder: customerIndex < 0 }">
                {{ customerIndex >= 0 ? customerLabels[customerIndex] : '请选择客户' }}
              </text>
              <view class="arrow" />
            </view>
          </picker>
        </view>

        <!-- 企业客户：可再选该公司下具体联系人，随工单带给司机 -->
        <view v-if="selectedCustomer?.type === 'COMPANY'" class="adm-field">
          <text class="adm-label">客户联系人</text>
          <picker
            v-if="contacts.length"
            mode="selector"
            :range="contactLabels"
            :value="contactIndex"
            @change="onContactChange"
          >
            <view class="adm-picker">
              <text :class="{ placeholder: contactIndex < 0 }">
                {{ contactIndex >= 0 ? contactLabels[contactIndex] : '请选择联系人' }}
              </text>
              <view class="arrow" />
            </view>
          </picker>
          <view v-else class="contact-hint">
            {{ contactsLoading ? '联系人加载中…' : '该公司暂无联系人，司机将看到客户默认电话；可在后台客户档案中补充联系人' }}
          </view>
        </view>

        <view class="adm-field">
          <text class="adm-label">计划日期</text>
          <picker mode="date" :value="planDate" @change="onDateChange">
            <view class="adm-picker"><text>{{ planDate }}</text><view class="arrow" /></view>
          </picker>
          <view class="adm-chip-row">
            <view
              v-for="q in DATE_QUICKS"
              :key="q.label"
              class="adm-chip"
              :class="{ active: planDate === dateByOffset(q.offset) }"
              @tap="planDate = dateByOffset(q.offset)"
            >{{ q.label }}</view>
          </view>
        </view>

        <view class="adm-field">
          <text class="adm-label">计划时间</text>
          <picker mode="time" :value="planTime" @change="onTimeChange">
            <view class="adm-picker"><text>{{ planTime }}</text><view class="arrow" /></view>
          </picker>
          <view class="adm-chip-grid">
            <view
              v-for="t in TIME_PRESETS"
              :key="t"
              class="adm-chip"
              :class="{ active: planTime === t }"
              @tap="planTime = t"
            >{{ t }}</view>
          </view>
        </view>

        <view class="adm-field">
          <text class="adm-label">常用施工地点（选填）</text>
          <picker mode="selector" :range="placeLabels" @change="onPlaceChange">
            <view class="adm-picker">
              <text :class="{ placeholder: !address }">{{ address || '选择常用地点或手动填写' }}</text>
              <view class="arrow" />
            </view>
          </picker>
        </view>

        <view class="adm-field">
          <text class="adm-label">施工地址</text>
          <input v-model="address" class="adm-input" placeholder="请输入详细地址" />
        </view>

        <view class="adm-btn-row">
          <button class="adm-btn ghost" @tap="showMap = true">地图选点</button>
          <view class="coord-status">
            {{ coords ? `${coords.longitude.toFixed(5)}, ${coords.latitude.toFixed(5)}` : '尚未获取经纬度' }}
          </view>
        </view>

        <view class="adm-field">
          <text class="adm-label">用车种类</text>
          <view class="adm-chip-grid type-grid">
            <view
              v-for="opt in VEHICLE_TYPE_OPTIONS"
              :key="opt.value"
              class="adm-chip"
              :class="{ active: vehicleType === opt.value }"
              @tap="onVehicleTypeChange(opt.value)"
            >{{ opt.label }}</view>
          </view>
        </view>

        <view v-if="isCraneVehicle" class="adm-field">
          <text class="adm-label">吊车吨位（吨）</text>
          <input v-model="craneTonnage" class="adm-input" type="digit" placeholder="点选下方常用吨位，或直接输入" />
          <view class="adm-chip-grid">
            <view
              v-for="t in TONNAGE_PRESETS"
              :key="t"
              class="adm-chip"
              :class="{ active: craneTonnage === String(t) }"
              @tap="toggleTonnage(t)"
            >{{ t }}</view>
          </view>
        </view>

        <view class="adm-inline">
          <view class="adm-field">
            <text class="adm-label">执行司机</text>
            <picker mode="selector" :range="staffLabels" :value="staffIndex" @change="onStaffChange">
              <view class="adm-picker">
                <text :class="{ placeholder: staffIndex < 0 }">
                  {{ staffIndex >= 0 ? staffLabels[staffIndex] : '请选择司机' }}
                </text>
                <view class="arrow" />
              </view>
            </picker>
          </view>
          <view class="adm-field">
            <text class="adm-label">施工车辆</text>
            <picker mode="selector" :range="vehicleLabels" :value="vehicleIndex" @change="onVehicleChange">
              <view class="adm-picker">
                <text :class="{ placeholder: vehicleIndex < 0 }">
                  {{ vehicleIndex >= 0 ? vehicleLabels[vehicleIndex] : '请选择车辆' }}
                </text>
                <view class="arrow" />
              </view>
            </picker>
          </view>
        </view>

        <view class="adm-field">
          <text class="adm-label">货物重量（吨）</text>
          <input v-model="cargoWeight" class="adm-input" type="digit" placeholder="选填" />
        </view>

        <view class="adm-field">
          <text class="adm-label">货物名称</text>
          <input v-model="cargoName" class="adm-input" placeholder="选填" />
        </view>

        <view class="adm-field">
          <text class="adm-label">任务备注</text>
          <textarea v-model="remark" class="adm-textarea" :maxlength="500" placeholder="选填" />
        </view>

        <button class="adm-btn primary" :disabled="submitting || loading" @tap="submit">
          {{ submitting ? '创建中...' : '创建并下发' }}
        </button>
      </view>
    </view>

    <MapPicker
      v-if="showMap"
      :initial-lng="coords?.longitude"
      :initial-lat="coords?.latitude"
      @confirm="onPicked"
      @close="showMap = false"
    />
  </view>
</template>

<style scoped>
.type-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}
.coord-status {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 46px;
  border-radius: 12px;
  font-size: 12px;
  color: #64748b;
  background: #f8fafc;
  border: 1px dashed #cbd5e1;
}
/* 语音说单辅助入口：低视觉权重 */
.voice-shortcut {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  border-radius: 12px;
  background: #fff;
  border: 1px dashed #cbd5e1;
}
.voice-shortcut-copy {
  flex: 1;
  min-width: 0;
}
.voice-shortcut-title {
  display: block;
  font-size: 14px;
  font-weight: 600;
  color: #0f172a;
}
.voice-shortcut-desc {
  display: block;
  margin-top: 1px;
  font-size: 11px;
  color: #94a3b8;
}
.contact-hint {
  font-size: 12px;
  line-height: 1.5;
  color: #94a3b8;
}
</style>
