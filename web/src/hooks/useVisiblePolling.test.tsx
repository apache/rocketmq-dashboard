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

  it('keeps the in-flight guard when the poll callback changes', async () => {
    const first = deferred();
    const oldPoll = vi.fn().mockReturnValue(first.promise);
    const newPoll = vi.fn().mockResolvedValue(undefined);
    const { rerender } = renderHook(({ poll }) => useVisiblePolling(true, 1_000, poll), {
      initialProps: { poll: oldPoll },
    });

    await act(async () => {
      vi.advanceTimersByTime(1_000);
      await Promise.resolve();
    });
    rerender({ poll: newPoll });

    await act(async () => {
      document.dispatchEvent(new Event('visibilitychange'));
      vi.advanceTimersByTime(1_000);
      await Promise.resolve();
    });
    expect(newPoll).not.toHaveBeenCalled();

    await act(async () => {
      first.resolve();
      await first.promise;
      await Promise.resolve();
      await Promise.resolve();
    });
    await act(async () => {
      vi.advanceTimersByTime(1_000);
      await Promise.resolve();
    });
    expect(newPoll).toHaveBeenCalledTimes(1);
  });

  it.each(['disable', 'unmount', 'hidden', 'replace'] as const)(
    'discardsAQueuedPollAfter%sTest',
    async (change) => {
      const poll = vi.fn().mockResolvedValue(undefined);
      const replacement = vi.fn().mockResolvedValue(undefined);
      const { rerender, unmount } = renderHook(
        ({ enabled, callback }) => useVisiblePolling(enabled, 1_000, callback),
        { initialProps: { enabled: true, callback: poll } },
      );

      // Queue the poll synchronously, then change the effect's world before its microtask runs.
      act(() => {
        document.dispatchEvent(new Event('visibilitychange'));
      });
      if (change === 'disable') rerender({ enabled: false, callback: poll });
      if (change === 'replace') rerender({ enabled: true, callback: replacement });
      if (change === 'unmount') unmount();
      if (change === 'hidden') visibilityState = 'hidden';

      await act(async () => {
        await Promise.resolve();
      });
      expect(poll).not.toHaveBeenCalled();

      if (change !== 'unmount') {
        visibilityState = 'visible';
        rerender({ enabled: true, callback: replacement });
        await act(async () => {
          vi.advanceTimersByTime(1_000);
          await Promise.resolve();
        });
        expect(replacement).toHaveBeenCalledTimes(1);
      }
    },
  );

  it('keeps polling after a hidden poll is discardedTest', async () => {
    const poll = vi.fn().mockResolvedValue(undefined);
    renderHook(() => useVisiblePolling(true, 1_000, poll));

    // A queued poll whose page went hidden before the microtask ran must be dropped...
    act(() => {
      document.dispatchEvent(new Event('visibilitychange'));
    });
    visibilityState = 'hidden';
    await act(async () => {
      await Promise.resolve();
    });
    expect(poll).not.toHaveBeenCalled();

    // ...and it must release the shared in-flight guard, or polling never resumes.
    visibilityState = 'visible';
    await act(async () => {
      vi.advanceTimersByTime(1_000);
      await Promise.resolve();
    });
    expect(poll).toHaveBeenCalledTimes(1);
  });
});
