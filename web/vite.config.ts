import { defineConfig } from 'vitest/config';
import { loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

function formatBuildTime(date: Date): string {
  // Build runs in a UTC container; render the timestamp in UTC+8.
  const utc8 = new Date(date.getTime() + 8 * 3600 * 1000);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${utc8.getUTCFullYear()}-${pad(utc8.getUTCMonth() + 1)}-${pad(utc8.getUTCDate())} ${pad(
    utc8.getUTCHours(),
  )}:${pad(utc8.getUTCMinutes())}`;
}

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, '.', '');
  // Build commit is injected as a Docker build arg (VITE_GIT_COMMIT) so the footer can show it.
  const buildCommit = env.VITE_GIT_COMMIT || 'dev';
  const buildTime = formatBuildTime(new Date());
  return {
    plugins: [react()],
    define: {
      __BUILD_COMMIT__: JSON.stringify(buildCommit),
      __BUILD_TIME__: JSON.stringify(buildTime),
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
