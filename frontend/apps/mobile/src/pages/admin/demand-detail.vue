<script setup lang="ts">
// 管理员需求详情：查看提报信息，驳回或转工单并下发
import { computed, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import dayjs from 'dayjs'
import StatusTag from '../../components/StatusTag.vue'
import BottomSheet from '../../components/BottomSheet.vue'
import {
  convertDemand,
  getDemand,
  reviewDemand,
  type DemandOrder
} from '@crane/shared/api/demand'
import {
  pageStaffs,
  pageVehicles,
  vehicleSpecLabel,
  type Staff,
  type Vehicle
} from '@crane/shared/api/staff'
import { VEHICLE_STATUS_LABEL, VEHICLE_TYPE_OPTIONS, VEHICLE_TYPE_LABEL, isCraneType } from '@crane/shared'
import { listContacts, type CustomerContact } from '@crane/shared/api/customer'
import {
  fmtDateTime,
  isImageAttachment,
  loadAttachmentPreviewUrl,
  toastError,
  toastOk
} from '../../utils'

const demandId = ref('')
const demand = ref<DemandOrder | null>(null)
const loading = ref(true)
const submitting = ref(false)

// 客户提报图片附件：解析 imageUrls JSON，点击弹窗预览
interface DemandImage {
  url: string
  fileName: string
}
const images = computed<DemandImage[]>(() => parseImages(demand.value?.imageUrls))
const previewVisible = ref(false)
const previewLoading = ref(false)
const previewUrl = ref('')

function parseImages(raw?: string | null): DemandImage[] {
  if (!raw) return []
  let list: unknown
  try {
    list = JSON.parse(raw)
  } catch {
    return []
  }
  if (!Array.isArray(list)) return []
  return list
    .filter((url): url is string => typeof url === 'string' && !!url)
    .map((url) => ({ url, fileName: fileNameFromUrl(url) }))
}

function fileNameFromUrl(url: string): string {
  const path = url.split('?')[0]
  const name = path.substring(path.lastIndexOf('/') + 1)
  try {
    return decodeURIComponent(name) || '图片'
  } catch {
    return name || '图片'
  }
}

/** 点击附件后弹窗预览图片 */
async function onPreviewImage(image: DemandImage) {
  if (previewLoading.value) return
  if (!isImageAttachment(image.fileName || image.url)) {
    uni.showToast({ title: '该附件暂不支持预览', icon: 'none' })
    return
  }
  previewLoading.value = true
  try {
    previewUrl.value = await loadAttachmentPreviewUrl(image.url)
    previewVisible.value = true
  } catch (e) {
    toastError(e)
  } finally {
    previewLoading.value = false
  }
}

function closePreview() {
  previewVisible.value = false
  previewUrl.value = ''
}

const showReject = ref(false)
const reviewRemark = ref('')

const showConvert = ref(false)
const staffList = ref<Staff[]>([])
const vehicleList = ref<Vehicle[]>([])
const staffIndex = ref(-1)
const vehicleIndex = ref(-1)
// 企业客户联系人：转工单时可改选，缺省沿用需求单提报人
const contacts = ref<CustomerContact[]>([])
const contactIndex = ref(-1)
const contactsLoading = ref(false)
const planDate = ref('')
const planTime = ref('08:00')
/** 用车种类：默认沿用需求单，可改选并联动车辆列表 */
const vehicleType = ref('CRANE')
const isCraneVehicle = computed(() => isCraneType(vehicleType.value))
const craneTonnage = ref('')
const cargoWeight = ref('')
const estimatedDays = ref('')
const convertRemark = ref('')

const canHandle = computed(
  () => !!demand.value && ['PENDING', 'REVIEWING'].includes(demand.value.status)
)

interface DemandConvertPrefill {
  demandId?: number | null
  staffId?: number | null
  staffName?: string | null
  vehicleId?: number | null
  plateNo?: string | null
  planStartAt?: string | null
  craneTonnage?: number | null
  cargoWeight?: number | null
  remark?: string | null
}

const staffLabels = computed(() =>
  staffList.value.map((s) => `${s.name} · ${s.phone}`)
)
const contactLabels = computed(() =>
  contacts.value.map((c) => `${c.name} · ${c.phone}${c.isDefault === 1 ? ' · 默认' : ''}`)
)
/** 按用车种类筛选可选车辆 */
const filteredVehicles = computed(() =>
  vehicleList.value.filter((v) => (v.type || 'CRANE') === vehicleType.value)
)
const vehicleLabels = computed(() =>
  filteredVehicles.value.map(
    (v) => `${v.plateNo} · ${vehicleSpecLabel(v)} · ${VEHICLE_STATUS_LABEL[v.status] || v.status}`
  )
)

async function load() {
  if (!demandId.value) return
  loading.value = true
  try {
    const d = await getDemand(demandId.value)
    demand.value = d
    initConvertForm(d)
  } catch (e) {
    toastError(e)
    demand.value = null
  } finally {
    loading.value = false
  }
}

function initConvertForm(d: DemandOrder) {
  vehicleType.value = d.vehicleType || 'CRANE'
  craneTonnage.value = d.craneTonnage != null ? String(d.craneTonnage) : ''
  cargoWeight.value = d.cargoWeight != null ? String(d.cargoWeight) : ''
  estimatedDays.value = d.estimatedDays != null ? String(d.estimatedDays) : ''
  convertRemark.value = d.description || ''
  const start = d.expectedStartAt ? dayjs(d.expectedStartAt) : dayjs().add(1, 'day').hour(8).minute(0)
  const safeStart = start.isBefore(dayjs()) ? dayjs().add(1, 'day').hour(8).minute(0) : start
  planDate.value = safeStart.format('YYYY-MM-DD')
  planTime.value = safeStart.format('HH:mm')
}

async function loadOptions() {
  try {
    const [staffPage, vehiclePage] = await Promise.all([
      pageStaffs({ pageNum: 1, pageSize: 100 }),
      pageVehicles({ pageNum: 1, pageSize: 100 })
    ])
    staffList.value = (staffPage.list ?? []).filter(
      (s) => s.position === 'DRIVER' && s.status !== 'RESIGNED'
    )
    vehicleList.value = (vehiclePage.list ?? []).filter((v) => v.status !== 'DISABLED')
    const intended = demand.value?.intendedVehicleId
    if (intended != null) {
      const target = vehicleList.value.find((v) => v.id === intended)
      if (target) {
        vehicleType.value = target.type || vehicleType.value
        const idx = filteredVehicles.value.findIndex((v) => v.id === intended)
        if (idx >= 0) vehicleIndex.value = idx
      }
    }
    await loadContacts()
  } catch (e) {
    toastError(e)
  }
}

/** 加载需求单所属客户的联系人：优先需求单提报人，其次默认联系人，最后第一位 */
async function loadContacts() {
  contacts.value = []
  contactIndex.value = -1
  const customerId = demand.value?.customerId
  if (!customerId) return
  contactsLoading.value = true
  try {
    contacts.value = await listContacts(customerId)
    const reported = demand.value?.contactId != null
      ? contacts.value.findIndex((c) => c.id === demand.value?.contactId)
      : -1
    const defaultIdx = contacts.value.findIndex((c) => c.isDefault === 1)
    contactIndex.value = reported >= 0 ? reported : defaultIdx >= 0 ? defaultIdx : contacts.value.length ? 0 : -1
  } catch {
    contacts.value = []
  } finally {
    contactsLoading.value = false
  }
}

function onContactChange(e: { detail: { value: string } }) {
  contactIndex.value = Number(e.detail.value)
}

function openConvert() {
  if (!staffList.value.length || !vehicleList.value.length) {
    loadOptions().then(() => {
      showConvert.value = true
    })
    return
  }
  showConvert.value = true
}

/** 应用一句话下工单解析结果（仅消费一次） */
function applyVoicePrefill() {
  const prefill = uni.getStorageSync('demand_convert_prefill') as DemandConvertPrefill | ''
  if (!prefill) return
  uni.removeStorageSync('demand_convert_prefill')
  if (!demand.value || prefill.demandId !== demand.value.id) return

  let idx = prefill.staffId != null
    ? staffList.value.findIndex((s) => s.id === prefill.staffId)
    : -1
  if (idx < 0 && prefill.staffName) {
    idx = staffList.value.findIndex((s) => s.name === prefill.staffName)
  }
  if (idx >= 0) staffIndex.value = idx

  idx = prefill.vehicleId != null
    ? vehicleList.value.findIndex((v) => v.id === prefill.vehicleId)
    : -1
  if (idx < 0 && prefill.plateNo) {
    idx = vehicleList.value.findIndex((v) => v.plateNo === prefill.plateNo)
  }
  if (idx >= 0) {
    const target = vehicleList.value[idx]
    vehicleType.value = target.type || vehicleType.value
    const filteredIdx = filteredVehicles.value.findIndex((v) => v.id === target.id)
    if (filteredIdx >= 0) vehicleIndex.value = filteredIdx
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
  if (prefill.remark) convertRemark.value = prefill.remark
  showConvert.value = true
  toastOk('已按语音解析结果填充，请核对')
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

async function submitReject() {
  if (!demand.value || submitting.value) return
  if (!reviewRemark.value.trim()) {
    uni.showToast({ title: '请填写驳回原因', icon: 'none' })
    return
  }
  submitting.value = true
  try {
    await reviewDemand(demand.value.id, {
      approve: false,
      reviewRemark: reviewRemark.value.trim()
    })
    toastOk('已驳回')
    showReject.value = false
    await load()
  } catch (e) {
    toastError(e)
  } finally {
    submitting.value = false
  }
}

async function submitConvert() {
  if (!demand.value || submitting.value) return
  const staff = staffList.value[staffIndex.value]
  const vehicle = filteredVehicles.value[vehicleIndex.value]
  if (!staff) {
    uni.showToast({ title: '请选择执行司机', icon: 'none' })
    return
  }
  if (!vehicle) {
    uni.showToast({ title: '请选择施工车辆', icon: 'none' })
    return
  }
  if (!planDate.value || !planTime.value) {
    uni.showToast({ title: '请选择计划开始时间', icon: 'none' })
    return
  }
  const planStartAt = `${planDate.value}T${planTime.value}:00`
  if (dayjs(planStartAt).isBefore(dayjs())) {
    uni.showToast({ title: '计划开始时间不能早于当前时间', icon: 'none' })
    return
  }
  const tonnage = Number(craneTonnage.value)
  if (isCraneVehicle.value && (!tonnage || tonnage <= 0)) {
    uni.showToast({ title: '请填写吊车吨位', icon: 'none' })
    return
  }
  submitting.value = true
  try {
    uni.showLoading({ title: '转工单中' })
    const res = await convertDemand(demand.value.id, {
      staffId: staff.id,
      vehicleId: vehicle.id,
      vehicleType: vehicleType.value,
      contactId: contacts.value[contactIndex.value]?.id,
      planStartAt,
      craneTonnage: isCraneVehicle.value ? tonnage : undefined,
      cargoWeightTon: cargoWeight.value ? Number(cargoWeight.value) : undefined,
      estimatedDays: estimatedDays.value ? Number(estimatedDays.value) : undefined,
      remark: convertRemark.value.trim() || demand.value.description || '需求单转工单'
    })
    uni.hideLoading()
    toastOk('已转工单并下发')
    showConvert.value = false
    if (res.convertedOrderId) {
      setTimeout(() => {
        uni.redirectTo({ url: `/pages/admin/order-detail?id=${res.convertedOrderId}` })
      }, 500)
    } else {
      await load()
    }
  } catch (e) {
    uni.hideLoading()
    toastError(e)
  } finally {
    submitting.value = false
  }
}

onLoad((opts) => {
  demandId.value = opts?.id ?? ''
})

onShow(async () => {
  await load()
  await loadOptions()
  applyVoicePrefill()
})
</script>

<template>
  <view class="adm-page">
    <view v-if="loading" class="adm-loading">加载中...</view>
    <view v-else-if="!demand" class="adm-empty">需求单不存在或无权查看</view>

    <template v-else>
      <view class="adm-hero">
        <text class="adm-hero-kicker">DEMAND DETAIL</text>
        <view class="title-row">
          <text class="adm-hero-title">{{ demand.demandNo }}</text>
          <StatusTag :status="demand.status" />
        </view>
        <text class="adm-hero-sub">{{ demand.contactName || '客户' }} · {{ fmtDateTime(demand.expectedStartAt) }}</text>
      </view>

      <view class="adm-section">
        <view class="adm-section-head">
          <text class="adm-section-title">提报信息</text>
        </view>
        <view class="adm-kv"><text class="k">联系人</text><text class="v strong">{{ demand.contactName || '-' }}</text></view>
        <view class="adm-kv"><text class="k">联系电话</text><text class="v">{{ demand.contactPhone || '-' }}</text></view>
        <view class="adm-kv"><text class="k">施工地址</text><text class="v">{{ demand.address || '-' }}</text></view>
        <view class="adm-kv"><text class="k">期望开始</text><text class="v">{{ fmtDateTime(demand.expectedStartAt) }}</text></view>
        <view class="adm-kv"><text class="k">用车种类</text><text class="v strong">{{ VEHICLE_TYPE_LABEL[demand.vehicleType || 'CRANE'] }}</text></view>
        <view v-if="!demand.vehicleType || demand.vehicleType === 'CRANE'" class="adm-kv"><text class="k">吊车吨位</text><text class="v">{{ demand.craneTonnage != null ? demand.craneTonnage + ' 吨' : '未填' }}</text></view>
        <view class="adm-kv"><text class="k">货物重量</text><text class="v">{{ demand.cargoWeight != null ? demand.cargoWeight + ' 吨' : '未填' }}</text></view>
        <view class="adm-kv"><text class="k">预计使用</text><text class="v">{{ demand.estimatedDays != null ? demand.estimatedDays + ' 天' : '未填' }}</text></view>
        <view class="adm-kv"><text class="k">需求描述</text><text class="v">{{ demand.description || '无' }}</text></view>
        <view v-if="demand.reviewRemark" class="adm-kv">
          <text class="k">复核意见</text>
          <text class="v">{{ demand.reviewRemark }}</text>
        </view>
      </view>

      <view class="adm-section">
        <view class="adm-section-head">
          <text class="adm-section-title">提报图片</text>
          <text v-if="images.length" class="adm-section-link">{{ images.length }} 张</text>
        </view>
        <view v-if="images.length" class="attach-list">
          <view
            v-for="img in images"
            :key="img.url"
            class="attach-row"
            @tap="onPreviewImage(img)"
          >
            <text class="attach-tag">附件</text>
            <text class="attach-name">{{ img.fileName }}</text>
            <text class="attach-action">{{ previewLoading ? '加载中' : '预览' }}</text>
          </view>
        </view>
        <view v-else class="adm-empty">客户未上传图片</view>
      </view>

      <view v-if="demand.status === 'CONVERTED'" class="adm-info">
        该需求已转为工单，可前往工单列表查看执行进度。
      </view>
      <view v-if="demand.status === 'REJECTED'" class="adm-error">
        该需求已驳回{{ demand.reviewRemark ? `：${demand.reviewRemark}` : '' }}
      </view>

      <view v-if="canHandle" class="adm-sticky-actions">
        <button class="adm-btn plain-danger" @tap="showReject = true">驳回</button>
        <button class="adm-btn primary" @tap="openConvert">转工单并下发</button>
      </view>
    </template>

    <BottomSheet v-if="showReject" title="驳回需求" @close="showReject = false">
      <view class="adm-form">
        <view class="adm-field">
          <text class="adm-label">驳回原因（客户可见）</text>
          <textarea
            v-model="reviewRemark"
            class="adm-textarea"
            :maxlength="500"
            placeholder="请说明驳回原因，便于客户修改后重新提报"
          />
        </view>
        <button class="adm-btn primary" :disabled="submitting" @tap="submitReject">
          {{ submitting ? '提交中...' : '确认驳回' }}
        </button>
      </view>
    </BottomSheet>

    <BottomSheet v-if="showConvert" title="转工单并下发" @close="showConvert = false">
      <view class="adm-form">
        <view class="adm-inline">
          <view class="adm-field">
            <text class="adm-label">计划日期</text>
            <picker mode="date" :value="planDate" @change="onDateChange">
              <view class="adm-picker"><text>{{ planDate || '选择日期' }}</text><view class="arrow" /></view>
            </picker>
          </view>
          <view class="adm-field">
            <text class="adm-label">计划时间</text>
            <picker mode="time" :value="planTime" @change="onTimeChange">
              <view class="adm-picker"><text>{{ planTime || '选择时间' }}</text><view class="arrow" /></view>
            </picker>
          </view>
        </view>

        <view class="adm-field">
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
            {{ contactsLoading ? '联系人加载中…' : '该客户暂无联系人，工单沿用需求单提报人信息' }}
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

        <view v-if="isCraneVehicle" class="adm-inline">
          <view class="adm-field">
            <text class="adm-label">吊车吨位（吨）</text>
            <input v-model="craneTonnage" class="adm-input" type="digit" placeholder="如 25" />
          </view>
          <view class="adm-field">
            <text class="adm-label">货物重量（吨）</text>
            <input v-model="cargoWeight" class="adm-input" type="digit" placeholder="选填" />
          </view>
        </view>
        <view v-else class="adm-field">
          <text class="adm-label">货物重量（吨）</text>
          <input v-model="cargoWeight" class="adm-input" type="digit" placeholder="选填" />
        </view>

        <view class="adm-field">
          <text class="adm-label">施工车辆</text>
          <picker
            mode="selector"
            :range="vehicleLabels"
            :value="vehicleIndex"
            @change="onVehicleChange"
          >
            <view class="adm-picker">
              <text :class="{ placeholder: vehicleIndex < 0 }">
                {{ vehicleIndex >= 0 ? vehicleLabels[vehicleIndex] : '请选择车辆' }}
              </text>
              <view class="arrow" />
            </view>
          </picker>
        </view>

        <view class="adm-field">
          <text class="adm-label">执行司机</text>
          <picker
            mode="selector"
            :range="staffLabels"
            :value="staffIndex"
            @change="onStaffChange"
          >
            <view class="adm-picker">
              <text :class="{ placeholder: staffIndex < 0 }">
                {{ staffIndex >= 0 ? staffLabels[staffIndex] : '请选择司机' }}
              </text>
              <view class="arrow" />
            </view>
          </picker>
        </view>

        <view class="adm-field">
          <text class="adm-label">预计使用（天）</text>
          <input v-model="estimatedDays" class="adm-input" type="digit" placeholder="选填，仅备注提示" />
        </view>

        <view class="adm-field">
          <text class="adm-label">任务备注</text>
          <textarea v-model="convertRemark" class="adm-textarea" :maxlength="500" placeholder="填写任务备注" />
        </view>

        <button class="adm-btn primary" :disabled="submitting" @tap="submitConvert">
          {{ submitting ? '转工单中...' : '确认转工单并下发' }}
        </button>
      </view>
    </BottomSheet>

    <!-- 图片附件预览 -->
    <view v-if="previewVisible" class="preview-mask" @tap="closePreview">
      <image :src="previewUrl" mode="aspectFit" class="preview-image" @tap.stop />
      <text class="preview-tip">点击任意位置关闭</text>
    </view>
  </view>
</template>

<style scoped>
.type-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}
.title-row {
  display: flex;
  align-items: center;
  gap: 10px;
}
.title-row .adm-hero-title {
  margin-bottom: 4px;
}
/* 图片附件行 */
.attach-list {
  display: flex;
  flex-direction: column;
}
.attach-row {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 44px;
  border-bottom: 1px dashed #eef2f7;
}
.attach-row:last-child {
  border-bottom: none;
}
.attach-tag {
  flex: none;
  padding: 2px 8px;
  border-radius: 6px;
  background: #ecfeff;
  color: #0e7490;
  font-size: 11px;
}
.attach-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  color: #1e293b;
}
.attach-action {
  flex: none;
  font-size: 13px;
  font-weight: 600;
  color: #1a6bff;
}
.contact-hint {
  font-size: 12px;
  line-height: 1.5;
  color: #94a3b8;
}
.preview-mask {
  position: fixed;
  inset: 0;
  z-index: 9999;
  background: rgba(0, 0, 0, 0.88);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
}
.preview-image {
  width: 100%;
  height: 82vh;
}
.preview-tip {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.7);
}
</style>
