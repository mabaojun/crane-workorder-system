<script setup lang="ts">
// 工单卡片：司机/客户共用，字段按需展示
import StatusTag from './StatusTag.vue'
import { fmtDateTime } from '../utils'

defineProps<{
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
</script>

<template>
  <view class="order-card" :class="{ highlight }" @tap="$emit('tap')">
    <view class="head">
      <text class="order-no">{{ order.orderNo }}</text>
      <StatusTag
        :status="statusKey === 'customerVisibleStatus' ? order.customerVisibleStatus ?? '' : order.status ?? ''"
        :label="statusKey === 'customerVisibleStatus' ? order.customerVisibleStatusLabel : undefined"
      />
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
  background: var(--panel);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  padding: 12px 16px;
}
.order-card.highlight {
  border-color: var(--brand);
}
.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.order-no {
  font-size: 13px;
  color: var(--sub);
  font-variant-numeric: tabular-nums;
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
