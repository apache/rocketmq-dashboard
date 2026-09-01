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
import { useVisiblePolling } from './useVisiblePolling';

function deferred() {
  let resolve!: () => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<void>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, resolve, reject };
}

describe('useVisiblePolling', () => {
  let visibilityState = 'visible';

  beforeEach(() => {
    vi.useFakeTimers();
    vi.spyOn(document, 'visibilityState', 'get').mockImplementation(
      () => visibilityState as DocumentVisibilityState,
    );
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it('does not overlap interval and visibility-triggered polls', async () => {
    const first = deferred();
    const poll = vi.fn().mockReturnValueOnce(first.promise).mockResolvedValue(undefined);

    renderHook(() => useVisiblePolling(true, 1_000, poll));

    await act(async () => {
      vi.advanceTimersByTime(1_000);
      await Promise.resolve();
    });
    expect(poll).toHaveBeenCalledTimes(1);

    await act(async () => {
      vi.advanceTimersByTime(2_000);
      document.dispatchEvent(new Event('visibilitychange'));
      await Promise.resolve();
    });
    expect(poll).toHaveBeenCalledTimes(1);

    await act(async () => {
      first.resolve();
      await first.promise;
      await Promise.resolve();
    });

    await act(async () => {
      vi.advanceTimersByTime(1_000);
      await Promise.resolve();
    });
    expect(poll).toHaveBeenCalledTimes(2);
  });

  it('releases the in-flight guard after a rejected poll', async () => {
    const first = deferred();
    const poll = vi.fn().mockReturnValueOnce(first.promise).mockResolvedValue(undefined);

    renderHook(() => useVisiblePolling(true, 1_000, poll));
    await act(async () => {
      vi.advanceTimersByTime(1_000);
      await Promise.resolve();
      first.reject(new Error('temporary failure'));
      await Promise.resolve();
      await Promise.resolve();
    });

    await act(async () => {
      vi.advanceTimersByTime(1_000);
      await Promise.resolve();
    });
    expect(poll).toHaveBeenCalledTimes(2);
  });

  it('stops scheduling polls after unmount', async () => {
    const poll = vi.fn().mockResolvedValue(undefined);
    const { unmount } = renderHook(() => useVisiblePolling(true, 1_000, poll));

    unmount();
    await act(async () => {
      vi.advanceTimersByTime(2_000);
      document.dispatchEvent(new Event('visibilitychange'));
      await Promise.resolve();
    });

    expect(poll).not.toHaveBeenCalled();
  });

  it('does not schedule polls for a non-finite or non-positive interval', async () => {
    const poll = vi.fn().mockResolvedValue(undefined);

    const first = renderHook(() => useVisiblePolling(true, Number.NaN, poll));
    await act(async () => {
      vi.advanceTimersByTime(10_000);
      await Promise.resolve();
    });
    expect(poll).not.toHaveBeenCalled();
    first.unmount();

    renderHook(() => useVisiblePolling(true, 0, poll));
    await act(async () => {
      vi.advanceTimersByTime(10_000);
      await Promise.resolve();
    });
    expect(poll).not.toHaveBeenCalled();
  });

  it('keeps polling when the callback identity changes on re-render', async () => {
    let calls = 0;
    const { rerender } = renderHook(
      ({ onPoll }: { onPoll: () => void }) => useVisiblePolling(true, 1_000, onPoll),
      { initialProps: { onPoll: () => { calls += 1; } } },
    );

    await act(async () => {
      vi.advanceTimersByTime(500);
      await Promise.resolve();
    });

    // A re-render replaces the inline callback's identity (e.g. a parent re-rendering
    // while loading data). If the subscription depends on the callback, the interval is
    // re-created here and the next tick is delayed past t=1000.
    rerender({ onPoll: () => { calls += 1; } });
    await act(async () => {
      await Promise.resolve();
    });

    await act(async () => {
      vi.advanceTimersByTime(500);
      await Promise.resolve();
    });

    expect(calls).toBe(1);
  });

  it('waits for a hidden page to become visible', async () => {
    visibilityState = 'hidden';
    const poll = vi.fn().mockResolvedValue(undefined);

    renderHook(() => useVisiblePolling(true, 1_000, poll));
    await act(async () => {
      vi.advanceTimersByTime(2_000);
      await Promise.resolve();
    });
    expect(poll).not.toHaveBeenCalled();

    visibilityState = 'visible';
    await act(async () => {
      document.dispatchEvent(new Event('visibilitychange'));
      await Promise.resolve();
    });
    expect(poll).toHaveBeenCalledTimes(1);
  });
});
