import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  // 生产部署在 /screen/ 子路径
  base: process.env.SCREEN_BASE || '/',
  plugins: [vue()],
  server: {
    host: true, // 局域网测试：放开 0.0.0.0
    allowedHosts: true,
    port: 7776,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:7777',
        changeOrigin: true
      }
    }
  }
})
