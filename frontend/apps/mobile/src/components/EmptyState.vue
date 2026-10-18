<script setup lang="ts">
defineProps<{ text?: string; hint?: string }>()
</script>

<template>
  <view class="empty">
    <view class="icon" aria-hidden="true">
      <view class="box" />
    </view>
    <text class="text">{{ text || '暂无数据' }}</text>
    <text v-if="hint" class="hint">{{ hint }}</text>
  </view>
</template>

<style scoped>
.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 48px 16px;
  gap: 8px;
}
.box {
  width: 40px;
  height: 28px;
  border: 2px solid var(--line);
  border-radius: 4px;
  background: var(--panel);
}
.text {
  color: var(--sub);
  font-size: 14px;
}
.hint {
  color: var(--sub);
  font-size: 12px;
}
</style>
