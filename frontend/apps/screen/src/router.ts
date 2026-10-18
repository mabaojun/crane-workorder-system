// 轻量路径路由：/login 与 /（驾驶舱），未登录重定向由 App.vue 守卫处理；不引入 vue-router
import { ref } from 'vue'

const BASE = import.meta.env.BASE_URL || '/'

function currentPath(): string {
  const p = window.location.pathname
  const path = BASE !== '/' && p.startsWith(BASE) ? p.slice(BASE.length - 1) : p
  return path === '' ? '/' : path
}

export const currentRoute = ref(currentPath())

export function navigate(path: string, replace = false) {
  const url = BASE === '/' ? path : BASE.replace(/\/$/, '') + path
  if (replace) window.history.replaceState(null, '', url)
  else window.history.pushState(null, '', url)
  currentRoute.value = path
}

window.addEventListener('popstate', () => {
  currentRoute.value = currentPath()
})
