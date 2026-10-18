import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
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
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true
      }
    }
  },
  build: {
    outDir: 'dist',
    chunkSizeWarningLimit: 1200
  }
})
