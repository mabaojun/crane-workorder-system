<template>
  <div class="login-page">
    <el-card class="login-card">
      <div class="login-head">
        <span class="login-badge">{{ logoText }}</span>
        <div>
          <h1 class="login-title">{{ sysName }}</h1>
          <p class="login-sub">后台管理系统</p>
        </div>
      </div>
      <el-form @submit.prevent="onSubmit">
        <el-form-item>
          <el-input v-model="username" placeholder="手机号" size="large" autocomplete="username" />
        </el-form-item>
        <el-form-item>
          <el-input
            v-model="password"
            type="password"
            placeholder="密码"
            size="large"
            show-password
            autocomplete="current-password"
          />
        </el-form-item>
        <el-button type="primary" size="large" class="login-btn" :loading="loading" native-type="submit">
          登 录
        </el-button>
      </el-form>
      <p v-if="error" class="login-error">{{ error }}</p>
    </el-card>
    <p class="login-copyright">{{ copyright }}</p>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../../stores/auth'
import { getBranding, loadBranding, systemName } from '@crane/shared/api/branding'

const router = useRouter()
const auth = useAuthStore()

const username = ref('')
const password = ref('')
const loading = ref(false)
const error = ref('')

const sysName = computed(() => systemName())
const logoText = computed(() => getBranding()['system.logo_text'] || '吊装')
const copyright = computed(() => getBranding()['system.copyright'] || getBranding()['system.company_name'] || '')

onMounted(() => loadBranding())

async function onSubmit() {
  if (!username.value || !password.value) {
    error.value = '请输入手机号与密码'
    return
  }
  loading.value = true
  error.value = ''
  try {
    await auth.login(username.value, password.value)
    router.push('/')
  } catch (e) {
    error.value = e instanceof Error ? e.message : '登录失败'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 20px;
  background: linear-gradient(160deg, #eef4fd 0%, var(--bg) 55%);
}
.login-card { width: 380px; padding: 8px 4px; }
.login-head { display: flex; align-items: center; gap: 12px; margin-bottom: 22px; }
.login-badge {
  width: 44px; height: 44px;
  border-radius: 10px;
  background: var(--brand);
  color: #fff;
  display: inline-flex; align-items: center; justify-content: center;
  font-weight: 600; font-size: 16px;
  flex: none;
}
.login-title { font-size: 18px; margin: 0; color: var(--txt); }
.login-sub { margin: 2px 0 0; font-size: 12px; color: var(--sub); }
.login-btn { width: 100%; }
.login-error { color: var(--danger); font-size: 13px; text-align: center; margin: 12px 0 0; }
.login-copyright { color: var(--sub); font-size: 12px; }
</style>
