<script setup lang="ts">
// 客户端我的页（T-33）：昵称 / 手机号 / 登出
import { computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import SectionCard from '../../components/SectionCard.vue'
import RoleTabbar from '../../components/RoleTabbar.vue'
import { useAuthStore } from '../../stores/auth'
import { toastError } from '../../utils'

const auth = useAuthStore()
const name = computed(() => auth.me?.name ?? '-')
const phone = computed(() => auth.me?.phone ?? '-')

onShow(async () => {
  if (auth.token && !auth.me) {
    try {
      await auth.loadMe()
    } catch (e) {
      toastError(e)
    }
  }
})

async function onLogout() {
  uni.showModal({
    title: '退出登录',
    content: '确认退出当前账号？',
    success: async (r) => {
      if (!r.confirm) return
      await auth.doLogout()
      uni.reLaunch({ url: '/pages/login/index' })
    }
  })
}

function goAgreement() {
  uni.navigateTo({ url: '/pages/agreement' })
}
</script>

<template>
  <view class="page">
    <view class="user-head">
      <view class="avatar">{{ name.slice(0, 1) }}</view>
      <view class="meta">
        <text class="name">{{ name }}</text>
        <text class="phone">{{ phone }}</text>
      </view>
      <text class="role">客户</text>
    </view>

    <SectionCard title="账号信息">
      <view class="row"><text class="k">昵称</text><text>{{ name }}</text></view>
      <view class="row"><text class="k">手机号</text><text>{{ phone }}</text></view>
    </SectionCard>

    <button class="logout" @tap="onLogout">退出登录</button>

    <view class="footer">
      <text class="agreement" @tap="goAgreement">用户协议与隐私政策</text>
    </view>

    <RoleTabbar role="customer" current="pages/customer/profile" />
  </view>
</template>

<style scoped>
.page {
  min-height: 100vh;
  background: var(--bg);
  padding: 16px;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.user-head {
  display: flex;
  align-items: center;
  gap: 12px;
  background: var(--panel);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  padding: 16px;
}
.avatar {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: var(--brand);
  color: #fff;
  font-size: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.meta {
  flex: 1;
  display: flex;
  flex-direction: column;
}
.name {
  font-size: 17px;
  font-weight: 600;
}
.phone {
  font-size: 13px;
  color: var(--sub);
  font-variant-numeric: tabular-nums;
}
.role {
  font-size: 12px;
  color: var(--brand);
  background: var(--brand-weak);
  border-radius: 4px;
  padding: 2px 8px;
}
.row {
  display: flex;
  gap: 8px;
  font-size: 14px;
  margin-bottom: 8px;
}
.k {
  color: var(--sub);
  flex: none;
  width: 64px;
}
.logout {
  height: 48px;
  line-height: 48px;
  border-radius: var(--radius);
  background: #fff;
  color: var(--danger);
  font-size: 17px;
  border: 1px solid var(--danger);
  box-sizing: border-box;
}
.logout::after {
  border: none;
}
.footer {
  margin-top: auto;
  display: flex;
  justify-content: center;
  padding: 12px 0 calc(12px + env(safe-area-inset-bottom));
}
.agreement {
  font-size: 13px;
  color: var(--sub);
  text-decoration: underline;
}
</style>
