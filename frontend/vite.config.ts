import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

// In development the Vite server proxies /api to the backend started from the IDE (option C).
// In Docker, nginx serves the build and proxies /api (same origin, no CORS).
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: { '/api': 'http://localhost:8080' },
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
    // three.js alone is about 580 kB; it is lazy-loaded in its own chunk (see App.tsx).
    chunkSizeWarningLimit: 650,
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    restoreMocks: true,
    unstubGlobals: true,
  },
});
