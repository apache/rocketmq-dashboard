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

async function loadStore(persistedEngine?: string, persistedVersion = 1) {
  vi.resetModules();
  if (persistedEngine !== undefined) {
    localStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({ state: { engine: persistedEngine }, version: persistedVersion }),
    );
  }
  return (await import('../engineStore')).useEngineStore;
}

describe('engineStore', () => {
  afterEach(() => {
    vi.resetModules();
    localStorage.clear();
  });

  it('defaults to claude-code when no preference is persisted', async () => {
    const store = await loadStore();
    expect(store.getState().engine).toBe('claude-code');
  });

  it('restores a persisted supported engine', async () => {
    const store = await loadStore('qoder');
    expect(store.getState().engine).toBe('qoder');
  });

  it('updates the persisted engine through the action', async () => {
    const store = await loadStore('claude-code');
    store.getState().setEngine('http');
    expect(store.getState().engine).toBe('http');
    expect(JSON.parse(localStorage.getItem(STORAGE_KEY) ?? '{}').state.engine).toBe('http');
  });

  it('migrates stale persisted engines back to claude-code', async () => {
    const store = await loadStore('stale-engine', 0);
    expect(store.getState().engine).toBe('claude-code');
  });
});
