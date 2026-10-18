<template>
  <div class="screen">
    <div class="screen-title">{{ name }} · 数据驾驶舱</div>
    <div class="screen-placeholder">大屏模块为二期任务（T-40），本骨架仅验证构建链路。</div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { loadBranding, systemName } from '@crane/shared/api/branding'

const name = ref(systemName())
loadBranding().then(() => {
  name.value = systemName()
  document.title = `${name.value} · 数据驾驶舱`
})
</script>

<style>
/* 大屏深色令牌（文档 11 §2.1 深色列 + 06 §6.1） */
:root {
  --brand-dark: #38bdf8;
  --bg-dark: #060c1a;
  --panel-dark: rgba(13, 28, 54, 0.72);
  --line-dark: rgba(64, 156, 255, 0.18);
  --txt-dark: #dbe7f5;
  --sub-dark: #7d93b5;
}
body { margin: 0; }
.screen {
  height: 100vh;
  background: var(--bg-dark);
  color: var(--txt-dark);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
}
.screen-title {
  font-size: 34px;
  font-weight: 700;
  letter-spacing: 6px;
  color: var(--brand-dark);
}
.screen-placeholder { color: var(--sub-dark); font-size: 14px; }
</style>
