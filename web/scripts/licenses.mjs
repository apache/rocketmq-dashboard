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
import { createHash } from 'node:crypto';
import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('../', import.meta.url));
const legalName = /^(licen[sc]e|notice|copying|copyright|patents|authors)([._-].*)?$/i;
const approved = new Set(['MIT', 'Apache-2.0', 'BSD-2-Clause', 'BSD-3-Clause', 'ISC', '0BSD']);
const sha = (data) => createHash('sha256').update(data).digest('hex');
const read = (name) => {
  const data = readFileSync(name);
  if (!data.toString().trim()) throw new Error(`empty license material: ${name}`);
  return data;
};
const json = (name) => JSON.parse(read(name));
const slash = (name) => name.split(path.sep).join('/');

function packageRoot(id, base) {
  let dir = path.dirname(id);
  while (dir !== base && dir !== path.dirname(dir)) {
    const file = path.join(dir, 'package.json');
    if (existsSync(file) && json(file).name) return dir;
    dir = path.dirname(dir);
  }
  throw new Error(`cannot find the package.json of an actually bundled module: ${id}`);
}

function legalFiles(dir) {
  const result = [];
  function walk(current) {
    for (const entry of readdirSync(current, { withFileTypes: true })) {
      if (['node_modules', '.git', 'test', 'tests', '__tests__'].includes(entry.name)) continue;
      const file = path.join(current, entry.name);
      if (entry.isDirectory()) walk(file);
      else if (legalName.test(entry.name) && !/\.(js|cjs|mjs|ts|tsx|jsx|class|map|svg|png|jpg|gif|woff2?)$/i.test(entry.name)) {
        if (!entry.isFile()) throw new Error(`license file must be a regular file: ${file}`);
        result.push(file);
      }
    }
  }
  walk(dir);
  return result.sort();
}

