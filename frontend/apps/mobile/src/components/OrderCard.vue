<script setup lang="ts">
// 工单卡片：司机/客户共用，字段按需展示
// 状态差异化视觉：六态专属背景填充+左侧状态色条，1 秒识别工单状态（11-前端设计手册语义色）
import { computed } from 'vue'
import StatusTag from './StatusTag.vue'
import { fmtDateTime } from '../utils'

const props = defineProps<{
  order: {
    orderNo: string
    taskName: string
    status?: string
    customerVisibleStatus?: string
    customerVisibleStatusLabel?: string
    customerName?: string
    staffName?: string
    vehiclePlateNo?: string
    address?: string
    longitude?: number
    latitude?: number
    expectedStartAt?: string
  }
  /** 用哪套状态：司机看 status，客户看 customerVisibleStatus */
  statusKey?: 'status' | 'customerVisibleStatus'
  highlight?: boolean
  /** 位置行显示「一键导航」按钮（有坐标时） */
  showNav?: boolean
}>()

defineEmits<{ (e: 'tap'): void; (e: 'navigate'): void }>()

/** 状态 → 卡片视觉基调 class */
const TONE: Record<string, string> = {
  PENDING_ACCEPT: 't-pending',
  DISPATCHED: 't-pending',
  PENDING_ARRIVE: 't-arrive',
  ACCEPTED: 't-arrive',
  IN_PROGRESS: 't-progress',
  STARTED: 't-progress',
  SUSPENDED: 't-suspend',
  PAUSED: 't-suspend',
  COMPLETED: 't-done',
  FINISHED: 't-done',
  CANCELLED: 't-cancel',
  CLOSED: 't-cancel'
}

const status = computed(() =>
  props.statusKey === 'customerVisibleStatus'
    ? props.order.customerVisibleStatus ?? ''
    : props.order.status ?? ''
)
const tone = computed(() => TONE[status.value] ?? '')
</script>

<template>
  <view class="order-card" :class="[tone, { highlight }]" @tap="$emit('tap')">
    <view class="head">
      <text class="order-no">{{ order.orderNo }}</text>
      <StatusTag
        :status="status"
        :label="statusKey === 'customerVisibleStatus' ? order.customerVisibleStatusLabel : undefined"
      />
      <view class="chev" />
    </view>
    <view class="title">{{ order.taskName || '吊装任务' }}</view>
    <view class="rows">
      <view v-if="order.customerName" class="row"><text class="k">客户</text><text>{{ order.customerName }}</text></view>
      <view v-if="order.staffName" class="row"><text class="k">司机</text><text>{{ order.staffName }}{{ order.vehiclePlateNo ? ' / ' + order.vehiclePlateNo : '' }}</text></view>
      <view class="row">
        <text class="k">地点</text>
        <text class="addr">{{ order.address || '-' }}</text>
        <view
          v-if="showNav && order.longitude != null && order.latitude != null"
          class="nav-btn"
          @tap.stop="$emit('navigate')"
        >一键导航</view>
      </view>
      <view v-if="order.expectedStartAt" class="row"><text class="k">计划</text><text>{{ fmtDateTime(order.expectedStartAt) }}</text></view>
    </view>
  </view>
</template>

<style scoped>
.order-card {
  position: relative;
  overflow: hidden;
  background: var(--panel);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  padding: 12px 16px 12px 20px;
}
/* 左侧状态色条 */
.order-card::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 4px;
}
/* 待办：琥珀 */
.order-card.t-pending {
  background: linear-gradient(135deg, #fffbeb, #fef3c7);
  border-color: #fcd34d;
}
.order-card.t-pending::before { background: #d97706; }
/* 待执行：明黄 */
.order-card.t-arrive {
  background: linear-gradient(135deg, #fefce8, #fef9c3);
  border-color: #fde047;
}
.order-card.t-arrive::before { background: #ca8a04; }
/* 进行中：品牌蓝（最醒目） */
.order-card.t-progress {
  background: linear-gradient(135deg, #eff6ff, #dbeafe);
  border-color: #93c5fd;
}
.order-card.t-progress::before { background: #1d4ed8; }
/* 暂停：红 */
.order-card.t-suspend {
  background: linear-gradient(135deg, #fef2f2, #fee2e2);
  border-color: #fca5a5;
}
.order-card.t-suspend::before { background: #dc2626; }
/* 已完成：绿 */
.order-card.t-done {
  background: linear-gradient(135deg, #f0fdf4, #dcfce7);
  border-color: #86efac;
}
.order-card.t-done::before { background: #16a34a; }
/* 已取消：灰 */
.order-card.t-cancel {
  background: #f9fafb;
  border-color: #e5e7eb;
}
.order-card.t-cancel::before { background: #9ca3af; }
.order-card.highlight {
  border-color: var(--brand);
}
.head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.order-no {
  flex: 1;
  font-size: 13px;
  color: var(--sub);
  font-variant-numeric: tabular-nums;
}
/* 可点击箭头 */
.chev {
  flex: none;
  width: 8px;
  height: 8px;
  border-top: 2px solid var(--sub);
  border-right: 2px solid var(--sub);
  transform: rotate(45deg);
  opacity: 0.6;
}
.title {
  font-size: 17px;
  font-weight: 600;
  color: var(--txt);
  margin: 4px 0 8px;
}
.rows {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.row {
  display: flex;
  gap: 8px;
  font-size: 13px;
  color: var(--txt);
}
.k {
  color: var(--sub);
  flex: none;
}
.addr {
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}
.nav-btn {
  flex: none;
  margin-left: auto;
  font-size: 12px;
  color: var(--brand);
  border: 1px solid var(--brand);
  border-radius: var(--radius);
  padding: 2px 8px;
}
</style>
