import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'

// @crane/shared 深路径由 package.json exports 统一解析，
// 不再单独 alias api/client 与 api/branding：否则小程序编译会把「相对路径 ./client」
// 与「别名 client.ts」识别为两个模块实例，导致 configureClient 配置不到实际请求实例。
const sharedSrc = fileURLToPath(new URL('../../packages/shared/src', import.meta.url))

export default defineConfig({
  // H5 生产部署在 /h5/ 子路径；小程序构建不使用该值
  base: process.env.H5_BASE || '/',
  resolve: {
    alias: [
      { find: /^@crane\/shared$/, replacement: `${sharedSrc}/index.ts` }
    ]
  },
  server: {
    port: 5175,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true
      }
    }
  },
  plugins: [uni()]
})
