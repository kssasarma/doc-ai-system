import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// https://vitejs.dev/config/
export default defineConfig({
  base: '/docs-inator/',
  plugins: [react()],
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
  },
  build: {
    rollupOptions: {
      output: {
        // Split large, rarely-changing vendor libraries out of the main entry chunk — they cache
        // independently of app code (which changes every deploy) and independently of each
        // other, so a deploy that only touches app code doesn't invalidate the vendor chunks a
        // returning visitor already has cached.
        manualChunks: {
          'vendor-react': ['react', 'react-dom', 'react-router-dom'],
          'vendor-motion': ['framer-motion'],
          'vendor-http': ['axios'],
        },
      },
    },
  },
});
