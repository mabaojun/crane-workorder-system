<script setup lang="ts">
// 工单卡片：司机/客户共用，字段按需展示
// 科技简约卡片：大号状态胶囊 + 左侧状态色条 + 图标化字段对齐，1 秒识别工单状态
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
        size="lg"
        :status="status"
        :label="statusKey === 'customerVisibleStatus' ? order.customerVisibleStatusLabel : undefined"
      />
    </view>

    <view class="title-line">
      <text class="title">{{ order.taskName || '吊装任务' }}</text>
      <uni-icons type="right" size="18" color="#9db0c9" />
    </view>

    <view class="rows">
      <view v-if="order.customerName" class="row">
        <view class="k"><uni-icons type="person" size="15" color="#7b8ca6" /><text>客户</text></view>
        <text class="v">{{ order.customerName }}</text>
      </view>
      <view v-if="order.staffName" class="row">
        <view class="k"><uni-icons type="staff" size="15" color="#7b8ca6" /><text>司机</text></view>
        <text class="v">{{ order.staffName }}{{ order.vehiclePlateNo ? ' · ' + order.vehiclePlateNo : '' }}</text>
      </view>
      <view class="row">
        <view class="k"><uni-icons type="location" size="15" color="#7b8ca6" /><text>地点</text></view>
        <text class="v addr">{{ order.address || '-' }}</text>
        <view
          v-if="showNav && order.longitude != null && order.latitude != null"
          class="nav-btn"
          @tap.stop="$emit('navigate')"
        >
          <uni-icons type="navigate" size="13" color="#1a6bff" />
          <text>导航</text>
        </view>
      </view>
      <view v-if="order.expectedStartAt" class="row">
        <view class="k"><uni-icons type="calendar" size="15" color="#7b8ca6" /><text>计划</text></view>
        <text class="v">{{ fmtDateTime(order.expectedStartAt) }}</text>
      </view>
    </view>
  </view>
</template>

<style scoped>
.order-card {
  --tone: #94a3b8;
  --tint: #ffffff;
  position: relative;
  overflow: hidden;
  padding: 16px 18px 15px 22px;
  border-radius: 16px;
  border: 1px solid #e4ecf7;
  background: linear-gradient(140deg, #ffffff 0%, var(--tint) 100%);
  box-shadow: 0 4px 16px rgba(15, 31, 61, 0.055);
}
/* 左侧状态色条：弱化面积、保留状态识别 */
.order-card::before {
  content: '';
  position: absolute;
  left: 0;
  top: 14px;
  bottom: 14px;
  width: 4px;
  border-radius: 0 4px 4px 0;
  background: var(--tone);
}
/* 待接单：琥珀 */
.order-card.t-pending { --tone: #f59e0b; --tint: #fffaef; }
/* 待到场：天青 */
.order-card.t-arrive { --tone: #0ea5e9; --tint: #f1f9ff; }
/* 进行中：科技蓝（最醒目） */
.order-card.t-progress { --tone: #1a6bff; --tint: #eef5ff; }
/* 受阻：红 */
.order-card.t-suspend { --tone: #ef4444; --tint: #fff5f5; }
/* 已完成：绿 */
.order-card.t-done { --tone: #10b981; --tint: #f0fdf8; }
/* 已取消：灰 */
.order-card.t-cancel { --tone: #94a3b8; --tint: #f8fafc; }
.order-card:active {
  border-color: #bcd4f7;
}
.order-card.highlight {
  border-color: rgba(26, 107, 255, 0.45);
  box-shadow: 0 8px 24px rgba(26, 107, 255, 0.13);
}

/* 头部：工单号与状态分列两端 */
.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
.order-no {
  flex: 1;
  min-width: 0;
  font-size: 12px;
  letter-spacing: 0.6px;
  color: #8496b0;
  font-variant-numeric: tabular-nums;
}
.title-line {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 9px 0 12px;
}
.title {
  flex: 1;
  min-width: 0;
  font-size: 19px;
  font-weight: 700;
  line-height: 1.35;
  color: var(--txt);
}
.rows {
  display: flex;
  flex-direction: column;
  gap: 9px;
  padding-top: 11px;
  border-top: 1px dashed #e6eef9;
}
.row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  font-size: 15px;
  line-height: 1.5;
  color: var(--txt);
}
.k {
  flex: none;
  width: 58px;
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 13px;
  color: #7b8ca6;
  line-height: 23px;
}
.v {
  flex: 1;
  min-width: 0;
}
.addr {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  text-overflow: ellipsis;
}
.nav-btn {
  flex: none;
  display: inline-flex;
  align-items: center;
  gap: 3px;
  min-height: 32px;
  padding: 0 12px;
  border-radius: 999px;
  background: rgba(26, 107, 255, 0.08);
  color: var(--brand);
  font-size: 13px;
  font-weight: 600;
}
</style>
