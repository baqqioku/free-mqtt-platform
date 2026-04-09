import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    open: true,
    proxy: {
      // Admin API (MQTT Server - HTTP)
      '/api': {
        target: 'http://localhost:23240',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, '')
      },
      // Route Service
      '/route': {
        target: 'http://localhost:8084',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/route/, '')
      }
    }
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
    chunkSizeWarningLimit: 1000
  }
})
