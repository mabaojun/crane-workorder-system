<template>
  <ScreenPanel title="今日工单总览" class="kpi-panel">
    <div class="kpi-main">
      <span class="kpi-big num">{{ order ? order.today : '--' }}</span>
      <span class="kpi-label">今日工单</span>
    </div>
    <div class="kpi-sub">
      <span class="kpi-sub-item">本周 <b class="num">{{ order ? order.week : '--' }}</b></span>
      <span class="kpi-sub-item">本月 <b class="num">{{ order ? order.month : '--' }}</b></span>
    </div>
    <div class="kpi-chips">
      <span class="chip">
        <i class="dot" style="background: var(--cyan)" />进行中
        <b class="num">{{ order ? order.inProgress : '--' }}</b>
      </span>
      <span class="chip">
        <i class="dot" style="background: var(--red)" />受阻
        <b class="num">{{ order ? order.suspended : '--' }}</b>
      </span>
      <span class="chip">
        <i class="dot" style="background: var(--green)" />已完成
        <b class="num">{{ order ? order.completed : '--' }}</b>
      </span>
    </div>
  </ScreenPanel>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { statStore } from '../store/stat'
import ScreenPanel from './ScreenPanel.vue'

const order = computed(() => statStore.dashboard?.order ?? null)
</script>

<style scoped>
.kpi-main {
  display: flex;
  align-items: baseline;
  gap: 14px;
  padding: 4px 2px 2px;
}

.kpi-big {
  font-size: 56px;
  font-weight: 700;
  color: var(--cyan2);
  line-height: 1;
}

.kpi-label {
  font-size: 15px;
  color: var(--sub);
}

.kpi-sub {
  display: flex;
  gap: 24px;
  padding: 10px 2px;
  font-size: 14px;
  color: var(--sub);
}

.kpi-sub b {
  color: var(--txt);
  font-weight: 600;
  margin-left: 4px;
}

.kpi-chips {
  display: flex;
  gap: 12px;
  border-top: 1px solid var(--line);
  padding-top: 10px;
}

.chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--sub);
}

.chip b {
  color: var(--txt);
  font-weight: 600;
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex: none;
}
</style>
