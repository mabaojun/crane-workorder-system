<script setup lang="ts">
// 状态标签：状态字面量 → 语义色（11-前端设计手册 §2.2 全端唯一语义色）
// size=lg 用于工单卡片，放大状态视觉占比；live 态圆点带呼吸动效
import { computed } from 'vue'
import { WORK_ORDER_STATUS_LABEL, DEMAND_ORDER_STATUS_LABEL } from '@crane/shared'

const props = defineProps<{ status: string; label?: string; size?: 'md' | 'lg' }>()

const COLOR: Record<string, string> = {
  PENDING_ACCEPT: '#d97706',
  PENDING_ARRIVE: '#0284c7',
  IN_PROGRESS: '#1a6bff',
  SUSPENDED: '#dc2626',
  COMPLETED: '#059669',
  CANCELLED: '#64748b',
  DISPATCHED: '#d97706',
  ACCEPTED: '#1a6bff',
  STARTED: '#1a6bff',
  PAUSED: '#dc2626',
  FINISHED: '#059669',
  PENDING: '#d97706',
  REVIEWING: '#1a6bff',
  CONVERTED: '#059669',
  REJECTED: '#dc2626',
  APPROVED: '#059669',
  CLOSED: '#64748b'
}

const text = computed(
  () =>
    props.label ??
    WORK_ORDER_STATUS_LABEL[props.status] ??
    DEMAND_ORDER_STATUS_LABEL[props.status] ?? { DISPATCHED: '已派单', ACCEPTED: '已接单', STARTED: '进行中', PAUSED: '已暂停', FINISHED: '已完成', CANCELLED: '已取消' }[props.status] ??
    props.status
)
const color = computed(() => COLOR[props.status] ?? '#64748b')
const background = computed(() => rgba(color.value, 0.1))
const borderColor = computed(() => rgba(color.value, 0.3))
const isLive = computed(() => ['IN_PROGRESS', 'STARTED'].includes(props.status))

/** #RRGGBB → rgba()，用于浅色底与描边，保持同一语义色 */
function rgba(hex: string, alpha: number): string {
  const value = hex.replace('#', '')
  const r = parseInt(value.slice(0, 2), 16)
  const g = parseInt(value.slice(2, 4), 16)
  const b = parseInt(value.slice(4, 6), 16)
  return `rgba(${r}, ${g}, ${b}, ${alpha})`
}
</script>

<template>
  <view
    class="status-tag"
    :class="[size === 'lg' ? 'lg' : '', { live: isLive }]"
    :style="{ color, background, borderColor }"
  >
    <view class="dot" :style="{ background: color }" />
    <text>{{ text }}</text>
  </view>
</template>

<style scoped>
.status-tag {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 3px 10px;
  border-radius: 6px;
  border: 1px solid transparent;
  font-size: 13px;
  font-weight: 600;
  line-height: 22px;
  white-space: nowrap;
}
.dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
}
/* 卡片专用大号状态：更高视觉占比，圆角胶囊 */
.status-tag.lg {
  gap: 7px;
  padding: 5px 13px;
  border-radius: 999px;
  font-size: 15px;
  font-weight: 700;
  line-height: 24px;
  box-shadow: 0 2px 8px rgba(15, 31, 61, 0.06);
}
.status-tag.lg .dot {
  width: 8px;
  height: 8px;
}
.status-tag.lg.live .dot {
  animation: status-pulse 1.8s ease-in-out infinite;
}
@keyframes status-pulse {
  0%,
  100% {
    box-shadow: 0 0 0 0 rgba(26, 107, 255, 0.35);
  }
  50% {
    box-shadow: 0 0 0 4px rgba(26, 107, 255, 0);
  }
}
</style>
