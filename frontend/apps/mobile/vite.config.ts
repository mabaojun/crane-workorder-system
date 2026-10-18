import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'

// @crane/shared 的 package.json exports 仅映射根入口且不可改动，
// 这里把深路径（api/client、api/branding）alias 到共享层源文件，根入口保持包解析。
const sharedSrc = fileURLToPath(new URL('../../packages/shared/src', import.meta.url))

export default defineConfig({
  resolve: {
    alias: [
      { find: /^@crane\/shared\/api\/client$/, replacement: `${sharedSrc}/api/client.ts` },
      { find: /^@crane\/shared\/api\/branding$/, replacement: `${sharedSrc}/api/branding.ts` },
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
