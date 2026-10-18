import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  // 生产部署在 /admin/ 子路径（见 docker/deploy/nginx.conf）；dev 不受影响
  base: process.env.ADMIN_BASE || '/',
  plugins: [vue()],
  resolve: {
    // @crane/shared 仅声明了根出口，这里补齐子路径解析（api/client 等），不改共享包
    alias: [
      {
        find: /^@crane\/shared$/,
        replacement: fileURLToPath(new URL('../../packages/shared/src/index.ts', import.meta.url))
      },
      {
        find: '@crane/shared/api',
        replacement: fileURLToPath(new URL('../../packages/shared/src/api', import.meta.url))
      }
    ]
  },
  server: {
    host: true, // 局域网测试：放开 0.0.0.0
    allowedHosts: true,
    port: 7774,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:7777',
        changeOrigin: true
      }
    }
  },
  build: {
    outDir: 'dist',
    chunkSizeWarningLimit: 1200
  }
})
