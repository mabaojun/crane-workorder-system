<template>
  <div class="cockpit">
    <el-card shadow="never" class="panel">
      <template #header>系统就绪</template>
      <p>后端服务与登录链路已就绪。业务菜单（需求单 / 工单 / 档案 / 权限）将随后端域接口逐步开放。</p>
      <p class="sub">当前角色：{{ auth.me?.role || '未知' }}；数据范围：{{ auth.me?.dataScope || '-' }}</p>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { useAuthStore } from '../../stores/auth'

const auth = useAuthStore()
</script>

<style scoped>
.panel { max-width: 720px; }
.sub { color: var(--sub); font-size: 13px; }
</style>
