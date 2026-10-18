<script setup lang="ts">
// 司机端首页（T-31）：首页即当前工单，状态化操作按钮
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { ApiError } from '@crane/shared/api/client'
import BrandBar from '../../components/BrandBar.vue'
import OrderCard from '../../components/OrderCard.vue'
import EmptyState from '../../components/EmptyState.vue'
import BottomSheet from '../../components/BottomSheet.vue'
import RoleTabbar from '../../components/RoleTabbar.vue'
import {
  acceptOrder,
  checkIn,
  createExceptionReport,
  dailyCheckout,
  listOrders,
  rejectOrder,
  taskEnd
} from '../../api/modules/biz'
import type { WorkOrder } from '../../api/types'
import { chooseImages, getLocation, toastError, toastOk } from '../../utils'
import { openNavigation, navFromOrder } from '../../utils/navigation'

// 状态机活跃态（终态不展示为当前工单）
const PRIORITY = ['PENDING_ACCEPT', 'PENDING_ARRIVE', 'IN_PROGRESS', 'SUSPENDED']

const loading = ref(false)
const order = ref<WorkOrder | null>(null)

// ---- 拒单弹层 ----
const showReject = ref(false)
const REJECT_TYPES = [
  { value: 'VEHICLE_FAULT', label: '车辆故障' },
  { value: 'TIME_CONFLICT', label: '时间冲突' },
  { value: 'CAPABILITY_MISMATCH', label: '能力不匹配' },
  { value: 'OTHER', label: '其他原因' }
]
const rejectType = ref('OTHER')
const rejectReason = ref('')

// ---- 异常上报弹层 ----
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

// ---- 今日任务完成弹层 ----
const showCheckout = ref(false)
const isLeftSite = ref(true)
const isReturned = ref(false)

// ---- 到场签到弹层 ----
const showCheckin = ref(false)
const locating = ref(false)
const coords = ref<{ longitude: number; latitude: number } | null>(null)
const photos = ref<string[]>([])

async function load() {
  loading.value = true
  try {
    const res = await listOrders({ pageNum: 1, pageSize: 20 })
    const actives = (res.list ?? []).filter((o) => PRIORITY.includes(o.status))
    actives.sort((a, b) => PRIORITY.indexOf(a.status) - PRIORITY.indexOf(b.status))
    order.value = actives[0] ?? null
  } catch (e) {
    toastError(e)
  } finally {
    loading.value = false
  }
}

onShow(load)

function needOrder(): WorkOrder {
  if (!order.value) throw new Error('当前没有工单')
  return order.value
}

/** 一键导航：无坐标时统一 toast 提示 */
function onNavigate() {
  const o = order.value
  if (!o) return
  const target = navFromOrder(o)
  if (!target) {
    uni.showToast({ title: '该工单无位置坐标', icon: 'none' })
    return
  }
  openNavigation(target)
}

