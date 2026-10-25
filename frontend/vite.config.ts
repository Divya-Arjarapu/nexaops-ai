import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv } from 'vite'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const apiBaseUrl = (env.VITE_API_BASE_URL || '/api').replace(/\/$/, '')

  return {
    plugins: [react()],
    server: {
      proxy: apiBaseUrl.startsWith('/api')
        ? {
            '/api': {
              target: env.VITE_BACKEND_URL || 'http://127.0.0.1:8080',
              changeOrigin: true,
            },
          }
        : undefined,
    },
  }
})
