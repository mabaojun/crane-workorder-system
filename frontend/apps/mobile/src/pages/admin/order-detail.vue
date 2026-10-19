<script setup lang="ts">
// 管理员工单详情：查看执行信息，重新派单、取消、兜底结单
import { computed, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import dayjs from 'dayjs'
import StatusTag from '../../components/StatusTag.vue'
import BottomSheet from '../../components/BottomSheet.vue'
import {
  changeWorkOrderStatus,
  getWorkOrder,
  resumeWorkOrder,
  settleWorkOrder,
  type WorkOrder
} from '@crane/shared/api/workorder'
import { dispatchOrder } from '@crane/shared/api/dispatch'
import {
  pageStaffs,
  pageVehicles,
  vehicleSpecLabel,
  type Staff,
  type Vehicle
} from '@crane/shared/api/staff'
import { pageExceptions, type ExceptionRecord } from '@crane/shared/api/exception'
import { VEHICLE_STATUS_LABEL, VEHICLE_TYPE_LABEL } from '@crane/shared'
import { listOrderPhotos, type OrderPhoto } from '../../api/modules/biz'
import {
  fmtDateTime,
  isImageAttachment,
  loadAttachmentPreviewUrl,
  toastError,
  toastOk
} from '../../utils'

const orderId = ref('')
const order = ref<WorkOrder | null>(null)
const loading = ref(true)
const exceptions = ref<ExceptionRecord[]>([])

// 图片附件：需求提报图、签到/签退/任务结束照片、异常配图，点击弹窗预览
const photos = ref<OrderPhoto[]>([])
const previewVisible = ref(false)
const previewLoading = ref(false)
const previewUrl = ref('')

const showDispatch = ref(false)
const staffList = ref<Staff[]>([])
const vehicleList = ref<Vehicle[]>([])
const staffIndex = ref(-1)
const vehicleIndex = ref(-1)
const planDate = ref('')
const planTime = ref('08:00')
const dispatchRemark = ref('')
const submitting = ref(false)

const showCancel = ref(false)
const cancelReason = ref('')

const canDispatch = computed(() => order.value?.status === 'PENDING_ACCEPT')
const canCancel = computed(
  () => !!order.value && !['COMPLETED', 'CANCELLED'].includes(order.value.status)
)
const canSettle = computed(
  () => !!order.value && ['IN_PROGRESS', 'SUSPENDED'].includes(order.value.status)
)
const canResume = computed(() => order.value?.status === 'SUSPENDED')

const staffLabels = computed(() => staffList.value.map((s) => `${s.name} · ${s.phone}`))
/** 派单车辆按工单用车种类筛选 */
const filteredVehicles = computed(() =>
  vehicleList.value.filter((v) => (v.type || 'CRANE') === (order.value?.vehicleType || 'CRANE'))
)
const vehicleLabels = computed(() =>
  filteredVehicles.value.map(
    (v) => `${v.plateNo} · ${vehicleSpecLabel(v)} · ${VEHICLE_STATUS_LABEL[v.status] || v.status}`
  )
)

async function load() {
  if (!orderId.value) return
  loading.value = true
  try {
    const o = await getWorkOrder(orderId.value)
    order.value = o
    initDispatchForm(o)
    loadExceptions(o.id)
    loadPhotos(o.id)
  } catch (e) {
    toastError(e)
    order.value = null
  } finally {
    loading.value = false
  }
}

function initDispatchForm(o: WorkOrder) {
  const start = o.expectedStartAt ? dayjs(o.expectedStartAt) : dayjs().add(1, 'day').hour(8).minute(0)
  const safeStart = start.isBefore(dayjs()) ? dayjs().add(1, 'day').hour(8).minute(0) : start
  planDate.value = safeStart.format('YYYY-MM-DD')
  planTime.value = safeStart.format('HH:mm')
  dispatchRemark.value = o.remark || ''
}

async function loadExceptions(id: number) {
  try {
    const res = await pageExceptions({ orderId: id, pageNum: 1, pageSize: 20 })
    exceptions.value = res.list ?? []
  } catch {
    exceptions.value = []
  }
}

/** 工单图片附件（后端聚合需求单、打卡、异常照片） */
async function loadPhotos(id: number) {
  try {
    photos.value = await listOrderPhotos(id)
  } catch {
    photos.value = []
  }
}

/** 点击附件后弹窗预览图片 */
async function onPreviewPhoto(photo: OrderPhoto) {
  if (previewLoading.value) return
  if (!isImageAttachment(photo.fileName || photo.url)) {
    uni.showToast({ title: '该附件暂不支持预览', icon: 'none' })
    return
  }
  previewLoading.value = true
  try {
    previewUrl.value = await loadAttachmentPreviewUrl(photo.url)
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
    const o = order.value
    if (o?.staffId != null) {
      const idx = staffList.value.findIndex((s) => s.id === o.staffId)
      if (idx >= 0) staffIndex.value = idx
    }
    if (o?.vehicleId != null) {
      const idx = filteredVehicles.value.findIndex((v) => v.id === o.vehicleId)
      if (idx >= 0) vehicleIndex.value = idx
    }
  } catch (e) {
    toastError(e)
  }
}

function openDispatch() {
  if (!staffList.value.length || !vehicleList.value.length) {
    loadOptions().then(() => {
      showDispatch.value = true
    })
    return
  }
  showDispatch.value = true
}

function onStaffChange(e: { detail: { value: string } }) {
  staffIndex.value = Number(e.detail.value)
}
function onVehicleChange(e: { detail: { value: string } }) {
  vehicleIndex.value = Number(e.detail.value)
}
function onDateChange(e: { detail: { value: string } }) {
  planDate.value = e.detail.value
}
function onTimeChange(e: { detail: { value: string } }) {
  planTime.value = e.detail.value
}

async function submitDispatch() {
  if (!order.value || submitting.value) return
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
  const planStartAt = `${planDate.value}T${planTime.value}:00`
  if (!planDate.value || !planTime.value) {
    uni.showToast({ title: '请选择计划开始时间', icon: 'none' })
    return
  }
  submitting.value = true
  try {
    await dispatchOrder(order.value.id, {
      staffId: staff.id,
      vehicleId: vehicle.id,
      planStartAt,
      remark: dispatchRemark.value.trim() || undefined
    })
    toastOk('派单成功，等待司机接单')
    showDispatch.value = false
    await load()
  } catch (e) {
    toastError(e)
  } finally {
    submitting.value = false
  }
}

async function submitCancel() {
  if (!order.value || submitting.value) return
  if (!cancelReason.value.trim()) {
    uni.showToast({ title: '请填写取消原因', icon: 'none' })
    return
  }
  submitting.value = true
  try {
    await changeWorkOrderStatus(order.value.id, {
      action: 'CANCEL',
      reason: cancelReason.value.trim()
    })
    toastOk('工单已取消')
    showCancel.value = false
    await load()
  } catch (e) {
    toastError(e)
  } finally {
    submitting.value = false
  }
}

function onSettle() {
  if (!order.value) return
  uni.showModal({
    title: '兜底结单',
    content: '确认强制结单并归档？结单后车辆将释放。',
    success: async (r) => {
      if (!r.confirm || !order.value) return
      try {
        uni.showLoading({ title: '结单中' })
        await settleWorkOrder(order.value.id)
        uni.hideLoading()
        toastOk('已结单')
        await load()
      } catch (e) {
        uni.hideLoading()
        toastError(e)
      }
    }
  })
}

/** 批准受阻工单继续施工（关闭未批复异常并恢复 IN_PROGRESS） */
function onResume() {
  if (!order.value) return
  uni.showModal({
    title: '批准继续施工',
    content: '确认批准该受阻工单恢复施工？未批复异常将自动关闭。',
    success: async (r) => {
      if (!r.confirm || !order.value) return
      try {
        uni.showLoading({ title: '处理中' })
        await resumeWorkOrder(order.value.id)
        uni.hideLoading()
        toastOk('已恢复施工')
        await load()
      } catch (e) {
        uni.hideLoading()
        toastError(e)
      }
    }
  })
}

function call(phone?: string | null) {
  if (!phone) {
    uni.showToast({ title: '暂无联系电话', icon: 'none' })
    return
  }
  uni.makePhoneCall({ phoneNumber: phone })
}

function goExceptions() {
  uni.navigateTo({ url: '/pages/admin/exceptions' })
}

onLoad((opts) => {
  orderId.value = opts?.id ?? ''
})

onShow(async () => {
  await load()
  await loadOptions()
})
</script>

<template>
  <view class="adm-page">
    <view v-if="loading" class="adm-loading">加载中...</view>
    <view v-else-if="!order" class="adm-empty">工单不存在或无权查看</view>

    <template v-else>
      <view class="adm-hero">
        <text class="adm-hero-kicker">WORK ORDER · CONTROL</text>
        <view class="title-row">
          <text class="adm-hero-title">{{ order.orderNo }}</text>
          <StatusTag :status="order.status" />
        </view>
        <text class="adm-hero-sub">{{ order.taskName }}</text>
      </view>

      <view class="adm-section">
        <view class="adm-section-head">
          <text class="adm-section-title">执行信息</text>
        </view>
        <view class="adm-kv"><text class="k">客户</text><text class="v strong">{{ order.customerName || '-' }}</text></view>
        <view class="adm-kv">
          <text class="k">联系人</text>
          <text class="v">{{ order.contactName || '-' }} {{ order.contactPhone || '' }}</text>
          <text v-if="order.contactPhone" class="call" @tap="call(order.contactPhone)">拨打</text>
        </view>
        <view class="adm-kv">
          <text class="k">司机</text>
          <text class="v">{{ order.staffName || '未派司机' }}</text>
          <text v-if="order.staffPhone" class="call" @tap="call(order.staffPhone)">拨打</text>
        </view>
        <view class="adm-kv"><text class="k">用车种类</text><text class="v strong">{{ VEHICLE_TYPE_LABEL[order.vehicleType || 'CRANE'] }}</text></view>
        <view class="adm-kv"><text class="k">车辆</text><text class="v">{{ order.vehiclePlateNo || '未派车' }}</text></view>
        <view class="adm-kv"><text class="k">地址</text><text class="v">{{ order.address || '-' }}</text></view>
        <view class="adm-kv"><text class="k">计划开始</text><text class="v">{{ fmtDateTime(order.expectedStartAt) }}</text></view>
        <view v-if="!order.vehicleType || order.vehicleType === 'CRANE'" class="adm-kv"><text class="k">吊车吨位</text><text class="v">{{ order.craneTonnage != null ? order.craneTonnage + ' 吨' : '-' }}</text></view>
        <view class="adm-kv"><text class="k">货物重量</text><text class="v">{{ order.cargoWeight != null ? order.cargoWeight + ' 吨' : '-' }}</text></view>
        <view class="adm-kv"><text class="k">任务备注</text><text class="v">{{ order.remark || '无' }}</text></view>
        <view class="adm-kv"><text class="k">施工要求</text><text class="v">{{ order.requirement || '无' }}</text></view>
      </view>

      <view class="adm-section">
        <view class="adm-section-head">
          <text class="adm-section-title">图片附件</text>
          <text v-if="photos.length" class="adm-section-link">{{ photos.length }} 张</text>
        </view>
        <view v-if="photos.length" class="attach-list">
          <view
            v-for="p in photos"
            :key="p.stage + p.url"
            class="attach-row"
            @tap="onPreviewPhoto(p)"
          >
            <text class="attach-stage">{{ p.stageLabel }}</text>
            <text class="attach-name">{{ p.fileName || '图片' }}</text>
            <text class="attach-action">{{ previewLoading ? '加载中' : '预览' }}</text>
          </view>
        </view>
        <view v-else class="adm-empty">暂无图片附件</view>
      </view>

      <view class="adm-section">
        <view class="adm-section-head">
          <text class="adm-section-title">异常记录</text>
          <text class="adm-section-link" @tap="goExceptions">异常审批</text>
        </view>
        <view v-if="exceptions.length" class="adm-list">
          <view v-for="x in exceptions" :key="x.id" class="exc-row">
            <view class="exc-main">
              <text class="exc-type">{{ x.typeLabel || x.type }}</text>
              <text class="exc-desc">{{ x.description || '无描述' }}</text>
            </view>
            <StatusTag :status="x.status" />
          </view>
        </view>
        <view v-else class="adm-empty">暂无异常记录</view>
      </view>

      <view v-if="canDispatch || canCancel || canSettle || canResume" class="adm-sticky-actions">
        <button v-if="canDispatch" class="adm-btn primary" @tap="openDispatch">重新派单</button>
        <button v-if="canResume" class="adm-btn primary" @tap="onResume">批准继续施工</button>
        <button v-if="canSettle" class="adm-btn ghost" @tap="onSettle">兜底结单</button>
        <button v-if="canCancel" class="adm-btn plain-danger" @tap="showCancel = true">取消工单</button>
      </view>
    </template>

    <BottomSheet v-if="showDispatch" title="派单 / 重新派单" @close="showDispatch = false">
      <view class="adm-form">
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
        <view class="adm-inline">
          <view class="adm-field">
            <text class="adm-label">计划日期</text>
            <picker mode="date" :value="planDate" @change="onDateChange">
              <view class="adm-picker"><text>{{ planDate }}</text><view class="arrow" /></view>
            </picker>
          </view>
          <view class="adm-field">
            <text class="adm-label">计划时间</text>
            <picker mode="time" :value="planTime" @change="onTimeChange">
              <view class="adm-picker"><text>{{ planTime }}</text><view class="arrow" /></view>
            </picker>
          </view>
        </view>
        <view class="adm-field">
          <text class="adm-label">派单备注</text>
          <textarea v-model="dispatchRemark" class="adm-textarea" :maxlength="500" placeholder="选填" />
        </view>
        <button class="adm-btn primary" :disabled="submitting" @tap="submitDispatch">
          {{ submitting ? '派单中...' : '确认派单' }}
        </button>
      </view>
    </BottomSheet>

    <BottomSheet v-if="showCancel" title="取消工单" @close="showCancel = false">
      <view class="adm-form">
        <view class="adm-field">
          <text class="adm-label">取消原因（客户可见）</text>
          <textarea v-model="cancelReason" class="adm-textarea" :maxlength="500" placeholder="请说明取消原因" />
        </view>
        <button class="adm-btn danger" :disabled="submitting" @tap="submitCancel">
          {{ submitting ? '提交中...' : '确认取消工单' }}
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
.title-row {
  display: flex;
  align-items: center;
  gap: 10px;
}
.title-row .adm-hero-title {
  margin-bottom: 4px;
}
.call {
  flex: none;
  margin-left: 8px;
  font-size: 12px;
  color: #0e7490;
  border: 1px solid #a5f3fc;
  border-radius: 999px;
  padding: 1px 10px;
}
.exc-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 8px 0;
  border-bottom: 1px dashed #eef2f7;
}
.exc-row:last-child {
  border-bottom: none;
}
.exc-main {
  flex: 1;
  min-width: 0;
}
.exc-type {
  display: block;
  font-size: 13px;
  font-weight: 600;
  color: #0f172a;
}
.exc-desc {
  display: block;
  margin-top: 2px;
  font-size: 12px;
  color: #64748b;
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
.attach-stage {
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
