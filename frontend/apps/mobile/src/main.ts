import { createSSRApp } from 'vue'
import * as Pinia from 'pinia'
import App from './App.vue'
import { configureClient } from '@crane/shared/api/client'
import { CLIENT_TYPE, uniTokenAdapter } from './api/client'
import { useAuthStore } from './stores/auth'

// uni-app vue3 入口：注入 pinia，配置共享层请求客户端（令牌适配 / 客户端类型 / 401 回调）
export function createApp() {
  const app = createSSRApp(App)
  app.use(Pinia.createPinia())

  configureClient({
    tokenAdapter: uniTokenAdapter,
    clientType: CLIENT_TYPE,
    baseURL: '/api/v1',
    onUnauthorized: () => {
      const auth = useAuthStore()
      auth.handleUnauthorized()
    }
  })

  return { app, Pinia }
}
