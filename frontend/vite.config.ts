import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5789,
    host: '0.0.0.0',
    strictPort: true,
    proxy: {
      // Proxy /api/* to the Spring Boot server
      '/api': {
        target: 'http://localhost:8090',
        changeOrigin: true,
        rewrite: (p) => p.replace(/^\/api/, ''),
      },
    },
  },
});
