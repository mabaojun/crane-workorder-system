<template>
  <el-container class="layout">
    <el-aside width="220px" class="aside">
      <div class="brand">
        <span class="brand-badge">{{ logoText }}</span>
        <span class="brand-name">{{ sysName }}</span>
      </div>
      <el-menu
        :default-active="String(route.meta?.menu || '')"
        router
        class="menu"
      >
        <template v-for="item in menus" :key="item.key">
          <el-sub-menu v-if="item.children?.length" :index="item.key">
            <template #title>{{ item.title }}</template>
            <el-menu-item
              v-for="child in item.children"
              :key="child.key"
              :index="child.key"
              :route="child.path"
            >{{ child.title }}</el-menu-item>
          </el-sub-menu>
          <el-menu-item v-else :index="item.key" :route="item.path">{{ item.title }}</el-menu-item>
        </template>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <div class="header-title">{{ route.meta?.title || '' }}</div>
        <el-dropdown v-if="auth.me">
          <span class="user-chip">{{ auth.me.name || auth.me.phone }}</span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item @click="onLogout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { useVisibleMenus } from '../menus'
import { getBranding, loadBranding, systemName } from '@crane/shared/api/branding'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const menus = useVisibleMenus()

// 系统名称动态化：登录前后均从 /api/v1/system/public-config 读取，后台可改
const sysName = computed(() => systemName())
const logoText = computed(() => getBranding()['system.logo_text'] || '吊装')

onMounted(async () => {
  await loadBranding()
  if (auth.token && !auth.me) {
    try {
      await auth.fetchMe()
    } catch {
      /* 401 由共享层统一跳登录 */
    }
  }
})

async function onLogout() {
  await auth.logout()
  router.push('/login')
}
</script>

<style scoped>
.layout { height: 100vh; }
.aside {
  background: var(--panel);
  border-right: 1px solid var(--line);
  display: flex;
  flex-direction: column;
}
.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 18px 16px;
  border-bottom: 1px solid var(--line);
}
.brand-badge {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: var(--brand);
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  font-size: 13px;
  flex: none;
}
.brand-name {
  font-weight: 600;
  font-size: 14px;
  color: var(--txt);
}
.menu { border-right: none; flex: 1; }
.header {
  background: var(--panel);
  border-bottom: 1px solid var(--line);
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.header-title { font-weight: 600; }
.user-chip { cursor: pointer; color: var(--txt); }
.main { background: var(--bg); padding: var(--gap); }
</style>
