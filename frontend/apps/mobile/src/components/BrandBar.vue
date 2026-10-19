<script setup lang="ts">
// 顶部品牌条：深色科技蓝底 + Logo 文字标 + 系统名（branding 动态）
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getBranding, loadBranding } from '@crane/shared/api/branding'

const logoText = ref('吊装')
const name = ref('吊装业务管理系统')

onShow(async () => {
  await loadBranding()
  logoText.value = getBranding()['system.logo_text'] ?? '吊装'
  name.value = getBranding()['system.name'] ?? '吊装业务管理系统'
})
</script>

<template>
  <view class="brand-bar">
    <view class="logo">{{ logoText }}</view>
    <view class="brand-text">
      <text class="name">{{ name }}</text>
      <text class="sub">移动业务台</text>
    </view>
  </view>
</template>

<style scoped>
.brand-bar {
  position: relative;
  overflow: hidden;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 15px 16px;
  background: linear-gradient(135deg, #0b1f3a 0%, #123a6b 62%, #0e7490 100%);
  box-shadow: 0 4px 16px rgba(11, 31, 58, 0.18);
}
.brand-bar::after {
  content: '';
  position: absolute;
  right: -36px;
  top: -54px;
  width: 150px;
  height: 150px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(23, 183, 212, 0.3), transparent 68%);
  pointer-events: none;
}
.logo {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: rgba(165, 243, 252, 0.16);
  border: 1px solid rgba(103, 232, 249, 0.55);
  color: #a5f3fc;
  font-size: 15px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex: none;
}
.brand-text {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
}
.name {
  font-size: 18px;
  font-weight: 700;
  color: #fff;
  letter-spacing: 0.3px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.sub {
  font-size: 11px;
  letter-spacing: 2px;
  color: rgba(255, 255, 255, 0.62);
}
</style>
