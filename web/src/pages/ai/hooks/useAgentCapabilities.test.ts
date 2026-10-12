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

import { act, renderHook, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { AiAgentCapabilitiesVO } from '../../../api/aiEvents';
import { useAgentCapabilities } from './useAgentCapabilities';

vi.mock('../../../api/aiConversations', () => ({
  getAgentCapabilities: vi.fn(),
}));

import { getAgentCapabilities } from '../../../api/aiConversations';

const capabilitiesMock = vi.mocked(getAgentCapabilities);

function deferred<T>() {
  let resolve: (value: T) => void = () => undefined;
  let reject: (reason?: unknown) => void = () => undefined;
  const promise = new Promise<T>((release, fail) => {
    resolve = release;
    reject = fail;
  });
  return { promise, resolve, reject };
}

function capabilities(rmqctlAvailable: boolean): AiAgentCapabilitiesVO {
  return {
    rmqctlAvailable,
    claudeAvailable: true,
    qoderAvailable: true,
    mcpEnabled: true,
    l3ToolsAllowed: false,
  };
}

describe('useAgentCapabilities', () => {
  beforeEach(() => {
    capabilitiesMock.mockReset();
  });

  it('failedReprobeDoesNotRetainAnOlderUnavailableResultTest', async () => {
    const firstProbe = deferred<AiAgentCapabilitiesVO>();
    const secondProbe = deferred<AiAgentCapabilitiesVO>();
    capabilitiesMock
      .mockReturnValueOnce(firstProbe.promise)
      .mockReturnValueOnce(secondProbe.promise);

    const { result, rerender } = renderHook(
      ({ enabled }: { enabled: boolean }) => useAgentCapabilities(enabled),
      { initialProps: { enabled: true } },
    );

    await act(async () => {
      firstProbe.resolve(capabilities(false));
      await firstProbe.promise;
    });
    expect(result.current).toBe(false);

    rerender({ enabled: false });
    expect(capabilitiesMock).toHaveBeenCalledTimes(1);

    rerender({ enabled: true });
    await waitFor(() => expect(capabilitiesMock).toHaveBeenCalledTimes(2));
    expect(result.current).toBe(true);

    await act(async () => {
      secondProbe.reject(new Error('temporary network failure'));
      await secondProbe.promise.catch(() => undefined);
    });
    expect(result.current).toBe(true);
  });
});
