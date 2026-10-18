<template>
  <ScreenPanel title="车辆状态看板" class="vehicle-panel">
    <template #extra>
      <span class="vehicle-total num">共 {{ vehicle ? vehicle.total : '--' }} 台</span>
    </template>
    <div class="vehicle-rows">
      <div v-for="row in rows" :key="row.label" class="vehicle-row">
        <i class="v-dot" :style="{ background: row.color }" />
        <span class="v-label">{{ row.label }}</span>
        <span class="v-count num">{{ vehicle ? row.value : '--' }}</span>
      </div>
      <div class="v-external">
        <span class="v-ext-label">外部占用（第三方派单）</span>
        <span class="v-ext-value">
          <b class="num">{{ vehicle ? vehicle.externalOccupyVehicles : '--' }}</b> 辆 ·
          当日 <b class="num">{{ vehicle ? vehicle.externalOccupyMinutesToday : '--' }}</b> 分钟
        </span>
      </div>
    </div>
  </ScreenPanel>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { statStore } from '../store/stat'
import ScreenPanel from './ScreenPanel.vue'

const vehicle = computed(() => statStore.dashboard?.vehicle ?? null)

const rows = computed(() => [
  { label: '空闲', value: vehicle.value?.idle ?? 0, color: 'var(--green)' },
  { label: '作业中', value: vehicle.value?.busy ?? 0, color: 'var(--cyan)' },
  { label: '第三方派单外出', value: vehicle.value?.busyThird ?? 0, color: 'var(--purple)' },
  { label: '维保', value: vehicle.value?.maintenance ?? 0, color: 'var(--amber)' },
  { label: '停用', value: vehicle.value?.disabled ?? 0, color: 'var(--red)' }
])
</script>

<style scoped>
.vehicle-total {
  color: var(--cyan2);
  font-size: 13px;
}

.vehicle-rows {
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 2px 0;
}

.vehicle-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 5px 0;
  border-bottom: 1px solid rgba(64, 156, 255, 0.1);
}

.v-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex: none;
}

.v-label {
  flex: 1;
  font-size: 14px;
  color: var(--txt);
}

.v-count {
  font-size: 20px;
  font-weight: 700;
  color: var(--cyan2);
}

.v-external {
  padding: 8px 10px;
  margin-top: 4px;
  border: 1px solid rgba(179, 136, 255, 0.35);
  background: rgba(179, 136, 255, 0.08);
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.v-ext-label {
  font-size: 12px;
  color: var(--purple);
}

.v-ext-value {
  font-size: 12px;
  color: var(--sub);
  white-space: nowrap;
}

.v-ext-value b {
  color: var(--txt);
  font-size: 14px;
}
</style>
