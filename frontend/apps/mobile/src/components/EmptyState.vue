<script setup lang="ts">
defineProps<{ text?: string; hint?: string }>()
</script>

<template>
  <view class="empty">
    <uni-icons type="folder-add" :size="46" color="#a8c2e4" />
    <text class="text">{{ text || '暂无数据' }}</text>
    <text v-if="hint" class="hint">{{ hint }}</text>
  </view>
</template>

<style scoped>
.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 56px 16px;
  gap: 10px;
}
.text {
  color: var(--txt);
  font-size: 16px;
  font-weight: 600;
}
.hint {
  color: var(--sub);
  font-size: 13px;
}
</style>
