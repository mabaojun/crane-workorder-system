<script setup lang="ts">
// 底部弹层：拒单原因 / 异常上报 / 签退开关等表单容器
defineProps<{ title: string }>()
const emit = defineEmits<{ (e: 'close'): void }>()
</script>

<template>
  <view class="mask" @tap="emit('close')">
    <view class="sheet" @tap.stop>
      <view class="head">
        <text class="title">{{ title }}</text>
        <view class="close" @tap="emit('close')">关闭</view>
      </view>
      <scroll-view scroll-y class="body">
        <slot />
      </scroll-view>
    </view>
  </view>
</template>

<style scoped>
.mask {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.45);
  z-index: 999;
  display: flex;
  align-items: flex-end;
}
.sheet {
  width: 100%;
  background: var(--panel);
  border-radius: var(--radius-lg) var(--radius-lg) 0 0;
  max-height: 80vh;
  display: flex;
  flex-direction: column;
}
.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border-bottom: 1px solid var(--line);
}
.title {
  font-size: 17px;
  font-weight: 600;
}
.close {
  font-size: 13px;
  color: var(--sub);
  padding: 2px 4px;
}
.body {
  padding: 16px;
  max-height: 64vh;
  box-sizing: border-box;
}
</style>
