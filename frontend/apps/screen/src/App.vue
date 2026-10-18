<template>
  <component :is="view" />
</template>

<script setup lang="ts">
import { computed, watch } from 'vue'
import { currentRoute, navigate } from './router'
import { authStore } from './stores/auth'
import LoginView from './views/LoginView.vue'
import DashboardView from './views/DashboardView.vue'

const view = computed(() => (currentRoute.value === '/login' ? LoginView : DashboardView))

// 路由守卫：未登录访问驾驶舱重定向 /login；已登录访问 /login 回驾驶舱
watch(
  () => [currentRoute.value, authStore.token] as const,
  ([path, token]) => {
    if (path !== '/login' && !token) navigate('/login', true)
    else if (path === '/login' && token) navigate('/', true)
  },
  { immediate: true }
)
</script>
