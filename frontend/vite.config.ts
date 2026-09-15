import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5789,
    host: '0.0.0.0',
    strictPort: true,
    proxy: {
      // Proxy /api/* to the Spring Boot server.
      // Override via REVIEW_BACKEND_URL env var, e.g. for staging.
      '/api': {
        // 显式 IPv4 + 8090 端口：localhost 在 Windows 默认解析到 IPv6 ::1，会 ECONNREFUSED
        target: process.env.REVIEW_BACKEND_URL || 'http://127.0.0.1:8090',
        changeOrigin: true,
        rewrite: (p) => p.replace(/^\/api/, ''),
      },
    },
  },
});