export function collectLicenses(moduleIds, base = root) {
  const packages = new Map();
  const files = new Map();
  const components = [];
  const baseText = (name) => read(path.join(base, name)).toString().split('\nThird-party source materials\n')[0];
  let license = baseText('LICENSE');
  let notice = baseText('NOTICE');
  const ids = [...new Set(moduleIds)].sort();
  let hasIcons = false;
  for (const original of ids) {
    const id = original.replace(/^\0/, '').split('?')[0];
    if (id.includes('src/assets/model-logos/') && id.endsWith('.svg')) hasIcons = true;
    if (id.includes('node_modules/')) {
      const dir = packageRoot(path.resolve(base, id), base);
      packages.set(dir, 'bundled-module');
    } else if (id.includes('vite/') || id.includes('commonjsHelpers')) {
      packages.set(path.join(base, 'node_modules/vite'), 'bundled-runtime-helper');
    }
  }
  // Tailwind's preflight CSS ends up in the bundle; do not omit it just because it is listed under devDependencies.
  for (const id of ids.filter((name) => name.endsWith('.css') && !name.includes('node_modules/'))) {
    if (/@tailwind\s+base\s*;/.test(read(path.resolve(base, id)).toString())) {
      packages.set(path.join(base, 'node_modules/tailwindcss'), 'bundled-preflight-css');
    }
  }
  if (hasIcons) packages.set(path.join(base, 'node_modules/@lobehub/icons-static-svg'), 'vendored-svg');
  if (packages.size === 0) throw new Error('no verifiable third-party component in the build; refuse to generate an empty manifest');

  for (const [dir, reason] of [...packages].sort(([a], [b]) => a.localeCompare(b, 'en'))) {
    const pkg = json(path.join(dir, 'package.json'));
    if (!pkg.version || !approved.has(pkg.license)) {
      throw new Error(`redistribution license requires manual review: ${pkg.name}@${pkg.version}: ${JSON.stringify(pkg.license)}`);
    }
    let sources = legalFiles(dir);
    const sourceURLs = {};
    const sourceNames = {};
    const sourceContent = {};
    const identity = `${pkg.name}@${pkg.version}`;
    if (identity === '@ant-design/icons-svg@4.5.0') {
      // Verified against the repository-level LICENSE at the npm gitHead; identical to the installed icons 5.6.1 text.
      const fallback = path.join(base, 'node_modules/@ant-design/icons/LICENSE');
      if (json(path.join(base, 'node_modules/@ant-design/icons/package.json')).version !== '5.6.1') {
        throw new Error('the icons license source version changed; re-verification required');
      }
      if (sha(read(fallback)) !== '5d367fb0a07340571542eb4ee8eb1add62d37b71bad6786a57c2e9a86bf76c70') {
        throw new Error('the icons upstream license text changed; re-verification required');
      }
      sources.push(fallback);
      sourceURLs[fallback] = 'https://github.com/ant-design/ant-design-icons/blob/e6d33f4ba94ebe9f4b8648373342505b88e51a57/LICENSE';
    }
    if (['agent-base@6.0.2', 'https-proxy-agent@5.0.1'].includes(identity)) {
      // These two installed modules put the full MIT text in their README; extract only the complete license section, do not infer copyright.
      const source = path.join(dir, 'README.md');
      const match = read(source).toString().match(/\(The MIT License\)\r?\n[\s\S]*?SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE\.\r?\n/);
      if (!match) throw new Error(`the complete upstream license section is missing from the README: ${identity}`);
      sources.push(source);
      sourceNames[source] = 'LICENSE-from-README';
      sourceContent[source] = Buffer.from(match[0]);
    }
    if (identity === 'toggle-selection@1.0.6') {
      // The 1.0.6 npm package omits LICENSE; the index.js from the immutable upstream commit below is identical to the package, and the MIT text is supplied.
      const fallback = path.join(base, 'licenses/toggle-selection-1.0.6/LICENSE');
      if (sha(read(path.join(dir, 'index.js'))) !== 'd1a1caf366f8ae5ed3cf4a87c42c46e73bb5536acbcbc5ca479c180d4d3e7756'
          || sha(read(fallback)) !== '5149051aed807f78acfbf9a43ac66368374a8fa1f9dfc092b73de5a67d42673a') {
        throw new Error('toggle-selection code or upstream license text does not match the verified source');
      }
      sources.push(fallback, path.join(dir, 'README.md'));
      sourceURLs[fallback] = 'https://github.com/sudodoki/toggle-selection/blob/888650b271ee4937e2baff6a43bee744635601e3/LICENSE';
      sourceNames[path.join(dir, 'README.md')] = 'ATTRIBUTION';
    }
    if (reason === 'vendored-svg') {
      if (pkg.version !== '1.95.0') throw new Error('the LobeHub version changed; the vendored SVG source must be re-approved');
      const vendoredDir = path.join(base, 'src/assets/model-logos');
      const icons = readdirSync(vendoredDir).filter((name) => name.endsWith('.svg')).sort();
      if (icons.length !== 17) throw new Error('the vendored SVG list changed; the source must be re-approved');
      for (const name of icons) {
        if (!read(path.join(vendoredDir, name)).equals(read(path.join(dir, 'icons', name)))) {
          throw new Error(`a vendored icon does not match the locked upstream text: ${name}`);
        }
      }
      const fallback = path.join(vendoredDir, 'LICENSE');
      sources = [...new Set([...sources, fallback])];
      sourceURLs[fallback] = 'https://github.com/lobehub/lobe-icons/blob/v1.95.0/LICENSE';
      notice += '\nModel brand icons from LobeHub (https://github.com/lobehub/lobe-icons):\nCopyright (c) 2023 LobeHub.\n';
    }
    if (!sources.some((file) => /^(licen[sc]e|copying)([._-].*)?$/i.test(sourceNames[file] || path.basename(file)))) {
      throw new Error(`missing the complete upstream license text: ${pkg.name}@${pkg.version}`);
    }
    const component = { name: pkg.name, version: pkg.version, license: pkg.license, reason, files: [] };
    if (identity === 'toggle-selection@1.0.6') {
      component.payloadSource = { sha256: sha(read(path.join(dir, 'index.js'))),
        upstream: 'https://github.com/sudodoki/toggle-selection/blob/888650b271ee4937e2baff6a43bee744635601e3/index.js' };
    }
    for (const source of sources) {
      const data = sourceContent[source] || read(source);
      if (/^(licen[sc]e|copying)/i.test(sourceNames[source] || path.basename(source)) && data.length < 300) {
        throw new Error(`license text too short; a link or SPDX id cannot replace the full text: ${source}`);
      }
      const relative = sourceNames[source] || (sourceURLs[source] ? 'LICENSE' : slash(path.relative(dir, source)));
      const target = `legal/licenses/${pkg.name}@${pkg.version}/${relative}.txt`;
      if (files.has(target) && !files.get(target).equals(data)) throw new Error(`conflicting license file: ${target}`);
      files.set(target, data);
      component.files.push({ path: target, source: sourceURLs[source] || slash(path.relative(base, source)), sha256: sha(data) });
      if (/^notice([._-]|$)/i.test(path.basename(source))) {
        notice += `\n--- ${pkg.name} ${pkg.version} / ${relative} ---\n${data}\n`;
      }
    }
    components.push(component);
    license += `\n${pkg.name} ${pkg.version} (${pkg.license}): legal/licenses/${pkg.name}@${pkg.version}/\n`;
  }
  files.set('LICENSE', Buffer.from(license));
  files.set('NOTICE', Buffer.from(notice));
  return { files, components, modules: ids };
}

