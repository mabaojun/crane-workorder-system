// 大屏会话：令牌存独立键 crane_screen_token（与后台同浏览器互不覆盖），登出即清除
import { reactive } from 'vue'
import { request } from '@crane/shared/api/client'
import { currentRoute, navigate } from '../router'

const TOKEN_KEY = 'crane_screen_token'

export interface ScreenMe {
  userId: number
  role: string
  name: string
  phone: string
}

export const authStore = reactive({
  token: localStorage.getItem(TOKEN_KEY) || '',
  me: null as ScreenMe | null
})

/** 账号密码登录（仅 ADMIN 放行，非 ADMIN 由登录页提示并登出） */
export async function login(username: string, password: string) {
  const data = await request<{ tokenType: string; accessToken: string; role: string }>(
    '/auth/password-login',
    { method: 'POST', body: { username, password } }
  )
  authStore.token = data.accessToken
  localStorage.setItem(TOKEN_KEY, data.accessToken)
  await fetchMe()
}

export async function fetchMe() {
  authStore.me = await request<ScreenMe>('/auth/me')
}

export function clearSession() {
  authStore.token = ''
  authStore.me = null
  localStorage.removeItem(TOKEN_KEY)
}

/** 令牌失效 / 非 ADMIN：清会话回登录页 */
export function forceLogout() {
  clearSession()
  if (currentRoute.value !== '/login') navigate('/login', true)
}

export async function logout() {
  try {
    await request('/auth/logout', { method: 'POST' })
  } catch {
    // 登出接口失败不阻塞本地清理
  }
  forceLogout()
}
