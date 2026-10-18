<template>
  <div class="login-page">
    <div class="login-card">
      <div class="login-head">
        <span class="login-badge">{{ logoText }}</span>
        <div>
          <h1 class="login-title">{{ name }}</h1>
          <p class="login-sub">数据驾驶舱 · 管理员登录</p>
        </div>
      </div>
      <form @submit.prevent="onSubmit">
        <input
          v-model="username"
          class="login-input"
          type="text"
          placeholder="手机号"
          autocomplete="username"
        />
        <input
          v-model="password"
          class="login-input"
          type="password"
          placeholder="密码"
          autocomplete="current-password"
        />
        <button class="login-btn" type="submit" :disabled="loading">
          {{ loading ? '登录中' : '登 录' }}
        </button>
      </form>
      <p v-if="error" class="login-error">{{ error }}</p>
    </div>
    <p class="login-tip">大屏仅管理员可用</p>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { loadBranding, systemName, getBranding } from '@crane/shared/api/branding'
import { authStore, login, forceLogout } from '../stores/auth'
import { navigate } from '../router'

const username = ref('')
const password = ref('')
const loading = ref(false)
const error = ref('')

const name = ref(systemName())
const logoText = ref(getBranding()['system.logo_text'] || '吊装')

onMounted(async () => {
  await loadBranding()
  name.value = systemName()
  logoText.value = getBranding()['system.logo_text'] || '吊装'
  document.title = `${name.value} · 数据驾驶舱`
})

async function onSubmit() {
  if (!username.value || !password.value) {
    error.value = '请输入手机号与密码'
    return
  }
  loading.value = true
  error.value = ''
  try {
    await login(username.value, password.value)
    if (authStore.me && authStore.me.role !== 'ADMIN') {
      forceLogout()
      error.value = '大屏仅管理员可用'
      return
    }
    navigate('/')
  } catch (e) {
    error.value = e instanceof Error ? e.message : '登录失败'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 20px;
  background: linear-gradient(160deg, var(--bg1) 0%, var(--bg0) 60%);
}

.login-card {
  width: 380px;
  padding: 32px 32px 24px;
  background: var(--panel);
  border: 1px solid var(--line);
  border-radius: 6px;
}

.login-head {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 24px;
}

.login-badge {
  width: 44px;
  height: 44px;
  border-radius: 8px;
  background: var(--cyan);
  color: #062033;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  font-size: 16px;
  flex: none;
}

.login-title {
  font-size: 18px;
  margin: 0;
  color: var(--txt);
  font-weight: 600;
}

.login-sub {
  margin: 2px 0 0;
  font-size: 12px;
  color: var(--sub);
}

.login-input {
  width: 100%;
  height: 42px;
  margin-bottom: 14px;
  padding: 0 12px;
  background: rgba(6, 12, 26, 0.6);
  border: 1px solid var(--line);
  border-radius: 6px;
  color: var(--txt);
  font-size: 14px;
  outline: none;
}

.login-input:focus {
  border-color: var(--line2);
}

.login-btn {
  width: 100%;
  height: 42px;
  border: none;
  border-radius: 6px;
  background: var(--cyan);
  color: #062033;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
}

.login-btn:disabled {
  opacity: 0.6;
  cursor: default;
}

.login-error {
  color: var(--red);
  font-size: 13px;
  text-align: center;
  margin: 12px 0 0;
}

.login-tip {
  color: var(--sub);
  font-size: 12px;
}
</style>
