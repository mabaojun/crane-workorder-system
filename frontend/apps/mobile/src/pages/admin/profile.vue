<script setup lang="ts">
// 管理员我的：身份信息、快捷入口与退出登录
import { computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import RoleTabbar from '../../components/RoleTabbar.vue'
import { useAuthStore } from '../../stores/auth'
import { toastError } from '../../utils'

const auth = useAuthStore()
const name = computed(() => auth.me?.name || '管理员')
const phone = computed(() => auth.me?.phone || '-')
const avatar = computed(() => name.value.slice(0, 1))

const links = [
  { title: '一句话下工单', desc: '语音输入，自动拆解并填表', path: '/pages/admin/voice-create' },
  { title: '需求审批', desc: '复核客户需求并转工单', path: '/pages/admin/demands' },
  { title: '异常审批', desc: '批复现场异常与受阻工单', path: '/pages/admin/exceptions' },
  { title: '工单管理', desc: '下发、改派、取消与结单', path: '/pages/admin/orders' },
  { title: '新建工单', desc: '管理员直接建单下发', path: '/pages/admin/order-create' }
]

function go(path: string) {
  uni.navigateTo({ url: path })
}

function onLogout() {
  uni.showModal({
    title: '退出登录',
    content: '确认退出当前管理员账号？',
    success: async (r) => {
      if (!r.confirm) return
      await auth.doLogout()
      uni.reLaunch({ url: '/pages/login/index' })
    }
  })
}

onShow(async () => {
  if (auth.token && !auth.me) {
    try {
      await auth.loadMe()
    } catch (e) {
      toastError(e)
    }
  }
})
</script>

<template>
  <view class="adm-page">
    <view class="adm-hero">
      <text class="adm-hero-kicker">ADMIN PROFILE</text>
      <view class="profile-row">
        <view class="avatar">{{ avatar }}</view>
        <view class="profile-info">
          <text class="profile-name">{{ name }}</text>
          <text class="profile-phone">{{ phone }}</text>
        </view>
        <view class="role-badge">管理员</view>
      </view>
      <text class="adm-hero-sub">移动审批 · 工单调度 · 异常处理</text>
    </view>

    <view class="adm-list">
      <view v-for="l in links" :key="l.title" class="adm-item" @tap="go(l.path)">
        <view class="adm-item-head">
          <text class="adm-item-no">{{ l.title }}</text>
          <text class="adm-section-link">进入</text>
        </view>
        <text class="link-desc">{{ l.desc }}</text>
      </view>
    </view>

    <button class="adm-btn plain-danger" @tap="onLogout">退出登录</button>

    <RoleTabbar role="admin" current="pages/admin/profile" />
  </view>
</template>

<style scoped>
.profile-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 8px 0 6px;
}
.avatar {
  width: 52px;
  height: 52px;
  border-radius: 16px;
  background: rgba(165, 243, 252, 0.18);
  border: 1px solid rgba(103, 232, 249, 0.5);
  color: #a5f3fc;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  font-weight: 700;
  flex: none;
}
.profile-info {
  flex: 1;
  min-width: 0;
}
.profile-name {
  display: block;
  font-size: 20px;
  font-weight: 700;
  color: #fff;
}
.profile-phone {
  display: block;
  margin-top: 2px;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.7);
}
.role-badge {
  flex: none;
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 11px;
  color: #0b1f3a;
  background: #a5f3fc;
  font-weight: 600;
}
.link-desc {
  display: block;
  font-size: 12px;
  color: #64748b;
}
</style>
