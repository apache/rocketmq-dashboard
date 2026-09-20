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

import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { LlmConfig } from '../../../api/llm';
import { useLlmRuntime } from './useLlmRuntime';

vi.mock('../../../api/llm', () => ({
  getLlmConfig: vi.fn(),
  getLlmModels: vi.fn(),
}));

import { getLlmConfig, getLlmModels } from '../../../api/llm';

const configMock = vi.mocked(getLlmConfig);
const modelsMock = vi.mocked(getLlmModels);

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((res) => {
    resolve = res;
  });
  return { promise, resolve };
}

const config: LlmConfig = {
  provider: 'openai',
  engine: 'http',
  apiBase: 'https://example.invalid',
  model: 'gpt-test',
  maxTokens: 1024,
  temperature: 0,
  enabled: true,
  ready: true,
};

function render(enabled: boolean) {
  return renderHook(({ enabled }: { enabled: boolean }) => useLlmRuntime({ enabled }), {
    initialProps: { enabled },
  });
}

describe('useLlmRuntime', () => {
  beforeEach(() => {
    configMock.mockReset();
    modelsMock.mockReset();
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it('a late response for a disabled runtime never repopulates the state', async () => {
    const configDeferred = deferred<LlmConfig>();
    const modelsDeferred = deferred<unknown>();
    configMock.mockReturnValue(configDeferred.promise as never);
    modelsMock.mockReturnValue(modelsDeferred.promise as never);

    const { result, rerender } = render(true);
    await act(async () => {
      // getLlmConfig resolves before the toggle; getLlmModels is still in flight.
      configDeferred.resolve(config);
    });
    rerender({ enabled: false });
    expect(result.current.config).toBeNull();
    expect(result.current.modelOptions).toEqual([]);
    expect(result.current.llmReady).toBe(false);

    await act(async () => {
      modelsDeferred.resolve({ status: 0, data: [{ id: 'gpt-test' }] });
    });
    expect(result.current.config).toBeNull();
    expect(result.current.modelOptions).toEqual([]);
    expect(result.current.selectedModel).toBe('');
    expect(result.current.llmReady).toBe(false);
    expect(result.current.modelsLoading).toBe(false);
  });

  it('never repopulates the state when the config response resolves after the toggle', async () => {
    // The config request is still in flight when the runtime is disabled; once it finally
    // resolves it belongs to a superseded load and must be dropped entirely.
    const configDeferred = deferred<LlmConfig>();
    configMock.mockReturnValue(configDeferred.promise as never);
    modelsMock.mockResolvedValue({ status: 0, data: [] });

    const { result, rerender } = render(true);
    rerender({ enabled: false });
    await act(async () => {
      configDeferred.resolve(config);
    });
    expect(result.current.config).toBeNull();
    expect(result.current.modelOptions).toEqual([]);
    expect(result.current.llmReady).toBe(false);
    expect(modelsMock).not.toHaveBeenCalled();
  });

  it('keeps the last request winning when reload overlaps', async () => {
    // The initial effect load, the first reload and the second reload each suspend on the
    // deferred the mock hands out at call time. Resolving the latest request first proves the
    // later-arriving superseded response cannot overwrite it.
    const superseded = deferred<LlmConfig>();
    const latest = deferred<LlmConfig>();
    const latestConfig: LlmConfig = { ...config, model: 'gpt-latest' };
    let current = superseded;
    configMock.mockImplementation(() => current.promise as never);
    modelsMock.mockResolvedValue({ status: 0, data: [] });

    const { result } = render(true);
    await act(async () => {
      const first = result.current.reload();
      current = latest;
      const second = result.current.reload();
      current = superseded;
      latest.resolve(latestConfig);
      superseded.resolve(config);
      await Promise.all([first, second]);
    });
    expect(result.current.config).toEqual(latestConfig);
    expect(result.current.config?.model).toBe('gpt-latest');
  });
});
