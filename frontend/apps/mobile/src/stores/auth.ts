// 认证状态：token / me / role / 登录 / 登出 / 未授权处理
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { uniTokenAdapter } from '../api/client'
import { fetchMe, logout, passwordLogin, smsLogin } from '../api/modules/auth'
import type { MeInfo } from '../api/types'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string>(uniTokenAdapter.get() ?? '')
  const me = ref<MeInfo | null>(null)
  const role = computed(() => me.value?.role ?? '')

  async function login(phone: string, smsCode: string) {
    const res = await smsLogin(phone, smsCode)
    token.value = res.accessToken
    uniTokenAdapter.set(res.accessToken)
    await loadMe()
  }

  /** 静态密码登录：用户名即手机号，成功后与验证码登录同流程分流 */
  async function loginByPassword(username: string, password: string) {
    const res = await passwordLogin(username, password)
    token.value = res.accessToken
    uniTokenAdapter.set(res.accessToken)
    await loadMe()
  }

  async function loadMe() {
    me.value = await fetchMe()
  }

  async function doLogout() {
    try {
      if (token.value) await logout()
    } catch {
      // 登出接口失败不阻断本地清理
    }
    clear()
  }

  function clear() {
    token.value = ''
    me.value = null
    uniTokenAdapter.clear()
  }

  /** 令牌失效（10001/10002/10004）：清理并回登录页 */
  function handleUnauthorized() {
    if (!token.value) return
    clear()
    uni.reLaunch({ url: '/pages/login/index' })
  }

  return { token, me, role, login, loginByPassword, loadMe, doLogout, clear, handleUnauthorized }
})
