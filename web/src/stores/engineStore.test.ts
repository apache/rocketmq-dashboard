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

import { beforeEach, describe, expect, it, vi } from 'vitest';

const STORAGE_KEY = 'rocketmq-studio-agent-engine';

describe('engineStore', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.resetModules();
  });

  it('defaults to the claude-code engine', async () => {
    const { useEngineStore } = await import('./engineStore');

    expect(useEngineStore.getState().engine).toBe('claude-code');
  });

  it('setEngine switches the active engine', async () => {
    const { useEngineStore } = await import('./engineStore');

    useEngineStore.getState().setEngine('qoder');

    expect(useEngineStore.getState().engine).toBe('qoder');
  });

  it('persists the chosen engine with the current schema version', async () => {
    const { useEngineStore } = await import('./engineStore');

    useEngineStore.getState().setEngine('http');

    const persisted = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? '{}');
    expect(persisted.state.engine).toBe('http');
    expect(persisted.version).toBe(1);
  });

  it('migrates stale persisted engines back to the default', async () => {
    localStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({ state: { engine: 'http' }, version: 0 }),
    );
    vi.resetModules();

    const { useEngineStore } = await import('./engineStore');

    expect(useEngineStore.getState().engine).toBe('claude-code');
  });
});
