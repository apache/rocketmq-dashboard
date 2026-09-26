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
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, readFileSync, writeFileSync, rmSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import test from 'node:test';
import { build } from 'vite';
import { checkDistribution, collectLicenses, distributionLicenses } from './licenses.mjs';

const root = fileURLToPath(new URL('../', import.meta.url));
function temporary(t) {
  const directory = mkdtempSync(path.join(root, 'node_modules/.license-test-'));
  t.after(() => rmSync(directory, { recursive: true, force: true }));
  return directory;
}
function write(directory, name, content) {
  const target = path.join(directory, name);
  mkdirSync(path.dirname(target), { recursive: true });
  writeFileSync(target, content);
}

test('vendoredSvgAndRuntimeOnlyTest', () => {
  const result = collectLicenses(['src/assets/model-logos/openai.svg', 'node_modules/react/index.js']);
  assert.deepEqual(result.components.map((c) => c.name), ['@lobehub/icons-static-svg', 'react']);
  assert.match(result.files.get('NOTICE').toString(), /Copyright \(c\) 2023 LobeHub/);
  assert.match(result.files.get('legal/licenses/@lobehub/icons-static-svg@1.95.0/LICENSE.txt').toString(), /THE SOFTWARE IS PROVIDED "AS IS"/);
  assert.doesNotMatch(result.files.get('LICENSE').toString(), /src\/assets\/model-logos/);
});

test('upstreamFallbacksKeepCompleteTextTest', () => {
  const result = collectLicenses(['node_modules/@ant-design/icons-svg/index.js', 'node_modules/toggle-selection/index.js',
    'node_modules/agent-base/index.js', 'node_modules/https-proxy-agent/index.js']);
  assert([...result.files.keys()].every((name) => !name.endsWith('.svg.txt')));
  assert.match(result.files.get('legal/licenses/agent-base@6.0.2/LICENSE-from-README.txt').toString(), /Copyright \(c\) 2013 Nathan Rajlich/);
  assert.match(result.files.get('legal/licenses/toggle-selection@1.0.6/ATTRIBUTION.txt').toString(), /shvaikalesh/);
  assert.match(result.files.get('legal/licenses/@ant-design/icons-svg@4.5.0/LICENSE.txt').toString(), /2018-present Ant UED/);
});

test('missingOrUnknownLicenseFailsTest', (t) => {
  const directory = temporary(t);
  for (const name of ['LICENSE', 'NOTICE']) write(directory, name, readFileSync(path.join(root, name)));
  write(directory, 'node_modules/example/package.json', JSON.stringify({ name: 'example', version: '1', license: 'MIT' }));
  assert.throws(() => collectLicenses(['node_modules/example/index.js'], directory), /missing the complete upstream license/);
  write(directory, 'node_modules/example/LICENSE', 'MIT');
  assert.throws(() => collectLicenses(['node_modules/example/index.js'], directory), /license text too short/);
  write(directory, 'node_modules/example/package.json', JSON.stringify({ name: 'example', version: '1', license: 'UNLICENSED' }));
  assert.throws(() => collectLicenses(['node_modules/example/index.js'], directory), /manual review/);
});

test('vitePackagingAndTamperGateTest', async (t) => {
  const directory = temporary(t);
  const outDir = path.join(directory, 'dist');
  // Build only fixtures for React, CSS, SVG and small dependencies; do not build the app or run app tests.
  const result = await build({
    root,
    configFile: false,
    logLevel: 'error',
    plugins: [
      {
        name: 'license-fixture',
        resolveId(id) { if (id === 'license-fixture') return '\0license-fixture'; },
        load(id) {
          if (id === '\0license-fixture') return `import React from '${root}node_modules/react/index.js'; import logo from '${root}src/assets/model-logos/openai.svg'; import '${root}src/index.css'; import toggle from '${root}node_modules/toggle-selection/index.js'; console.log(React, logo, toggle);`;
        },
      },
      distributionLicenses(),
    ],
    build: { write: true, outDir, emptyOutDir: true, minify: false, rollupOptions: { input: 'license-fixture' } },
  });
  void result;
  checkDistribution(outDir);
  const manifest = JSON.parse(readFileSync(path.join(outDir, 'legal/manifest.json')));
  assert(manifest.components.some((c) => c.name === 'react'));
  assert(manifest.components.some((c) => c.name === '@lobehub/icons-static-svg'));
  assert(manifest.components.some((c) => c.name === 'tailwindcss' && c.reason === 'bundled-preflight-css'));
  assert(manifest.components.some((c) => c.name === 'toggle-selection'));
  assert(!manifest.components.some((c) => c.name === 'vitest' || c.name === 'eslint'));
  write(outDir, 'NOTICE', 'tampered');
  assert.throws(() => checkDistribution(outDir), /modified or missing/);
  write(outDir, 'NOTICE', result.output.find((item) => item.fileName === 'NOTICE').source);
  const output = Object.keys(manifest.outputFiles)[0];
  write(outDir, output, 'tampered');
  assert.throws(() => checkDistribution(outDir), /build artifact verification failed/);
});

test('vitePackagingSurvivesDynamicImportPreloadInjectionTest', async (t) => {
  // vite:build-import-analysis injects the __vite__mapDeps preload map into the entry chunk
  // in its own generateBundle, which runs after this plugin's. The manifest must therefore be
  // finalized at writeBundle, or the checksums never match the written entry chunk.
  const directory = temporary(t);
  write(directory, 'package.json', JSON.stringify({ name: 'license-fixture-app', version: '1.0.0', license: 'Apache-2.0', private: true }));
  write(directory, 'LICENSE', readFileSync(path.join(root, 'LICENSE')));
  write(directory, 'entry.mjs', `import React from '${root}node_modules/react/index.js'; import('./lazy.mjs').then((lazy) => console.log(React, lazy));`);
  write(directory, 'lazy.mjs', `import toggle from '${root}node_modules/toggle-selection/index.js'; export default toggle;`);
  const outDir = path.join(directory, 'dist');
  await build({
    root,
    configFile: false,
    logLevel: 'error',
    plugins: [distributionLicenses()],
    build: {
      write: true,
      outDir,
      emptyOutDir: true,
      minify: false,
      rollupOptions: { input: path.join(directory, 'entry.mjs') },
    },
  });
  checkDistribution(outDir);
});