async function onAccept() {
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

function onRejectOpen() {
  rejectType.value = 'OTHER'
  rejectReason.value = ''
  showReject.value = true
}

async function onRejectSubmit() {
  if (!rejectReason.value.trim()) {
    uni.showToast({ title: '请填写原因说明', icon: 'none' })
    return
  }
  try {
    await rejectOrder(needOrder().id, { reasonType: rejectType.value, reason: rejectReason.value.trim() })
    showReject.value = false
    toastOk('已提交拒单')
    await load()
  } catch (e) {
    toastError(e)
  }
}

// ---- 到场签到 ----
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
  } catch (e) {
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
  if (!coords.value) {
    uni.showToast({ title: '尚未获取定位', icon: 'none' })
    return
  }
  try {
    uni.showLoading({ title: '签到中' })
    // TODO(二期): 对接文件上传，将本地照片（photos，base64 预览）上传后回填 photoUrls；MVP 固定占位路径
    await checkIn({
      orderId: o.id,
      longitude: coords.value.longitude,
      latitude: coords.value.latitude,
      photoUrls: ['/files/mock/checkin.jpg']
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
  } catch (e) {
    toastError(e)
  }
}

// ---- 今日任务完成 / 任务结束 ----
function onCheckoutOpen() {
  isLeftSite.value = true
  isReturned.value = false
  showCheckout.value = true
}

async function onCheckoutSubmit() {
  const o = needOrder()
  try {
    uni.showLoading({ title: '提交中' })
    const loc = await getLocation()
    await dailyCheckout({
      orderId: o.id,
      longitude: loc.longitude,
      latitude: loc.latitude,
      isLeftSite: isLeftSite.value,
      isReturned: isReturned.value
    })
    uni.hideLoading()
    showCheckout.value = false
    toastOk('今日任务已完成')
    await load()
  } catch (e) {
    uni.hideLoading()
    toastError(e)
  }
}

async function onTaskEnd() {
  const o = needOrder()
  uni.showModal({
    title: '确认任务结束',
    content: '任务结束将自动结单并归档，确认已完工？',
    success: async (r) => {
      if (!r.confirm) return
      try {
        uni.showLoading({ title: '提交中' })
        const loc = await getLocation()
        // TODO(二期): 对接文件上传；MVP 照片路径传占位
        await taskEnd({
          orderId: o.id,
          longitude: loc.longitude,
          latitude: loc.latitude,
          photoUrls: ['/files/mock/checkin.jpg']
        })
        uni.hideLoading()
        toastOk('任务已结束')
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
    <BrandBar />
    <scroll-view scroll-y class="content">
      <EmptyState
        v-if="!loading && !order"
        text="当前没有进行中的工单"
        hint="有新派单时将在这里展示"
      />

      <view v-if="order" class="order-wrap">
        <view v-if="order.status === 'SUSPENDED'" class="paused-tip">已暂停，等待管理员批复</view>

        <OrderCard
          :order="{
            orderNo: order.orderNo,
            taskName: order.taskName,
            customerName: order.customerName,
            address: order.address,
            longitude: order.longitude,
            latitude: order.latitude,
            expectedStartAt: order.expectedStartAt
          }"
          status-key="status"
          highlight
          show-nav
          @navigate="onNavigate"
        />

        <view class="info-extra">
          <view v-if="order.craneTonnage" class="kv"><text class="k">吨位</text><text>{{ order.craneTonnage }} 吨</text></view>
          <view v-if="order.cargoName" class="kv"><text class="k">货物</text><text>{{ order.cargoName }}{{ order.cargoWeight ? ' / ' + order.cargoWeight + ' 吨' : '' }}</text></view>
          <view v-if="order.staffName" class="kv"><text class="k">联系人</text><text>{{ order.staffName }}</text></view>
        </view>

        <!-- 状态化操作按钮：核心操作高度 48px，全宽 -->
        <view class="actions">
          <template v-if="order.status === 'PENDING_ACCEPT'">
            <button class="btn primary" @tap="onAccept">接单</button>
            <button class="btn danger-outline" @tap="onRejectOpen">拒单</button>
          </template>

          <template v-else-if="order.status === 'PENDING_ARRIVE'">
            <button class="btn primary" @tap="openCheckin">到场签到</button>
          </template>

          <template v-else-if="order.status === 'IN_PROGRESS'">
            <button class="btn warn-outline" @tap="onExceptionOpen">异常上报</button>
            <button class="btn primary" @tap="onCheckoutOpen">今日任务完成</button>
            <button class="btn danger" @tap="onTaskEnd">任务结束</button>
          </template>

          <view v-else-if="order.status === 'SUSPENDED'" class="waiting">操作暂不可用</view>
        </view>
      </view>
    </scroll-view>

    <!-- 拒单弹层 -->
    <BottomSheet v-if="showReject" title="拒单原因" @close="showReject = false">
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
      <button class="btn primary block" @tap="onRejectSubmit">提交拒单</button>
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

    <!-- 今日任务完成弹层 -->
    <BottomSheet v-if="showCheckout" title="今日任务完成" @close="showCheckout = false">
      <view class="tip-line">今日任务完成仅结束当日施工打卡，工单保持进行中，不会结单。</view>
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

    <RoleTabbar role="driver" current="pages/driver/index" />
  </view>
</template>

<style scoped>
.page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--bg);
}
.content {
  flex: 1;
  padding: 12px 16px 88px;
  box-sizing: border-box;
}
.order-wrap {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.paused-tip {
  background: #fef2f2;
  border: 1px solid #fecaca;
  color: var(--danger);
  border-radius: var(--radius);
  padding: 10px 16px;
  font-size: 15px;
}
.info-extra {
  background: var(--panel);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  padding: 10px 16px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 13px;
}
.kv {
  display: flex;
  gap: 8px;
  align-items: center;
  font-size: 14px;
}
.k {
  color: var(--sub);
  flex: none;
}
.sub {
  color: var(--sub);
  font-size: 12px;
}
.link {
  color: var(--brand);
  font-size: 13px;
}
.err {
  color: var(--danger);
}
.actions {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 4px;
}
.btn {
  height: 48px;
  line-height: 48px;
  border-radius: var(--radius);
  font-size: 17px;
  font-weight: 600;
  border: none;
}
.btn::after {
  border: none;
}
.btn.block {
  margin-top: 16px;
}
.btn.primary {
  background: var(--brand);
  color: #fff;
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
.waiting {
  text-align: center;
  color: var(--sub);
  font-size: 14px;
  padding: 12px 0;
}
.opts {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}
.opt {
  padding: 6px 14px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  font-size: 14px;
  color: var(--txt);
  background: var(--panel);
}
.opt.active {
  border-color: var(--brand);
  color: var(--brand);
  background: var(--brand-weak);
}
.textarea {
  width: 100%;
  box-sizing: border-box;
  min-height: 96px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  padding: 10px;
  font-size: 14px;
}
.field-input {
  border: 1px solid var(--line);
  border-radius: var(--radius);
  padding: 10px;
  font-size: 14px;
  margin-bottom: 12px;
}
.tip-line {
  font-size: 13px;
  color: var(--sub);
  background: var(--brand-weak);
  border-radius: var(--radius);
  padding: 8px 12px;
  margin-bottom: 12px;
}
.switch-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 0;
  border-bottom: 1px solid var(--line);
}
.switch-label {
  display: flex;
  flex-direction: column;
  font-size: 15px;
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
