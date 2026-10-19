<template>
  <div class="timeline">
    <div class="track">
      <div
        v-for="bar in bars"
        :key="bar.order.id"
        class="bar"
        :class="{ pending: bar.pending, ongoing: bar.order.ongoing }"
        :style="{ left: `${bar.left}%`, width: `${bar.width}%`, background: bar.color }"
        :title="bar.title"
        @click="$emit('select', bar.order)"
      >
        <span v-if="bar.width > 14 && !bar.pending" class="bar-text">
          {{ bar.order.vehiclePlateNo || bar.order.taskName }}
        </span>
      </div>
      <div v-if="nowPercent != null" class="now-line" :style="{ left: `${nowPercent}%` }" />
    </div>
    <div class="axis">
      <span v-for="hour in HOURS" :key="hour" :style="{ left: `${(hour / 24) * 100}%` }">
        {{ String(hour).padStart(2, '0') }}:00
      </span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import dayjs from 'dayjs'
import type { StatusBoardOrder } from '@crane/shared/api/stat'
import { ORDER_STATUS_COLOR, fmtOrderDuration, fmtRange, timelineRect } from '../utils/status'

const props = defineProps<{
  orders: StatusBoardOrder[]
  date: string
}>()

defineEmits<{ (e: 'select', order: StatusBoardOrder): void }>()

const HOURS = [0, 6, 12, 18, 24]

interface BarItem {
  order: StatusBoardOrder
  left: number
  width: number
  pending: boolean
  color: string
  title: string
}

const bars = computed<BarItem[]>(() =>
  props.orders
    .map((order) => {
      const rect = timelineRect(order, props.date)
      if (!rect) return null
      return {
        order,
        ...rect,
        color: ORDER_STATUS_COLOR[order.status] || '#909399',
        title: `${order.orderNo} ${order.taskName}\n${order.staffName || '未派单'} · ${order.vehiclePlateNo || '无车辆'}\n${fmtRange(order)} · ${fmtOrderDuration(order)}`
      }
    })
    .filter((bar): bar is BarItem => bar !== null)
)

const nowPercent = computed(() => {
  const today = dayjs().format('YYYY-MM-DD')
  if (today !== props.date) return null
  const minutes = dayjs().diff(dayjs(props.date).startOf('day'), 'minute')
  if (minutes < 0 || minutes > 1440) return null
  return (minutes / 1440) * 100
})
</script>

<style scoped>
.timeline {
  width: 100%;
  padding-top: 2px;
}
.track {
  position: relative;
  height: 14px;
  border-radius: 7px;
  background: var(--line);
  overflow: visible;
}
.bar {
  position: absolute;
  top: 0;
  height: 14px;
  min-width: 2px;
  border-radius: 7px;
  cursor: pointer;
  opacity: 0.92;
  transition: opacity 0.15s;
}
.bar:hover {
  opacity: 1;
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.28);
}
.bar.pending {
  width: 8px !important;
  height: 8px;
  top: 3px;
  transform: translateX(-4px);
  border-radius: 50%;
}
.bar.ongoing {
  box-shadow: 0 0 0 2px rgba(103, 194, 58, 0.25);
}
.bar-text {
  position: absolute;
  left: 6px;
  top: 0;
  line-height: 14px;
  font-size: 10px;
  color: #fff;
  white-space: nowrap;
  pointer-events: none;
}
.now-line {
  position: absolute;
  top: -3px;
  bottom: -3px;
  width: 1px;
  background: #f56c6c;
  pointer-events: none;
}
.axis {
  position: relative;
  height: 14px;
  margin-top: 2px;
}
.axis span {
  position: absolute;
  top: 0;
  transform: translateX(-50%);
  font-size: 10px;
  color: var(--sub);
  white-space: nowrap;
}
.axis span:first-child {
  transform: none;
}
.axis span:last-child {
  transform: translateX(-100%);
}
</style>
