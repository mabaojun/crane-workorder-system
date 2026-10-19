<script setup lang="ts">
// 底部导航：DRIVER / CUSTOMER / ADMIN 三角色共用，图标使用 uni-ui 标准组件 uni-icons
const props = defineProps<{
  role: 'driver' | 'customer' | 'admin'
  current: string
}>()

interface TabItem {
  key: string
  text: string
  url: string
  icon: string
  activeIcon?: string
}

const TABS: Record<string, TabItem[]> = {
  driver: [
    { key: 'index', text: '首页', url: '/pages/driver/index', icon: 'home', activeIcon: 'home-filled' },
    { key: 'orders', text: '工单列表', url: '/pages/driver/orders', icon: 'list' },
    { key: 'profile', text: '我的', url: '/pages/driver/profile', icon: 'person', activeIcon: 'person-filled' }
  ],
  customer: [
    { key: 'index', text: '工单', url: '/pages/customer/index', icon: 'list' },
    { key: 'demand-create', text: '需求提报', url: '/pages/customer/demand-create', icon: 'plusempty' },
    { key: 'demands', text: '我的需求', url: '/pages/customer/demands', icon: 'compose' },
    { key: 'profile', text: '我的', url: '/pages/customer/profile', icon: 'person', activeIcon: 'person-filled' }
  ],
  admin: [
    { key: 'index', text: '工作台', url: '/pages/admin/index', icon: 'home', activeIcon: 'home-filled' },
    { key: 'demands', text: '需求审批', url: '/pages/admin/demands', icon: 'compose' },
    { key: 'exceptions', text: '异常审批', url: '/pages/admin/exceptions', icon: 'notification', activeIcon: 'notification-filled' },
    { key: 'orders', text: '工单', url: '/pages/admin/orders', icon: 'list' },
    { key: 'profile', text: '我的', url: '/pages/admin/profile', icon: 'person', activeIcon: 'person-filled' }
  ]
}

function isActive(url: string): boolean {
  return '/' + props.current === url
}

function iconType(t: TabItem): string {
  return isActive(t.url) && t.activeIcon ? t.activeIcon : t.icon
}

function go(url: string) {
  if (isActive(url)) return
  uni.redirectTo({ url })
}
</script>

<template>
  <view class="tabbar">
    <view
      v-for="t in TABS[role]"
      :key="t.key"
      class="item"
      :class="{ active: isActive(t.url) }"
      @tap="go(t.url)"
    >
      <view class="icon">
        <uni-icons :type="iconType(t)" :size="24" :color="isActive(t.url) ? '#1a6bff' : '#5f708a'" />
      </view>
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
  background: rgba(255, 255, 255, 0.98);
  border-top: 1px solid var(--line);
  padding-bottom: env(safe-area-inset-bottom);
  box-shadow: 0 -2px 12px rgba(15, 31, 61, 0.06);
}
.item {
  position: relative;
  flex: 1;
  min-height: 60px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 3px;
  padding: 7px 0 6px;
  font-size: 14px;
  color: var(--sub);
}
.item.active {
  color: var(--brand);
  font-weight: 600;
}
.item.active::before {
  content: '';
  position: absolute;
  top: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 26px;
  height: 3px;
  border-radius: 0 0 3px 3px;
  background: var(--brand);
}
.icon {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 24px;
}
.icon :deep(.uni-icons) {
  line-height: 1;
}
</style>
