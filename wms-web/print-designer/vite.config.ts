import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { resolve } from 'path'

/** 与 wms-web 挂载路径一致，避免代理后 /src/main.tsx 加载到 Vue 应用 */
const BASE = '/print-designer/'

export default defineConfig({
  base: BASE,
  plugins: [react()],
  build: {
    outDir: resolve(__dirname, '../public/print-designer'),
    emptyOutDir: true,
  },
  server: {
    port: 5174,
    host: true,
    proxy: {
      '/api': {
        target: 'http://localhost:9980',
        changeOrigin: true,
      },
    },
  },
  preview: {
    port: 5174,
    host: true,
  },
})
