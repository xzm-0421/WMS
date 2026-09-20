import { defineConfig } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'

export default defineConfig({
  plugins: [uni()],
  server: {
    port: 5176,
    proxy: {
      '/api': {
        target: 'http://localhost:9980',
        changeOrigin: true,
      },
    },
  },
})
