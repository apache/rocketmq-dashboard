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
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { AiTimelineVO } from '../../../api/aiEvents';
import { useActiveRunAttach } from './useActiveRunAttach';
import { useConversationTimeline } from './useConversationTimeline';
import { useAgentRun } from './useAgentRun';
import { attachRunStream } from '../../../api/ai';

vi.mock('../../../api/ai', () => ({ attachRunStream: vi.fn(), openRunStream: vi.fn() }));

vi.mock('../../../api/aiConversations', () => ({
  getConversationTimeline: vi.fn(),
}));

import { getConversationTimeline } from '../../../api/aiConversations';

/**
 * `useActiveRunAttach` decides what to re-attach; `useConversationTimeline` is what it trusts to say
 * which run belongs to the conversation on screen. They are exercised together because the property
 * is the handover between them: an attach is addressed by run id alone, so one made while the user
 * switches conversation streams the previous conversation's frames into the new transcript.
 */

const timelineMock = vi.mocked(getConversationTimeline);

function page(activeRun: AiTimelineVO['activeRun']): AiTimelineVO {
  return { items: [], nextAfter: null, activeRun };
}

describe('useActiveRunAttach', () => {
  afterEach(() => vi.useRealTimers());

  it('discoversRunningAfterTheQueuedAttachAlreadyClosedTest', async () => {
    vi.useFakeTimers();
    timelineMock
      .mockReset()
      .mockResolvedValueOnce(page({ id: 41, status: 'QUEUED' }))
      .mockResolvedValueOnce(page({ id: 41, status: 'QUEUED' }))
      .mockResolvedValue(page({ id: 41, status: 'RUNNING' }));
    vi.mocked(attachRunStream)
      .mockResolvedValueOnce(undefined)
      .mockImplementation(() => new Promise<void>(() => {}));
    const { result, unmount } = renderHook(() => {
      const timeline = useConversationTimeline(7);
      const run = useAgentRun(7, { refetchTimeline: timeline.refetch });
      useActiveRunAttach(7, timeline, run);
      return { timeline, run };
    });
    await act(async () => {});
    expect(attachRunStream).toHaveBeenCalledTimes(1);
    expect(result.current.run.isStreaming).toBe(false);
    await act(async () => {
      await vi.advanceTimersByTimeAsync(1000);
    });
    await act(async () => {
      await vi.advanceTimersByTimeAsync(1000);
    });
    expect(result.current.timeline.activeRun?.status).toBe('RUNNING');
    expect(attachRunStream).toHaveBeenCalledTimes(2);
    expect(result.current.run.isStreaming).toBe(true);
    const reads = timelineMock.mock.calls.length;
    await act(async () => {
      await vi.advanceTimersByTimeAsync(10_000);
    });
    expect(timelineMock).toHaveBeenCalledTimes(reads);
    unmount();
  });

  it.each(['unmount', 'navigation', 'failure', 'budget'] as const)(
    'stopsQueuedDiscoveryTest (%s)',
    async (ending) => {
      vi.useFakeTimers();
      timelineMock.mockReset().mockResolvedValue(page({ id: 41, status: 'QUEUED' }));
      const attach = vi.fn().mockResolvedValue(undefined);
      const view = renderHook(
        ({ id }) => {
          const timeline = useConversationTimeline(id);
          useActiveRunAttach(id, timeline, { isStreaming: false, attach });
        },
        { initialProps: { id: 7 as number | null } },
      );
      await act(async () => {});
      if (ending === 'unmount') view.unmount();
      else if (ending === 'navigation') {
        view.rerender({ id: null });
        await act(async () => {});
      } else if (ending === 'failure') {
        timelineMock.mockRejectedValue(new Error('offline'));
        await act(async () => {
          await vi.advanceTimersByTimeAsync(1000);
        });
        expect(timelineMock).toHaveBeenCalledTimes(2);
      } else {
        for (let i = 0; i < 30; i++) {
          await act(async () => {
            await vi.advanceTimersByTimeAsync(1000);
          });
        }
        expect(timelineMock).toHaveBeenCalledTimes(31);
      }
      const reads = timelineMock.mock.calls.length;
      await act(async () => {
        await vi.advanceTimersByTimeAsync(60_000);
      });
      expect(timelineMock).toHaveBeenCalledTimes(reads);
      expect(attach).toHaveBeenCalledTimes(1);
      view.unmount();
    },
  );
  it('attachesOnlyTheRunOfTheConversationOnScreenTest', async () => {
    let resolveSecond: (value: AiTimelineVO) => void = () => {};
    timelineMock.mockImplementation((id) =>
      id === 7
        ? Promise.resolve(page({ id: 41, status: 'RUNNING' }))
        : new Promise<AiTimelineVO>((resolve) => {
            resolveSecond = resolve;
          }),
    );
    const attach = vi.fn().mockResolvedValue(undefined);

    const { rerender } = renderHook(
      ({ id }: { id: number | null }) =>
        useActiveRunAttach(id, useConversationTimeline(id), { isStreaming: false, attach }),
      { initialProps: { id: 7 } },
    );

    await waitFor(() => expect(attach).toHaveBeenCalledWith(7, 41, 0));

    // The user switches conversation and the new timeline has not answered yet, so the last thing the
    // server said is still conversation 7's run.
    rerender({ id: 9 });

    expect(attach).toHaveBeenCalledTimes(1);

    // Once conversation 9's own timeline lands, its run is the one that gets attached.
    await act(async () => {
      resolveSecond(page({ id: 77, status: 'RUNNING' }));
    });

    await waitFor(() => expect(attach).toHaveBeenCalledWith(9, 77, 0));
    expect(attach).toHaveBeenCalledTimes(2);
  });

  it('reattachesWhenTheRunAdvancesPastTheAdmissionWindowItWasAttachedInTest', async () => {
    const attach = vi.fn().mockResolvedValue(undefined);
    const { rerender } = renderHook(
      ({ status }: { status: 'QUEUED' | 'RUNNING' }) =>
        useActiveRunAttach(
          7,
          { activeRun: { id: 42, status }, lastSeq: 0, refetch: vi.fn() },
          { isStreaming: false, attach },
        ),
      { initialProps: { status: 'QUEUED' as 'QUEUED' | 'RUNNING' } },
    );

    // A reload during admission attaches to a QUEUED row that has no worker yet: the server closes
    // that stream with a bare `done` and no status, so nothing was observed.
    await waitFor(() => expect(attach).toHaveBeenCalledWith(7, 42, 0));

    // The run now has a worker. Without re-attaching, the answer streams with nobody watching and
    // the next send is refused with 409.
    rerender({ status: 'RUNNING' });
    await waitFor(() => expect(attach).toHaveBeenCalledTimes(2));

    // The same state again is not new information, so the guard still holds and nothing loops.
    rerender({ status: 'RUNNING' });
    await new Promise((resolve) => setTimeout(resolve, 20));
    expect(attach).toHaveBeenCalledTimes(2);
  });
});
