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

import { afterEach, describe, expect, it, vi } from 'vitest';

const STORAGE_KEY = 'rocketmq-studio-agent-engine';

async function loadStore() {
  vi.resetModules();
  return (await import('./engineStore')).useEngineStore;
}

describe('engineStore', () => {
  afterEach(() => {
    localStorage.clear();
    vi.resetModules();
  });

  it('defaultsToClaudeCodeWithoutAPersistedPreferenceTest', async () => {
    const store = await loadStore();

    expect(store.getState().engine).toBe('claude-code');
  });

  it.each(['claude-code', 'qoder', 'http'] as const)(
    'persistsAndRestoresTheSelectedEngine_%sTest',
    async (engine) => {
      const store = await loadStore();

      store.getState().setEngine(engine);

      expect(store.getState().engine).toBe(engine);
      expect(JSON.parse(localStorage.getItem(STORAGE_KEY)!)).toEqual({
        state: { engine },
        version: 1,
      });

      const reloadedStore = await loadStore();

      expect(reloadedStore).not.toBe(store);
      expect(reloadedStore.getState().engine).toBe(engine);
    },
  );

  it.each(['qoder', 'stale-engine'])(
    'migratesVersionZeroPreference_%sToClaudeCodeTest',
    async (engine) => {
      localStorage.setItem(STORAGE_KEY, JSON.stringify({ state: { engine }, version: 0 }));

      const store = await loadStore();

      expect(store.getState().engine).toBe('claude-code');
      expect(JSON.parse(localStorage.getItem(STORAGE_KEY)!)).toEqual({
        state: { engine: 'claude-code' },
        version: 1,
      });

      const reloadedStore = await loadStore();

      expect(reloadedStore).not.toBe(store);
      expect(reloadedStore.getState().engine).toBe('claude-code');
    },
  );
});
