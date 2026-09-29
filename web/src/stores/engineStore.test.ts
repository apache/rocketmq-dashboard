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
  const { useEngineStore } = await import('./engineStore');
  await useEngineStore.persist.rehydrate();
  return useEngineStore;
}

describe('agent engine preference', () => {
  afterEach(() => {
    vi.resetModules();
    localStorage.clear();
  });

  it('defaults to Claude Code when no preference is stored', async () => {
    const store = await loadStore();

    expect(store.getState().engine).toBe('claude-code');
  });

  it('restores the selected engine after the store is reloaded', async () => {
    const store = await loadStore();
    store.getState().setEngine('qoder');

    const reloadedStore = await loadStore();
    expect(reloadedStore.getState().engine).toBe('qoder');
  });

  it('resets a version-zero engine preference during migration', async () => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({ state: { engine: 'http' }, version: 0 }));

    const store = await loadStore();

    expect(store.getState().engine).toBe('claude-code');
    expect(JSON.parse(localStorage.getItem(STORAGE_KEY) ?? '{}')).toMatchObject({
      state: { engine: 'claude-code' },
      version: 1,
    });
  });
});
