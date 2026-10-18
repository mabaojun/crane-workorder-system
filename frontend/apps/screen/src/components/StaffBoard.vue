<template>
  <ScreenPanel title="人员看板" class="staff-panel">
    <template #extra>
      <span class="staff-total num">共 {{ staff ? staff.total : '--' }} 人</span>
    </template>
    <div class="staff-cards">
      <div v-for="card in cards" :key="card.label" class="staff-card">
        <div class="staff-num num" :style="{ color: card.color }">
          {{ staff ? card.value : '--' }}
        </div>
        <div class="staff-label">{{ card.label }}</div>
      </div>
    </div>
  </ScreenPanel>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { statStore } from '../store/stat'
import ScreenPanel from './ScreenPanel.vue'

const staff = computed(() => statStore.dashboard?.staff ?? null)

const cards = computed(() => [
  { label: '在岗', value: staff.value?.onDuty ?? 0, color: 'var(--cyan)' },
  { label: '作业中', value: staff.value?.busy ?? 0, color: 'var(--amber)' },
  { label: '空闲', value: staff.value?.idle ?? 0, color: 'var(--green)' },
  { label: '离岗', value: staff.value?.away ?? 0, color: 'var(--sub)' }
])
</script>

<style scoped>
.staff-total {
  color: var(--cyan2);
  font-size: 13px;
}

.staff-cards {
  height: 100%;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
}

.staff-card {
  background: rgba(6, 12, 26, 0.45);
  border: 1px solid var(--line);
  border-radius: 4px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 8px 4px;
}

.staff-num {
  font-size: 26px;
  font-weight: 700;
  line-height: 1;
}

.staff-label {
  font-size: 12px;
  color: var(--sub);
}
</style>
