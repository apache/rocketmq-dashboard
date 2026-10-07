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

async function loadDataMode() {
  vi.resetModules();
  const { isMockMode } = await import('./dataMode');
  const { useDataModeStore } = await import('../stores/dataModeStore');
  return { isMockMode, store: useDataModeStore };
}

describe('dataMode service', () => {
  afterEach(() => {
    vi.unstubAllEnvs();
    vi.resetModules();
    localStorage.clear();
  });

  it.each(['true', 'false'])('reflectsRuntimeTogglesWithViteUseMock_%sTest', async (envValue) => {
    vi.stubEnv('VITE_USE_MOCK', envValue);
    const { isMockMode, store } = await loadDataMode();
    const initialMode = envValue === 'true';

    expect(isMockMode()).toBe(initialMode);

    store.getState().toggle();
    expect(isMockMode()).toBe(!initialMode);

    store.getState().toggle();
    expect(isMockMode()).toBe(initialMode);
  });

  it.each([
    ['false', true],
    ['true', false],
  ] as const)('usesPersistedModeInsteadOfBuildDefault_%sTest', async (envValue, persistedMode) => {
    vi.stubEnv('VITE_USE_MOCK', envValue);
    localStorage.setItem(
      'rocketmq-studio-data-mode',
      JSON.stringify({ state: { useMock: persistedMode }, version: 0 }),
    );
    const { isMockMode, store } = await loadDataMode();

    expect(isMockMode()).toBe(persistedMode);

    store.getState().toggle();
    expect(isMockMode()).toBe(!persistedMode);
  });
});
