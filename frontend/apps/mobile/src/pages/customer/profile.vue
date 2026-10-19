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
  padding: 16px 16px calc(96px + env(safe-area-inset-bottom));
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.user-head {
  position: relative;
  overflow: hidden;
  display: flex;
  align-items: center;
  gap: 14px;
  background: linear-gradient(135deg, #0b1f3a 0%, #123a6b 62%, #0e7490 100%);
  border: 1px solid rgba(103, 232, 249, 0.18);
  border-radius: var(--radius-lg);
  box-shadow: 0 8px 22px rgba(11, 31, 58, 0.2);
  padding: 18px;
}
.avatar {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: rgba(165, 243, 252, 0.16);
  border: 1px solid rgba(103, 232, 249, 0.55);
  color: #a5f3fc;
  font-size: 22px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex: none;
}
.meta {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.name {
  font-size: 20px;
  font-weight: 700;
  color: #fff;
}
.phone {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.7);
  font-variant-numeric: tabular-nums;
}
.role {
  font-size: 12px;
  font-weight: 600;
  color: #0b1f3a;
  background: #a5f3fc;
  border-radius: 999px;
  padding: 3px 10px;
}
.row {
  display: flex;
  gap: 8px;
  font-size: 15px;
  margin-bottom: 9px;
}
.k {
  color: var(--sub);
  font-size: 14px;
  flex: none;
  width: 72px;
}
.logout {
  width: 100%;
  display: block;
  height: 50px;
  line-height: 50px;
  border-radius: var(--radius);
  background: #fff;
  color: var(--danger);
  font-size: 17px;
  font-weight: 600;
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
  font-size: 14px;
  color: var(--sub);
  text-decoration: underline;
}
</style>
