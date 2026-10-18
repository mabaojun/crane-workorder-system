<script setup lang="ts">
// 顶部品牌条：Logo 文字标 + 系统名（branding 动态）
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
    <text class="name">{{ name }}</text>
  </view>
</template>

<style scoped>
.brand-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: var(--panel);
  border-bottom: 1px solid var(--line);
}
.logo {
  width: 30px;
  height: 30px;
  border-radius: 6px;
  background: var(--brand);
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
}
.name {
  font-size: 17px;
  font-weight: 600;
  color: var(--txt);
}
</style>
