import { defineConfig } from 'vitest/config';
import { loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
import packageMetadata from './package.json';

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, '.', '');
  const buildTime = env.VITE_BUILD_TIME?.trim() || new Date().toISOString();
  return {
    plugins: [react()],
    define: {
      __STUDIO_VERSION__: JSON.stringify(packageMetadata.version),
      __STUDIO_BUILD_TIME__: JSON.stringify(buildTime),
    },
    build: {
      // Ant Design is shared by the application shell and most route components. Keep it
      // cacheable as one vendor chunk rather than splitting its cyclic internals.
      // The 1.3MB (gzip ~420KB) size is inherent to the dashboard UI surface; combined with
      // immutable asset caching in nginx.conf, repeat visits download it only once.
      chunkSizeWarningLimit: 1400,
      rollupOptions: {
        output: {
          manualChunks: {
            react: ['react', 'react-dom', 'react-router-dom'],
            antd: ['antd', '@ant-design/icons'],
            // react-markdown / remark-gfm are only used by the lazily-loaded /ai page;
            // pinning them here would force the entry to preload them on first paint.
            // markdown: ['react-markdown', 'remark-gfm'],
          },
        },
      },
    },
    server: {
      port: 5173,
      proxy: {
        '/api': {
          target: env.VITE_API_PROXY_TARGET || 'http://localhost:8888',
          changeOrigin: true,
        },
      },
    },
    test: {
      globals: true,
      environment: 'jsdom',
      setupFiles: './src/test/setup.ts',
      css: true,
      // antd interactions driven through userEvent are slow in jsdom, and the default
      // 5s budget is exceeded once the whole suite runs in parallel.
      testTimeout: 20000,
      hookTimeout: 20000,
    },
  };
});
