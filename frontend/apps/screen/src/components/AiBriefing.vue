<template>
  <ScreenPanel title="AI 今日工作简报" class="ai-panel">
    <template #extra>
      <span class="ai-badge">AI</span>
    </template>
    <div class="ai-body">
      <div class="ai-placeholder">AI 简报将于每日 20:00 自动生成（二期开放）</div>
      <div class="ai-updated">上次更新：--</div>
    </div>
  </ScreenPanel>
</template>

<script setup lang="ts">
// 二期降级态：不调接口，仅展示占位说明（06 §8.3 AI 接口不可用降级）
import ScreenPanel from './ScreenPanel.vue'
</script>

<style scoped>
.ai-badge {
  color: var(--purple);
  border: 1px solid rgba(179, 136, 255, 0.45);
  border-radius: 3px;
  padding: 0 6px;
  font-size: 11px;
  line-height: 18px;
}

.ai-body {
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 8px;
  padding: 0 4px;
}

.ai-placeholder {
  font-size: 13px;
  color: var(--sub);
}

.ai-updated {
  font-size: 12px;
  color: var(--sub);
  opacity: 0.7;
}
</style>
