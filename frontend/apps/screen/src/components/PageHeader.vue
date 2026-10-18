<template>
  <header class="page-header">
    <div class="header-left">
      <span class="header-badge">{{ logoText }}</span>
      <div class="header-titles">
        <span class="sys-name">{{ name }}</span>
        <span class="title-sep">·</span>
        <span class="screen-name">数据驾驶舱</span>
      </div>
    </div>
    <div class="header-right">
      <span class="meta">管理员视角</span>
      <span class="sep">·</span>
      <span class="meta num">{{ fmtDate(now) }}</span>
      <span class="sep">·</span>
      <span class="clock num">{{ fmtTime(now) }}</span>
      <span class="sep">·</span>
      <span class="meta updated num" :class="{ stale }">
        数据更新于 {{ updatedText }}{{ stale ? '（陈旧）' : '' }}
      </span>
    </div>
  </header>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { getBranding, loadBranding, systemName } from '@crane/shared/api/branding'
import { statStore } from '../store/stat'
import { fmtDate, fmtTime } from '../utils/format'

const STALE_MS = 60000

const name = ref(systemName())
const logoText = ref(getBranding()['system.logo_text'] || '吊装')
const now = ref(new Date())
const mountedAt = Date.now()

let clockTimer: number | null = null

onMounted(async () => {
  clockTimer = window.setInterval(() => {
    now.value = new Date()
  }, 1000)
  await loadBranding()
  name.value = systemName()
  logoText.value = getBranding()['system.logo_text'] || '吊装'
})

onUnmounted(() => {
  if (clockTimer !== null) clearInterval(clockTimer)
})

// 连续 60s 未取到新数据转告警色（06 §8.2，C-06-08 暂定 60s）
const stale = computed(() => {
  const base = statStore.lastUpdateAt || mountedAt
  return now.value.getTime() - base > STALE_MS
})

const updatedText = computed(() =>
  statStore.lastUpdateAt ? fmtTime(new Date(statStore.lastUpdateAt)) : '--:--:--'
)
</script>

<style scoped>
.page-header {
  height: 92px;
  flex: none;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 8px;
  border-bottom: 1px solid var(--line);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 14px;
}

.header-badge {
  width: 46px;
  height: 46px;
  border-radius: 8px;
  background: var(--cyan);
  color: #062033;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 18px;
}

.header-titles {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.sys-name {
  font-size: 28px;
  font-weight: 700;
  color: var(--txt);
  letter-spacing: 2px;
}

.title-sep {
  color: var(--sub);
}

.screen-name {
  font-size: 24px;
  font-weight: 600;
  color: var(--cyan);
  letter-spacing: 6px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 16px;
  color: var(--sub);
}

.sep {
  color: var(--line2);
}

.clock {
  color: var(--cyan2);
  font-size: 18px;
}

.updated {
  color: var(--cyan2);
}

.updated.stale {
  color: var(--red);
}
</style>
