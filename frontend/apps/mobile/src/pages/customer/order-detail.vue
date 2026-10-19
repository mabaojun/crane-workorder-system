<script setup lang="ts">
// 客户端工单详情（T-33）：状态步骤条 + 工单信息 + 异常公开卡 + 完成后评价
import { computed, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import StatusTag from '../../components/StatusTag.vue'
import EmptyState from '../../components/EmptyState.vue'
import SectionCard from '../../components/SectionCard.vue'
import { getOrderDetail, listExceptionReports, submitReview } from '../../api/modules/biz'
import type { ExceptionRecord, WorkOrder } from '../../api/types'
import { VEHICLE_TYPE_LABEL } from '@crane/shared'
import { fmtDateTime, fmtTimeWithDayHint, toastError, toastOk } from '../../utils'

const STEPS = ['已派单', '已接单', '进行中', '已完成']
const STEP_INDEX: Record<string, number> = {
  DISPATCHED: 0,
  ACCEPTED: 1,
  STARTED: 2,
  PAUSED: 2,
  FINISHED: 3,
  CANCELLED: -1
}

const orderId = ref<number | string>('')
const order = ref<WorkOrder | null>(null)
const exceptions = ref<ExceptionRecord[]>([])
const loading = ref(true)

const stepIndex = computed(() => {
  if (!order.value) return -1
  return STEP_INDEX[order.value.customerVisibleStatus] ?? -1
})

// ---- 评价 ----
const score = ref(0)
const content = ref('')
const submitting = ref(false)

async function load() {
  loading.value = true
  try {
    order.value = await getOrderDetail(orderId.value)
    const exc = await listExceptionReports({ orderId: orderId.value, pageNum: 1, pageSize: 20 })
    // 客户可见异常：后端已按数据范围过滤，这里再按公开标记兜底过滤
    exceptions.value = (exc.list ?? []).filter((x) => x.isPublicToCustomer === 1)
  } catch (e) {
    toastError(e)
  } finally {
    loading.value = false
  }
}

onLoad((opts) => {
  orderId.value = opts?.id ?? ''
})

onShow(() => {
  if (orderId.value) load()
})

/** 拨打执行司机电话 */
function callDriver() {
  const phone = order.value?.staffPhone
  if (!phone) {
    uni.showToast({ title: '暂无司机电话', icon: 'none' })
    return
  }
  uni.makePhoneCall({ phoneNumber: phone })
}

async function submit() {
  if (!score.value) {
    uni.showToast({ title: '请先选择评分', icon: 'none' })
    return
  }
  submitting.value = true
  try {
    await submitReview(orderId.value, { score: score.value, content: content.value.trim() || undefined })
    toastOk('评价已提交')
  } catch (e) {
    toastError(e)
  } finally {
    submitting.value = false
  }
}

const EXC_LABEL: Record<string, string> = {
  RAIN: '下雨',
  PARTY_STOP: '双方停工',
  VEHICLE_FAULT: '车辆故障',
  CUSTOM: '其他原因'
}
const EXC_STATUS: Record<string, string> = {
  REPORTED: '待处理',
  REPLYING: '处理中',
  REPLIED: '已批复',
  CLOSED: '已关闭'
}
</script>

<template>
  <view class="page">
    <view v-if="loading" class="loading">加载中...</view>
    <EmptyState v-else-if="!order" text="工单不存在或无权查看" />

    <template v-else>
      <!-- 步骤条 -->
      <view v-if="stepIndex >= 0" class="steps">
        <view v-for="(s, i) in STEPS" :key="s" class="step" :class="{ done: i <= stepIndex }">
          <view class="dot" />
          <text>{{ s }}</text>
          <view v-if="i < STEPS.length - 1" class="bar" :class="{ done: i < stepIndex }" />
        </view>
      </view>
      <view v-else class="cancelled-tip">任务已取消</view>

      <!-- 工单信息 -->
      <SectionCard>
        <view class="head">
          <text class="no">{{ order.orderNo }}</text>
          <StatusTag
            :status="order.customerVisibleStatus"
            :label="order.customerVisibleStatusLabel"
          />
        </view>
        <view class="title">{{ order.taskName || '吊装任务' }}</view>
        <view class="row"><text class="k">施工地址</text><text>{{ order.address || '-' }}</text></view>
        <view class="row"><text class="k">计划开始</text><text>{{ fmtTimeWithDayHint(order.expectedStartAt) }}</text></view>
        <view class="row link-row" @tap="callDriver">
          <text class="k">司机</text>
          <text>{{ order.staffName || '-' }}</text>
          <text v-if="order.staffPhone" class="tel">拨打</text>
        </view>
        <view class="row"><text class="k">用车种类</text><text>{{ VEHICLE_TYPE_LABEL[order.vehicleType || 'CRANE'] }}</text></view>
        <view class="row"><text class="k">车牌</text><text>{{ order.vehiclePlateNo || '-' }}</text></view>
        <view v-if="!order.vehicleType || order.vehicleType === 'CRANE'" class="row"><text class="k">吊车吨位</text><text>{{ order.craneTonnage ? order.craneTonnage + ' 吨' : '-' }}</text></view>
        <view class="row"><text class="k">货物名称</text><text>{{ order.cargoName || '-' }}</text></view>
        <view class="row"><text class="k">货物重量</text><text>{{ order.cargoWeight ? order.cargoWeight + ' 吨' : '-' }}</text></view>
        <view v-if="order.estimatedDays" class="row"><text class="k">预计使用</text><text>{{ order.estimatedDays }} 天</text></view>
        <view class="row"><text class="k">完成时间</text><text>{{ fmtDateTime(order.completedAt) }}</text></view>
        <view v-if="order.remark" class="remark-block">
          <text class="remark-label">下单备注</text>
          <text class="remark-text">{{ order.remark }}</text>
        </view>
      </SectionCard>

      <!-- 异常公开卡 -->
      <SectionCard v-if="exceptions.length" title="异常情况">
        <view v-for="x in exceptions" :key="x.id" class="exc">
          <view class="exc-head">
            <text class="exc-type">{{ x.typeLabel || EXC_LABEL[x.type] || x.type }}</text>
            <text class="exc-status">{{ EXC_STATUS[x.status] || x.status }}</text>
            <text class="exc-time">{{ fmtDateTime(x.reportedAt) }}</text>
          </view>
          <view class="exc-desc">{{ x.description }}</view>
        </view>
      </SectionCard>

      <!-- 评价（已完成） -->
      <SectionCard v-if="order.status === 'COMPLETED'" title="服务评价">
        <view class="stars">
          <view
            v-for="i in 5"
            :key="i"
            class="star"
            :class="{ active: i <= score }"
            @tap="score = i"
          >
            <view class="star-shape" />
          </view>
          <text class="score-text">{{ score ? score + ' 分' : '点击评分' }}</text>
        </view>
        <textarea v-model="content" class="textarea" placeholder="说说本次服务的感受（选填）" :maxlength="500" />
        <button class="submit" :disabled="submitting" @tap="submit">提交评价</button>
      </SectionCard>
    </template>
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
.steps {
  background: var(--panel);
  border: 1px solid var(--line);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow);
  padding: 18px 16px;
  display: flex;
}
.step {
  flex: 1;
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 5px;
  font-size: 13px;
  color: var(--sub);
}
.step .dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  border: 2px solid var(--line);
  background: #fff;
  box-sizing: border-box;
  z-index: 1;
}
.step .bar {
  position: absolute;
  top: 5px;
  left: calc(50% + 8px);
  right: calc(-50% + 8px);
  height: 2px;
  background: var(--line);
}
.step.done {
  color: var(--brand);
}
.step.done .dot {
  border-color: var(--brand);
  background: var(--brand);
}
.bar.done {
  background: var(--brand);
}
.cancelled-tip {
  background: #f3f4f6;
  color: var(--sub);
  border-radius: var(--radius);
  padding: 14px 16px;
  font-size: 15px;
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
  font-size: 19px;
  font-weight: 700;
  margin: 5px 0 10px;
  line-height: 1.4;
}
.row {
  display: flex;
  gap: 8px;
  font-size: 15px;
  margin-bottom: 8px;
  line-height: 1.55;
}
.k {
  color: var(--sub);
  flex: none;
  width: 70px;
  font-size: 14px;
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
.exc {
  border-bottom: 1px solid var(--line);
  padding: 8px 0;
}
.exc:last-child {
  border-bottom: none;
}
.exc-head {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}
.exc-type {
  font-weight: 600;
  color: var(--danger);
}
.exc-status {
  color: var(--sub);
}
.exc-time {
  margin-left: auto;
  color: var(--sub);
  font-size: 12px;
}
.exc-desc {
  font-size: 14px;
  margin-top: 4px;
}
.stars {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}
.star {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.star-shape {
  width: 24px;
  height: 24px;
  background: var(--line);
  clip-path: polygon(50% 0%, 61% 35%, 98% 35%, 68% 57%, 79% 91%, 50% 70%, 21% 91%, 32% 57%, 2% 35%, 39% 35%);
}
.star.active .star-shape {
  background: #eab308;
}
.score-text {
  font-size: 13px;
  color: var(--sub);
  margin-left: 4px;
}
.textarea {
  width: 100%;
  box-sizing: border-box;
  min-height: 96px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  padding: 12px;
  font-size: 15px;
  background: #f8fafc;
}
.submit {
  width: 100%;
  display: block;
  margin-top: 12px;
  height: 50px;
  line-height: 50px;
  border-radius: var(--radius);
  background: linear-gradient(135deg, #1a6bff, #0b4fd6);
  color: #fff;
  font-size: 17px;
  font-weight: 700;
  box-shadow: 0 6px 16px rgba(26, 107, 255, 0.2);
}
.submit::after {
  border: none;
}
.submit[disabled] {
  opacity: 0.6;
  color: #fff;
  background: var(--brand);
}
</style>
