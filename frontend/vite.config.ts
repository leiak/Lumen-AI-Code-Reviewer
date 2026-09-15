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
        target: process.env.REVIEW_BACKEND_URL || 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (p) => p.replace(/^\/api/, ''),
      },
    },
  },
});
