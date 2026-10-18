<template>
  <ScreenPanel title="本月人效排名 TOP5" class="rank-panel">
    <template #extra>
      <span class="rank-period">自然月 · 工单数</span>
    </template>
    <div v-if="top5.length" class="rank-list">
      <div v-for="(item, i) in top5" :key="item.staffId" class="rank-row">
        <span class="rank-no num" :class="`no-${i + 1}`">{{ i + 1 }}</span>
        <span class="rank-name" :title="item.name">{{ item.name }}</span>
        <div class="rank-bar">
          <div
            class="rank-bar-inner"
            :style="{ width: barWidth(item.orderCount), background: barColor(i) }"
          />
        </div>
        <span class="rank-count num">{{ item.orderCount }}</span>
      </div>
    </div>
    <div v-else class="rank-empty">暂无人效数据</div>
  </ScreenPanel>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { statStore } from '../store/stat'
import ScreenPanel from './ScreenPanel.vue'

const top5 = computed(() => statStore.staffPerformance.slice(0, 5))
const maxCount = computed(() => Math.max(...top5.value.map((i) => i.orderCount), 1))

function barWidth(count: number): string {
  return `${Math.max(Math.round((count / maxCount.value) * 100), 4)}%`
}

function barColor(index: number): string {
  if (index === 0) return 'var(--amber)'
  if (index === 1) return 'var(--cyan)'
  if (index === 2) return 'var(--purple)'
  return 'rgba(56, 189, 248, 0.55)'
}
</script>

<style scoped>
.rank-period {
  color: var(--sub);
}

.rank-list {
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: space-around;
  gap: 6px;
}

.rank-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.rank-no {
  flex: none;
  width: 22px;
  height: 22px;
  border-radius: 4px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
  color: var(--sub);
  background: rgba(6, 12, 26, 0.5);
  border: 1px solid var(--line);
}

.no-1 {
  color: #062033;
  background: var(--amber);
  border-color: var(--amber);
}

.no-2 {
  color: #062033;
  background: var(--cyan);
  border-color: var(--cyan);
}

.no-3 {
  color: #062033;
  background: var(--purple);
  border-color: var(--purple);
}

.rank-name {
  flex: none;
  width: 110px;
  font-size: 13px;
  color: var(--txt);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.rank-bar {
  flex: 1;
  height: 10px;
  background: rgba(6, 12, 26, 0.5);
  border-radius: 5px;
  overflow: hidden;
}

.rank-bar-inner {
  height: 100%;
  border-radius: 5px;
  transition: width 0.5s ease;
}

.rank-count {
  flex: none;
  width: 30px;
  text-align: right;
  font-size: 15px;
  font-weight: 700;
  color: var(--cyan2);
}

.rank-empty {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--sub);
  font-size: 13px;
}
</style>
