import { createSSRApp } from 'vue'
import * as Pinia from 'pinia'
import App from './App.vue'
import { configureClient } from '@crane/shared/api/client'
import { API_BASE_URL, CLIENT_TYPE, uniRequestAdapter, uniTokenAdapter } from './api/client'
import { useAuthStore } from './stores/auth'

// uni-app vue3 入口：注入 pinia，配置共享层请求客户端（令牌适配 / 客户端类型 / baseURL / 传输适配 / 401 回调）
export function createApp() {
  const app = createSSRApp(App)
  app.use(Pinia.createPinia())

  configureClient({
    tokenAdapter: uniTokenAdapter,
    clientType: CLIENT_TYPE,
    baseURL: API_BASE_URL,
    // 小程序运行环境无 fetch，注入 uni.request 适配；H5 保持默认 fetch
    requestAdapter: CLIENT_TYPE === 'mp' ? uniRequestAdapter : undefined,
    onUnauthorized: () => {
      const auth = useAuthStore()
      auth.handleUnauthorized()
    }
  })

  return { app, Pinia }
}
