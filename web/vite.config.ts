/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import { defineConfig } from 'vitest/config';
import { loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
import { distributionLicenses } from './scripts/licenses.mjs';

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
    plugins: [react(), distributionLicenses()],
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
      // Scope vitest to src/; scripts/*.test.mjs are node:test files run by `npm run license:test`.
      include: ['src/**/*.{test,spec}.?(c|m)[jt]s?(x)'],
      css: true,
      // antd interactions driven through userEvent are slow in jsdom, and the default
      // 5s budget is exceeded once the whole suite runs in parallel.
      testTimeout: 20000,
      hookTimeout: 20000,
    },
  };
});
