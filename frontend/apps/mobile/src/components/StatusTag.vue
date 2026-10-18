<script setup lang="ts">
// 状态标签：状态字面量 → 语义色（11-前端设计手册 §2.2 全端唯一语义色）
import { computed } from 'vue'
import { WORK_ORDER_STATUS_LABEL, DEMAND_ORDER_STATUS_LABEL } from '@crane/shared'

const props = defineProps<{ status: string; label?: string }>()

const COLOR: Record<string, string> = {
  PENDING_ACCEPT: '#d97706',
  PENDING_ARRIVE: '#eab308',
  IN_PROGRESS: '#1d4ed8',
  SUSPENDED: '#dc2626',
  COMPLETED: '#16a34a',
  CANCELLED: '#6b7280',
  DISPATCHED: '#d97706',
  ACCEPTED: '#1d4ed8',
  STARTED: '#1d4ed8',
  PAUSED: '#dc2626',
  FINISHED: '#16a34a',
  PENDING: '#d97706',
  REVIEWING: '#1d4ed8',
  CONVERTED: '#16a34a',
  REJECTED: '#dc2626',
  CLOSED: '#6b7280'
}

const text = computed(
  () =>
    props.label ??
    WORK_ORDER_STATUS_LABEL[props.status] ??
    DEMAND_ORDER_STATUS_LABEL[props.status] ?? { DISPATCHED: '已派单', ACCEPTED: '已接单', STARTED: '进行中', PAUSED: '已暂停', FINISHED: '已完成', CANCELLED: '已取消' }[props.status] ??
    props.status
)
const color = computed(() => COLOR[props.status] ?? '#6b7280')
</script>

<template>
  <view class="status-tag" :style="{ color, background: 'rgba(0,0,0,0.04)', borderColor: color }">
    <view class="dot" :style="{ background: color }" />
    <text>{{ text }}</text>
  </view>
</template>

<style scoped>
.status-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 1px 8px;
  border-radius: 4px;
  border: 1px solid transparent;
  font-size: 12px;
  line-height: 20px;
  white-space: nowrap;
}
.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
}
</style>
