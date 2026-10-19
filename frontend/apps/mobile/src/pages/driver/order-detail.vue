<script setup lang="ts">
// 司机端工单专属操作页：所有司机端工单相关操作均在此页完成
// 状态化操作：接单/拒单（待接单）、签到/退单（待到场）、异常/签退/终止（进行中）
import { computed, ref } from 'vue'
import { onHide, onLoad, onShow, onUnload } from '@dcloudio/uni-app'
import StatusTag from '../../components/StatusTag.vue'
import EmptyState from '../../components/EmptyState.vue'
import BottomSheet from '../../components/BottomSheet.vue'
import {
  acceptOrder,
  checkIn,
  createExceptionReport,
  dailyCheckout,
  getOrderDetail,
  listOrderPhotos,
  rejectOrder,
  requestResume,
  taskEnd,
  withdrawOrder,
  type OrderPhoto
} from '../../api/modules/biz'
import type { WorkOrder } from '../../api/types'
import { VEHICLE_TYPE_LABEL } from '@crane/shared'
import {
  chooseImages,
  fmtTimeWithDayHint,
  getLocation,
  isImageAttachment,
  loadAttachmentPreviewUrl,
  toastError,
  toastOk,
  uploadLocalFile
} from '../../utils'
import { openNavigation, navFromOrder } from '../../utils/navigation'
import { ApiError, request } from '@crane/shared/api/client'

const orderId = ref<number | string>('')
const order = ref<WorkOrder | null>(null)
const loading = ref(true)

// 客户提报照片：附件形式展示，点击后弹窗预览
const demandPhotos = ref<OrderPhoto[]>([])
const previewVisible = ref(false)
const previewLoading = ref(false)
const previewUrl = ref('')

// 接单倒计时：按秒刷新，超时提示报警
const nowMs = ref(Date.now())
let countdownTimer: ReturnType<typeof setInterval> | null = null
const acceptRemainSec = computed(() => {
  const deadline = order.value?.acceptDeadlineAt
  if (!deadline) return null
  const end = new Date(deadline).getTime()
  if (Number.isNaN(end)) return null
  return Math.max(0, Math.floor((end - nowMs.value) / 1000))
})
const acceptExpired = computed(() => acceptRemainSec.value === 0)
const expectedStartText = computed(() => fmtTimeWithDayHint(order.value?.expectedStartAt, nowMs.value))

function fmtCountdown(sec: number) {
  const h = Math.floor(sec / 3600)
  const m = Math.floor((sec % 3600) / 60)
  const s = sec % 60
  const mm = String(m).padStart(2, '0')
  const ss = String(s).padStart(2, '0')
  return h > 0 ? `${h}:${mm}:${ss}` : `${mm}:${ss}`
}

function startCountdown() {
  stopCountdown()
  nowMs.value = Date.now()
  countdownTimer = setInterval(() => {
    nowMs.value = Date.now()
  }, 1000)
}

function stopCountdown() {
  if (!countdownTimer) return
  clearInterval(countdownTimer)
  countdownTimer = null
}

// 打卡定位/照片强制开关（后台系统配置实时下发；测试阶段默认不强制）
const locationRequired = ref(false)
const photoRequired = ref(false)

async function loadAttendanceSwitches() {
  try {
    const cfg = await request<Record<string, string>>('/system/public-config')
    locationRequired.value = cfg['attendance.location_required'] === 'true'
    photoRequired.value = cfg['attendance.photo_required'] === 'true'
  } catch {
    // 拉取失败按后端默认（不强制）处理，最终以后端校验为准
  }
}
loadAttendanceSwitches()

/** 签退 / 结单定位：强制开关关闭时定位失败不阻断，按无定位提交 */
async function optionalLocation(): Promise<{ longitude: number; latitude: number } | null> {
  try {
    return await getLocation()
  } catch {
    if (locationRequired.value) {
      throw new Error('定位失败，请检查定位权限')
    }
    return null
  }
}

