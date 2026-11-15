import { fileURLToPath, URL } from 'node:url'
import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')

  return {
    plugins: [vue()],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url))
      }
    },
    server: {
      host: '127.0.0.1',
      port: 5173,
      open: false,
      proxy: env.VITE_PROXY_TARGET
        ? {
            '/api': {
              target: env.VITE_PROXY_TARGET,
              // Nginx preserves this Host; Spring must see the browser's same origin.
              // Keep Origin unchanged so cross-origin requests are still rejected.
              changeOrigin: false
            }
          }
        : undefined
    },
    build: {
      outDir: 'dist',
      chunkSizeWarningLimit: 1200
    }
  }
})