export function distributionLicenses() {
  let base;
  return {
    name: 'distribution-licenses',
    apply: 'build',
    enforce: 'post',
    configResolved(config) { base = config.root; },
    generateBundle(_options, bundle) {
      const ids = new Set();
      for (const item of Object.values(bundle)) {
        if (item.type !== 'chunk') continue;
        for (const [id, module] of Object.entries(item.modules)) {
          if (module.renderedLength > 0 || /\.(css|svg)(\?|$)/.test(id)) {
            ids.add(id.startsWith('\0') ? id : slash(path.relative(base, id)));
          }
        }
      }
      const result = collectLicenses([...ids], base);
      const outputFiles = {};
      for (const [name, item] of Object.entries(bundle)) {
        outputFiles[name] = sha(item.type === 'chunk' ? item.code : item.source);
      }
      const manifest = { modules: result.modules, components: result.components, outputFiles, files: {} };
      for (const [name, data] of result.files) {
        manifest.files[name] = sha(data);
        this.emitFile({ type: 'asset', fileName: name, source: data });
      }
      this.emitFile({ type: 'asset', fileName: 'legal/manifest.json', source: `${JSON.stringify(manifest, null, 2)}\n` });
    },
  };
}

export function checkDistribution(directory = path.join(root, 'dist'), base = root) {
  const manifest = json(path.join(directory, 'legal/manifest.json'));
  const result = collectLicenses(manifest.modules, base);
  if (JSON.stringify(manifest.components) !== JSON.stringify(result.components)) throw new Error('dependency versions/license texts changed; a rebuild is required');
  if (Object.keys(manifest.files).length !== result.files.size) throw new Error('the license manifest is incomplete');
  for (const [name, data] of result.files) {
    if (manifest.files[name] !== sha(data) || !read(path.join(directory, name)).equals(data)) throw new Error(`legal material modified or missing: ${name}`);
  }
  for (const [name, checksum] of Object.entries(manifest.outputFiles)) {
    const file = path.resolve(directory, name);
    if (!file.startsWith(`${path.resolve(directory)}${path.sep}`) || sha(read(file)) !== checksum) throw new Error(`build artifact verification failed: ${name}`);
  }
  if (!statSync(path.join(directory, 'LICENSE')).isFile()) throw new Error('missing LICENSE');
  console.log(`web license check passed: ${result.components.length} actual components, ${result.files.size} license files`);
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    if (process.argv[2] === 'audit') {
      // Only pre-check the materials of installed candidate dependencies; this set is not used for the binary attribution manifest.
      const lock = json(path.join(root, 'package-lock.json'));
      const candidates = Object.entries(lock.packages)
        .filter(([name, pkg]) => name.includes('node_modules/') && !pkg.dev && existsSync(path.join(root, name, 'package.json')))
        .map(([name]) => `${name}/index.js`);
      candidates.push('src/assets/model-logos/openai.svg', 'node_modules/vite/index.js', 'node_modules/tailwindcss/index.js');
      const errors = [];
      for (const id of candidates) {
        try { collectLicenses([id]); } catch (error) { errors.push(error.message); }
      }
      console.log(`license pre-check: ${candidates.length} candidate components, ${errors.length} gaps; the final distribution manifest is still based on the actually bundled modules`);
      if (errors.length) throw new Error(errors.join('\n'));
    } else {
      if (process.argv[2] !== 'check') throw new Error('usage: node scripts/licenses.mjs check [dist] | audit');
      checkDistribution(process.argv[3]);
    }
  } catch (error) {
    console.error(`license gate: ${error.message}`);
    process.exitCode = 1;
  }
}
