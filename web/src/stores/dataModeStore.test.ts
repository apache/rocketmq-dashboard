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

const STORAGE_KEY = 'rocketmq-studio-data-mode';

async function loadStore(envValue: string, persistedValue?: boolean) {
  vi.stubEnv('VITE_USE_MOCK', envValue);
  vi.resetModules();
  if (persistedValue !== undefined) {
    localStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({ state: { useMock: persistedValue }, version: 0 }),
    );
  }
  return (await import('./dataModeStore')).useDataModeStore;
}

describe('dataModeStore', () => {
  afterEach(() => {
    vi.unstubAllEnvs();
    vi.resetModules();
    localStorage.clear();
  });

  it.each([
    ['true', true],
    ['false', false],
  ])('uses VITE_USE_MOCK=%s as the initial mode', async (envValue, expected) => {
    const store = await loadStore(envValue);

    expect(store.getState().useMock).toBe(expected);
  });

  it.each([
    ['true', false],
    ['false', true],
  ])('prefers a persisted mode over VITE_USE_MOCK=%s', async (envValue, persistedValue) => {
    const store = await loadStore(envValue, persistedValue);

    expect(store.getState().useMock).toBe(persistedValue);
  });
});
