<script setup lang="ts">
// 区块卡片：标题 + 内容
defineProps<{ title?: string }>()
</script>

<template>
  <view class="section-card">
    <view v-if="title" class="title">{{ title }}</view>
    <slot />
  </view>
</template>

<style scoped>
.section-card {
  background: var(--panel);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  padding: 12px 16px;
}
.title {
  font-size: 15px;
  font-weight: 600;
  color: var(--txt);
  margin-bottom: 8px;
}
</style>