async function load() {
  loading.value = true
  try {
    order.value = await getOrderDetail(orderId.value)
    loadDemandPhotos()
  } catch (e) {
    toastError(e)
  } finally {
    loading.value = false
  }
}

/** 客户提报图片：仅取需求单阶段，附件形式展示 */
async function loadDemandPhotos() {
  try {
    const photos = await listOrderPhotos(orderId.value)
    demandPhotos.value = photos.filter((p) => p.stage === 'DEMAND')
  } catch {
    demandPhotos.value = []
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

onLoad((opts) => {
  orderId.value = opts?.id ?? ''
})

onShow(() => {
  startCountdown()
  if (orderId.value) load()
})

onHide(stopCountdown)
onUnload(() => {
  stopCountdown()
  closePreview()
})

function needOrder(): WorkOrder {
  if (!order.value) throw new Error('工单未加载')
  return order.value
}

/** 一键导航 */
function onNavigate() {
  const o = needOrder()
  const target = navFromOrder(o)
  if (!target) {
    uni.showToast({ title: '该工单无位置坐标', icon: 'none' })
    return
  }
  openNavigation(target)
}

/** 拨打需求单联系人电话 */
function callContact() {
  const phone = needOrder().contactPhone
  if (!phone) {
    uni.showToast({ title: '暂无联系人电话', icon: 'none' })
    return
  }
  uni.makePhoneCall({ phoneNumber: phone })
}

// ---- 接单 ----
function onAccept() {
  const o = needOrder()
  uni.showModal({
    title: '确认接单',
    content: `接受工单 ${o.orderNo}？`,
    success: async (r) => {
      if (!r.confirm) return
      try {
        await acceptOrder(o.id)
        toastOk('已接单')
        await load()
      } catch (e) {
        toastError(e)
      }
    }
  })
}

// ---- 拒单/退单共用弹层（原因类型一致） ----
const showReject = ref(false)
/** mode=reject 拒单（待接单）；mode=withdraw 退单（已接单待到场） */
const rejectMode = ref<'reject' | 'withdraw'>('reject')
const REJECT_TYPES = [
  { value: 'VEHICLE_FAULT', label: '车辆故障' },
  { value: 'TIME_CONFLICT', label: '时间冲突' },
  { value: 'ABILITY_MISMATCH', label: '能力不匹配' },
  { value: 'OTHER', label: '其他原因' }
]
const rejectType = ref('OTHER')
const rejectReason = ref('')

function onRejectOpen() {
  rejectMode.value = 'reject'
  rejectType.value = 'OTHER'
  rejectReason.value = ''
  showReject.value = true
}

/** 退单：仅已接单（待到场）可发起，工单回退待接单并实时通知管理端 */
function onWithdrawOpen() {
  rejectMode.value = 'withdraw'
  rejectType.value = 'OTHER'
  rejectReason.value = ''
  showReject.value = true
}

async function onRejectSubmit() {
  if (!rejectReason.value.trim()) {
    uni.showToast({ title: '请填写原因说明', icon: 'none' })
    return
  }
  const body = { reasonType: rejectType.value, reason: rejectReason.value.trim() }
  try {
    if (rejectMode.value === 'withdraw') {
      await withdrawOrder(needOrder().id, body)
      toastOk('已退单，管理员将重新派单')
    } else {
      await rejectOrder(needOrder().id, body)
      toastOk('已提交拒单')
    }
    showReject.value = false
    await load()
  } catch (e) {
    toastError(e)
  }
}

// ---- 到场签到 ----
const showCheckin = ref(false)
const locating = ref(false)
const coords = ref<{ longitude: number; latitude: number } | null>(null)
const photos = ref<string[]>([])

async function openCheckin() {
  showCheckin.value = true
  photos.value = []
  coords.value = null
  await locate()
}

async function locate() {
  locating.value = true
  try {
    coords.value = await getLocation()
  } catch {
    toastError(new Error('定位失败，请检查定位权限'))
  } finally {
    locating.value = false
  }
}

async function takePhoto() {
  try {
    const paths = await chooseImages(1)
    photos.value = [...photos.value, ...paths].slice(0, 3)
  } catch {
    // 用户取消选图
  }
}

async function submitCheckin() {
  const o = needOrder()
  // 定位强制开关关闭（测试阶段）时允许无定位提交；有定位仍随单记录
  if (!coords.value && locationRequired.value) {
    uni.showToast({ title: '尚未获取定位', icon: 'none' })
    return
  }
  if (photoRequired.value && photos.value.length === 0) {
    uni.showToast({ title: '请拍摄现场照片', icon: 'none' })
    return
  }
  try {
    uni.showLoading({ title: '签到中' })
    // 现场照片先上传到文件服务，打卡记录只保存可鉴权下载的地址
    const photoUrls = photos.value.length
      ? await Promise.all(photos.value.map(uploadLocalFile))
      : null
    await checkIn({
      orderId: o.id,
      longitude: coords.value?.longitude ?? null,
      latitude: coords.value?.latitude ?? null,
      photoUrls
    })
    uni.hideLoading()
    showCheckin.value = false
    toastOk('签到成功')
    await load()
  } catch (e) {
    uni.hideLoading()
    // 30005 超出签到有效范围等业务错误：直接展示后端 message
    if (e instanceof ApiError && e.code === 30005) {
      uni.showToast({ title: e.message, icon: 'none' })
    } else {
      toastError(e)
    }
  }
}

// ---- 异常上报 ----
const showException = ref(false)
const EXC_TYPES = [
  { value: 'RAIN', label: '下雨' },
  { value: 'PARTY_STOP', label: '双方停工' },
  { value: 'VEHICLE_FAULT', label: '车辆故障' },
  { value: 'CUSTOM', label: '其他原因' }
]
const excType = ref('RAIN')
const excTypeLabel = ref('')
const excDesc = ref('')

function onExceptionOpen() {
  excType.value = 'RAIN'
  excTypeLabel.value = ''
  excDesc.value = ''
  showException.value = true
}

async function onExceptionSubmit() {
  if (!excDesc.value.trim()) {
    uni.showToast({ title: '请填写异常描述', icon: 'none' })
    return
  }
  if (excType.value === 'CUSTOM' && !excTypeLabel.value.trim()) {
    uni.showToast({ title: '请填写自定义原因标签', icon: 'none' })
    return
  }
  try {
    await createExceptionReport({
      orderId: needOrder().id,
      type: excType.value,
      typeLabel: excType.value === 'CUSTOM' ? excTypeLabel.value.trim() : undefined,
      description: excDesc.value.trim()
    })
    showException.value = false
    toastOk('异常已上报')
    await load()
  } catch (e) {
    toastError(e)
  }
}

// ---- 签退（今日任务完成） / 工单终止（任务结束） ----
const showCheckout = ref(false)
const isLeftSite = ref(true)
const isReturned = ref(false)

function onCheckoutOpen() {
  isLeftSite.value = true
  isReturned.value = false
  showCheckout.value = true
}

async function onCheckoutSubmit() {
  const o = needOrder()
  try {
    uni.showLoading({ title: '提交中' })
    const loc = await optionalLocation()
    await dailyCheckout({
      orderId: o.id,
      longitude: loc?.longitude ?? null,
      latitude: loc?.latitude ?? null,
      isLeftSite: isLeftSite.value,
      isReturned: isReturned.value
    })
    uni.hideLoading()
    showCheckout.value = false
    toastOk('签退成功')
    await load()
  } catch (e) {
    uni.hideLoading()
    toastError(e)
  }
}

/** 暂停工单申请继续施工：实时通知管理端，批准后恢复 */
const resuming = ref(false)
async function onResumeRequest() {
  const o = needOrder()
  if (resuming.value) return
  resuming.value = true
  try {
    await requestResume(o.id)
    toastOk('已提交申请，管理员会尽快处理')
  } catch (e) {
    toastError(e)
  } finally {
    resuming.value = false
  }
}

/** 工单终止：任务结束自动结单归档 */
function onTaskEnd() {
  const o = needOrder()
  uni.showModal({
    title: '确认终止工单',
    content: '工单终止将自动结单并归档，确认已完工？',
    success: async (r) => {
      if (!r.confirm) return
      try {
        uni.showLoading({ title: '提交中' })
        const loc = await optionalLocation()
        await taskEnd({
          orderId: o.id,
          longitude: loc?.longitude ?? null,
          latitude: loc?.latitude ?? null
        })
        uni.hideLoading()
        toastOk('工单已终止')
        await load()
      } catch (e) {
        uni.hideLoading()
        toastError(e)
      }
    }
  })
}
</script>

<template>
  <view class="page">
    <view v-if="loading" class="loading">加载中...</view>
    <EmptyState v-else-if="!order" text="工单不存在或无权查看" />

    <template v-else>
      <!-- 工单信息 -->
      <view class="card">
        <view class="head">
          <text class="no">{{ order.orderNo }}</text>
          <StatusTag :status="order.status" />
        </view>
        <view class="title">{{ order.taskName || '吊装任务' }}</view>
        <view class="row"><text class="k">客户</text><text>{{ order.customerName || '-' }}</text></view>
        <view class="row link-row" @tap="callContact">
          <text class="k">联系人</text>
          <text>{{ order.contactName || '-' }}</text>
          <text v-if="order.contactPhone" class="tel">拨打</text>
        </view>
        <view class="row"><text class="k">用车种类</text><text>{{ VEHICLE_TYPE_LABEL[order.vehicleType || 'CRANE'] }}</text></view>
        <view class="row"><text class="k">车牌</text><text>{{ order.vehiclePlateNo || '-' }}</text></view>
        <view class="row"><text class="k">地址</text><text>{{ order.address || '-' }}</text></view>
        <view class="row">
          <text class="k">导航</text>
          <view
            v-if="order.longitude != null && order.latitude != null"
            class="nav-link"
            @tap="onNavigate"
          >一键导航</view>
          <text v-else class="sub-text">无位置坐标</text>
        </view>
        <view class="row"><text class="k">计划开始</text><text>{{ expectedStartText }}</text></view>
        <view v-if="!order.vehicleType || order.vehicleType === 'CRANE'" class="row"><text class="k">吊车吨位</text><text>{{ order.craneTonnage ? order.craneTonnage + ' 吨' : '-' }}</text></view>
        <view class="row"><text class="k">货物名称</text><text>{{ order.cargoName || '-' }}</text></view>
        <view class="row"><text class="k">货物重量</text><text>{{ order.cargoWeight ? order.cargoWeight + ' 吨' : '-' }}</text></view>
        <view v-if="order.estimatedDays" class="row"><text class="k">预计使用</text><text>{{ order.estimatedDays }} 天（备注提示）</text></view>
        <view v-if="order.remark" class="remark-block">
          <text class="remark-label">下单备注</text>
          <text class="remark-text">{{ order.remark }}</text>
        </view>
      </view>

      <!-- 客户提报照片：附件形式，点击弹窗预览 -->
      <view v-if="demandPhotos.length" class="card">
        <view class="section-title">客户提报照片</view>
        <view
          v-for="p in demandPhotos"
          :key="p.url"
          class="attach-row"
          @tap="onPreviewPhoto(p)"
        >
          <text class="attach-tag">附件</text>
          <text class="attach-name">{{ p.fileName || '图片' }}</text>
          <text class="attach-action">{{ previewLoading ? '加载中' : '预览' }}</text>
        </view>
      </view>

      <!-- 状态化操作区：核心操作高度 48px，全宽（≥44dp） -->
      <view class="card actions-card">
        <view class="actions-title">工单操作</view>
        <view
          v-if="order.status === 'PENDING_ACCEPT' && acceptRemainSec != null"
          class="countdown"
          :class="{ expired: acceptExpired }"
        >
          <text class="countdown-main">
            {{ acceptExpired ? '已超出接单时限' : `接单倒计时 ${fmtCountdown(acceptRemainSec)}` }}
          </text>
          <text class="countdown-sub">
            {{ acceptExpired ? '系统已触发接单超时报警，请立即接单' : '请在倒计时结束前接单，避免触发报警' }}
          </text>
        </view>
        <view class="actions">
          <template v-if="order.status === 'PENDING_ACCEPT'">
            <button class="btn primary" @tap="onAccept">接单</button>
            <button class="btn danger-outline" @tap="onRejectOpen">拒单</button>
          </template>

          <template v-else-if="order.status === 'PENDING_ARRIVE'">
            <button class="btn primary" @tap="openCheckin">到场签到</button>
            <button class="btn danger-outline" @tap="onWithdrawOpen">退单</button>
          </template>

          <template v-else-if="order.status === 'IN_PROGRESS'">
            <button class="btn warn-outline" @tap="onExceptionOpen">异常上报</button>
            <button class="btn primary" @tap="onCheckoutOpen">签退（今日任务完成）</button>
            <button class="btn danger" @tap="onTaskEnd">终止工单</button>
          </template>

          <template v-else-if="order.status === 'SUSPENDED'">
            <button class="btn primary" :loading="resuming" @tap="onResumeRequest">申请继续施工</button>
            <button class="btn warn-outline" @tap="onExceptionOpen">补充异常说明</button>
            <button class="btn primary-outline" @tap="onCheckoutOpen">签退（今日任务完成）</button>
            <button class="btn danger" @tap="onTaskEnd">终止工单</button>
            <view class="tip-line">申请继续后将实时通知管理员，批准后恢复施工；也可直接签退或终止工单</view>
          </template>
          <view v-else class="waiting">
            工单已{{ order.status === 'COMPLETED' ? '完成' : order.status === 'CANCELLED' ? '取消' : '结束' }}，无可用操作
          </view>
        </view>
      </view>
    </template>

    <!-- 拒单/退单弹层 -->
    <BottomSheet v-if="showReject" :title="rejectMode === 'withdraw' ? '退单原因' : '拒单原因'" @close="showReject = false">
      <view v-if="rejectMode === 'withdraw'" class="tip-line">
        退单后工单将回到待接单队列，管理员会收到实时通知并重新派单
      </view>
      <view class="opts">
        <view
          v-for="t in REJECT_TYPES"
          :key="t.value"
          class="opt"
          :class="{ active: rejectType === t.value }"
          @tap="rejectType = t.value"
        >
          {{ t.label }}
        </view>
      </view>
      <textarea
        v-model="rejectReason"
        class="textarea"
        placeholder="请填写原因说明"
        :maxlength="200"
      />
      <button class="btn primary block" @tap="onRejectSubmit">
        {{ rejectMode === 'withdraw' ? '确认退单' : '提交拒单' }}
      </button>
    </BottomSheet>

    <!-- 异常上报弹层 -->
    <BottomSheet v-if="showException" title="异常上报" @close="showException = false">
      <view class="opts">
        <view
          v-for="t in EXC_TYPES"
          :key="t.value"
          class="opt"
          :class="{ active: excType === t.value }"
          @tap="excType = t.value"
        >
          {{ t.label }}
        </view>
      </view>
      <input
        v-if="excType === 'CUSTOM'"
        v-model="excTypeLabel"
        class="field-input"
        placeholder="请填写自定义原因标签"
      />
      <textarea v-model="excDesc" class="textarea" placeholder="请描述异常情况" :maxlength="500" />
      <button class="btn primary block" @tap="onExceptionSubmit">提交异常</button>
    </BottomSheet>

    <!-- 签退弹层 -->
    <BottomSheet v-if="showCheckout" title="签退（今日任务完成）" @close="showCheckout = false">
      <view class="tip-line">签退仅结束当日施工打卡，工单保持进行中，不会结单。</view>
      <view class="switch-row">
        <view class="switch-label">
          <text>车辆是否离场</text>
          <text class="sub">今日施工结束，车辆离开作业现场</text>
        </view>
        <switch :checked="isLeftSite" color="#1d4ed8" @change="(e: any) => (isLeftSite = e.detail.value)" />
      </view>
      <view class="switch-row">
        <view class="switch-label">
          <text>车辆是否回场</text>
          <text class="sub">车辆返回公司场地后车辆状态置为空闲</text>
        </view>
        <switch :checked="isReturned" color="#1d4ed8" @change="(e: any) => (isReturned = e.detail.value)" />
      </view>
      <button class="btn primary block" @tap="onCheckoutSubmit">确认签退</button>
    </BottomSheet>

    <!-- 到场签到弹层 -->
    <BottomSheet v-if="showCheckin" title="到场签到" @close="showCheckin = false">
      <view class="kv">
        <text class="k">定位</text>
        <text v-if="locating">定位中...</text>
        <text v-else-if="coords">已定位（{{ coords.longitude.toFixed(5) }}, {{ coords.latitude.toFixed(5) }}）</text>
        <text v-else class="err">定位失败</text>
      </view>
      <view class="kv" @tap="locate"><text class="link">重新定位</text></view>
      <view class="kv">
        <text class="k">现场照片</text>
        <text class="sub">建议拍摄 1 张现场照片</text>
      </view>
      <view class="photo-row">
        <view v-for="(p, i) in photos" :key="i" class="photo">
          <image :src="p" mode="aspectFill" />
        </view>
        <view v-if="photos.length < 3" class="photo add" @tap="takePhoto">拍照</view>
      </view>
      <button class="btn primary block" :disabled="locating" @tap="submitCheckin">确认签到</button>
    </BottomSheet>

    <!-- 附件图片预览弹窗 -->
    <view v-if="previewVisible" class="preview-mask" @tap="closePreview">
      <image :src="previewUrl" mode="aspectFit" class="preview-image" @tap.stop />
      <text class="preview-tip">点击任意位置关闭</text>
    </view>
  </view>
</template>

<style scoped>
.page {
  min-height: 100vh;
  background: var(--bg);
  padding: 16px;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.loading {
  text-align: center;
  color: var(--sub);
  font-size: 14px;
  padding: 32px 0;
}
.card {
  background: var(--panel);
  border: 1px solid var(--line);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow);
  padding: 16px 18px;
}
.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.no {
  font-size: 14px;
  color: var(--sub);
  font-variant-numeric: tabular-nums;
}
.title {
  font-size: 20px;
  font-weight: 700;
  margin: 5px 0 12px;
  line-height: 1.4;
}
.row {
  display: flex;
  gap: 8px;
  font-size: 15px;
  margin-bottom: 8px;
  align-items: center;
}
.k {
  color: var(--sub);
  font-size: 14px;
  flex: none;
  width: 70px;
}
.link-row .tel {
  margin-left: auto;
  color: var(--brand);
  font-size: 14px;
  font-weight: 600;
  border: 1px solid var(--brand);
  border-radius: 999px;
  padding: 2px 14px;
  min-height: 32px;
  line-height: 32px;
}
.nav-link {
  color: var(--brand);
  font-size: 15px;
  font-weight: 600;
  min-height: 32px;
  display: flex;
  align-items: center;
  text-decoration: underline;
}
.sub-text {
  color: var(--sub);
  font-size: 14px;
}
.actions-title {
  font-size: 17px;
  font-weight: 700;
  margin-bottom: 12px;
}
.section-title {
  font-size: 16px;
  font-weight: 700;
  margin-bottom: 10px;
}
.attach-row {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 48px;
  border-bottom: 1px solid var(--line);
}
.attach-row:last-child {
  border-bottom: none;
}
.attach-tag {
  flex: none;
  font-size: 13px;
  color: var(--sub);
  border: 1px solid var(--line);
  border-radius: 6px;
  padding: 1px 7px;
}
.attach-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 15px;
}
.attach-action {
  flex: none;
  font-size: 14px;
  font-weight: 600;
  color: var(--brand);
}
.remark-block {
  margin-top: 10px;
  padding: 12px 14px;
  border-left: 4px solid #e6a23c;
  background: rgba(230, 162, 60, 0.12);
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.remark-label {
  font-size: 13px;
  font-weight: 700;
  color: #a86a1f;
}
.remark-text {
  font-size: 15px;
  line-height: 1.65;
  color: #1f2937;
  word-break: break-all;
}
.countdown {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 12px;
  padding: 12px 14px;
  border-radius: var(--radius);
  border: 1px solid rgba(230, 162, 60, 0.45);
  background: rgba(230, 162, 60, 0.12);
}
.countdown-main {
  font-size: 18px;
  font-weight: 700;
  color: #a86a1f;
  font-variant-numeric: tabular-nums;
}
.countdown-sub {
  font-size: 13px;
  color: #a86a1f;
}
.countdown.expired {
  border-color: rgba(220, 38, 38, 0.5);
  background: rgba(220, 38, 38, 0.12);
}
.countdown.expired .countdown-main,
.countdown.expired .countdown-sub {
  color: #dc2626;
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
.actions {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.actions .btn,
.btn.block {
  width: 100%;
  margin-left: 0;
  margin-right: 0;
}
.btn {
  height: 50px;
  line-height: 50px;
  border-radius: var(--radius);
  font-size: 17px;
  font-weight: 700;
  border: none;
}
.btn::after {
  border: none;
}
.btn.block {
  margin-top: 16px;
}
.btn.primary {
  background: linear-gradient(135deg, #1a6bff, #0b4fd6);
  color: #fff;
  box-shadow: 0 6px 16px rgba(26, 107, 255, 0.22);
}
.btn.danger {
  background: var(--danger);
  color: #fff;
}
.btn.danger-outline {
  background: #fff;
  color: var(--danger);
  border: 1px solid var(--danger);
  box-sizing: border-box;
}
.btn.warn-outline {
  background: #fff;
  color: var(--warning);
  border: 1px solid var(--warning);
  box-sizing: border-box;
}
.btn.primary-outline {
  background: #fff;
  color: var(--brand);
  border: 1px solid var(--brand);
  box-sizing: border-box;
}
.waiting {
  text-align: center;
  color: var(--sub);
  font-size: 15px;
  padding: 12px 0;
}
.opts {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}
.opt {
  min-height: 40px;
  display: inline-flex;
  align-items: center;
  padding: 6px 16px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  font-size: 15px;
  color: var(--txt);
  background: var(--panel);
}
.opt.active {
  border-color: var(--brand);
  color: var(--brand);
  background: var(--brand-weak);
  font-weight: 600;
}
.textarea {
  width: 100%;
  box-sizing: border-box;
  min-height: 100px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  padding: 12px;
  font-size: 15px;
  background: #f8fafc;
}
.field-input {
  border: 1px solid var(--line);
  border-radius: var(--radius);
  padding: 12px;
  min-height: 46px;
  font-size: 15px;
  margin-bottom: 12px;
  background: #f8fafc;
}
.tip-line {
  font-size: 14px;
  color: var(--sub);
  background: var(--brand-weak);
  border-radius: var(--radius);
  padding: 10px 12px;
  margin-bottom: 12px;
  line-height: 1.6;
}
.switch-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 0;
  border-bottom: 1px solid var(--line);
}
.switch-label {
  display: flex;
  flex-direction: column;
  font-size: 16px;
}
.kv {
  display: flex;
  gap: 8px;
  align-items: center;
  font-size: 15px;
  margin-bottom: 8px;
}
.kv .k {
  color: var(--sub);
  flex: none;
  width: 70px;
}
.link {
  color: var(--brand);
  font-size: 14px;
  font-weight: 600;
}
.err {
  color: var(--danger);
}
.sub {
  color: var(--sub);
  font-size: 12px;
}
.photo-row {
  display: flex;
  gap: 8px;
  margin: 8px 0 4px;
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
  font-size: 13px;
}
</style>
