<template>
  <ScreenPanel title="报警中心" class="alarm-panel">
    <template #extra>
      <span class="alarm-count" v-if="count">未处理 {{ count }}</span>
    </template>
    <div class="alarm-disclaimer">AI 建议仅供参考 · 报警仅系统内推送</div>
    <div v-if="list.length" class="alarm-list">
      <div
        v-for="a in list"
        :key="a.alarmNo"
        class="alarm-row"
        :class="isHigh(a) ? 'level-high' : 'level-mid'"
      >
        <div class="alarm-top">
          <i class="alarm-dot" />
          <span class="alarm-type">{{ typeLabel(a.type) }}</span>
          <span class="alarm-target" v-if="targetText(a)">{{ targetText(a) }}</span>
          <span class="alarm-duration num" v-if="a.durationMinutes != null">
            持续 {{ a.durationMinutes }} 分钟
          </span>
          <span class="alarm-time num">{{ fmtHM(a.triggeredAt) }}</span>
        </div>
        <div class="alarm-sub" :title="a.summary">{{ a.summary }}</div>
      </div>
    </div>
    <div v-else class="alarm-empty">当前无待处理报警</div>
  </ScreenPanel>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { StatAlarmItem, StatAlarmType } from '@crane/shared/api/stat'
import { statStore } from '../store/stat'
import ScreenPanel from './ScreenPanel.vue'
import { fmtHM } from '../utils/format'

const TYPE_LABELS: Record<StatAlarmType, string> = {
  CONFLICT: '司机工时冲突',
  NOT_ACCEPTED: '超时未接单',
  NOT_ARRIVED: '预定时间未到场',
  NOT_CHECKED_IN: '未执行签到',
  VEHICLE_OCCUPIED: '车辆长时占用'
}

const list = computed(() => statStore.alarms.list)
const count = computed(() => statStore.alarms.count)

// 高等级与冲突行实心红底白字（06 §7.3）
function isHigh(a: StatAlarmItem): boolean {
  return a.level === 'HIGH' || a.type === 'CONFLICT'
}

function typeLabel(type: StatAlarmType): string {
  return TYPE_LABELS[type] ?? type
}

function targetText(a: StatAlarmItem): string {
  return [a.staffName, a.plateNo, a.orderNo].filter(Boolean).join(' / ')
}
</script>

<style scoped>
.alarm-disclaimer {
  flex: none;
  font-size: 11px;
  color: var(--sub);
  padding-bottom: 6px;
}

.alarm-list {
  height: calc(100% - 24px);
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.alarm-row {
  flex: none;
  border-radius: 4px;
  padding: 8px 10px;
}

/* 高等级：实心红底白字 + 2.2s 呼吸光晕 + 红点闪烁 */
.level-high {
  background: var(--red);
  color: #fff;
  animation: breath 2.2s ease-in-out infinite;
}

.level-mid {
  background: var(--orange);
  color: #1a2233;
}

@keyframes breath {
  0%,
  100% {
    box-shadow: 0 0 0 0 rgba(251, 77, 99, 0);
  }
  50% {
    box-shadow: 0 0 16px 2px rgba(251, 77, 99, 0.85);
  }
}

.alarm-top {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  min-width: 0;
}

.alarm-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #fff;
  flex: none;
}

.level-high .alarm-dot {
  animation: blink 1s ease-in-out infinite;
}

@keyframes blink {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.15;
  }
}

.alarm-type {
  flex: none;
}

.alarm-target {
  flex: 1;
  min-width: 0;
  font-weight: 400;
  font-size: 13px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  opacity: 0.92;
}

.alarm-duration {
  flex: none;
  font-weight: 400;
  font-size: 12px;
  opacity: 0.92;
}

.alarm-time {
  flex: none;
  font-weight: 400;
  font-size: 12px;
  opacity: 0.92;
}

.alarm-sub {
  margin-top: 4px;
  font-size: 12px;
  font-weight: 400;
  opacity: 0.88;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.alarm-count {
  color: var(--red);
  font-weight: 600;
}

.alarm-empty {
  height: calc(100% - 24px);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--sub);
  font-size: 13px;
}
</style>
