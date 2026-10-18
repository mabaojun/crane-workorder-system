<script setup lang="ts">
// 自绘底部导航：单套代码承载 DRIVER/CUSTOMER 两角色，tab 页合计超原生 tabBar 5 项上限，
// 故以组件形式实现（视觉与原生 tabBar 一致），角色内页面用 redirectTo 切换。
const props = defineProps<{
  role: 'driver' | 'customer'
  current: string
}>()

const TABS: Record<string, { key: string; text: string; url: string }[]> = {
  driver: [
    { key: 'index', text: '首页', url: '/pages/driver/index' },
    { key: 'orders', text: '工单列表', url: '/pages/driver/orders' },
    { key: 'profile', text: '我的', url: '/pages/driver/profile' }
  ],
  customer: [
    { key: 'index', text: '工单', url: '/pages/customer/index' },
    { key: 'demand-create', text: '需求提报', url: '/pages/customer/demand-create' },
    { key: 'demands', text: '我的需求', url: '/pages/customer/demands' },
    { key: 'profile', text: '我的', url: '/pages/customer/profile' }
  ]
}

function go(url: string) {
  if (url === '/' + props.current) return
  uni.redirectTo({ url })
}
</script>

<template>
  <view class="tabbar">
    <view
      v-for="t in TABS[role]"
      :key="t.key"
      class="item"
      :class="{ active: '/' + current === t.url }"
      @tap="go(t.url)"
    >
      <view class="icon" />
      <text>{{ t.text }}</text>
    </view>
  </view>
</template>

<style scoped>
.tabbar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 99;
  display: flex;
  background: var(--panel);
  border-top: 1px solid var(--line);
  padding-bottom: env(safe-area-inset-bottom);
}
.item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  padding: 8px 0 6px;
  font-size: 12px;
  color: var(--sub);
}
.item.active {
  color: var(--brand);
}
.icon {
  width: 18px;
  height: 18px;
  border-radius: 4px;
  border: 2px solid currentColor;
  background: transparent;
}
.item.active .icon {
  background: currentColor;
}
</style>
