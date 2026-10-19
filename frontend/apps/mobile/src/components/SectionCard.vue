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
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow);
  padding: 16px 18px;
}
.title {
  font-size: 17px;
  font-weight: 700;
  color: var(--txt);
  margin-bottom: 10px;
  letter-spacing: 0.2px;
}
</style>
