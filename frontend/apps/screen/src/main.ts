import { createApp } from 'vue'
import App from './App.vue'
import { configureClient } from '@crane/shared/api/client'
import { loadBranding, systemName } from '@crane/shared/api/branding'
import { forceLogout } from './stores/auth'
import './styles/screen.css'

// 大屏端独立标识与令牌键（crane_screen_token，与后台 crane_token 隔离）
configureClient({
  clientType: 'screen',
  tokenAdapter: {
    get: () => localStorage.getItem('crane_screen_token'),
    set: (t) => localStorage.setItem('crane_screen_token', t),
    clear: () => localStorage.removeItem('crane_screen_token')
  },
  onUnauthorized: () => forceLogout()
})

function applyTitle() {
  document.title = `${systemName()} · 数据驾驶舱`
}
applyTitle()
loadBranding().then(applyTitle)

createApp(App).mount('#app')
