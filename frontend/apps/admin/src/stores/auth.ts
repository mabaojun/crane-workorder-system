import { defineStore } from 'pinia'
import { ref } from 'vue'
import { request, configureClient } from '@crane/shared/api/client'

export interface MeInfo {
  userId: number
  role: string
  name: string
  phone: string
  staffId?: number
  customerId?: number
  dataScope?: string
  permissions?: string[]
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('crane_token') || '')
  const me = ref<MeInfo | null>(null)

  configureClient({
    tokenAdapter: {
      get: () => localStorage.getItem('crane_token'),
      set: (t) => localStorage.setItem('crane_token', t),
      clear: () => localStorage.removeItem('crane_token')
    }
  })

  /** API-AUTH-004 账号密码登录 */
  async function login(username: string, password: string) {
    const data = await request<{ tokenType: string; accessToken: string; role: string }>(
      '/auth/password-login',
      { method: 'POST', body: { username, password } }
    )
    token.value = data.accessToken
    localStorage.setItem('crane_token', data.accessToken)
    await fetchMe()
  }

  /** API-AUTH-006 当前用户信息 */
  async function fetchMe() {
    me.value = await request<MeInfo>('/auth/me')
  }

  /** API-AUTH-005 登出并失效令牌 */
  async function logout() {
    try {
      await request('/auth/logout', { method: 'POST' })
    } finally {
      token.value = ''
      me.value = null
      localStorage.removeItem('crane_token')
    }
  }

  return { token, me, login, fetchMe, logout }
})
