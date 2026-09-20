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
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { AiTimelineVO, TimelineEvent, TimelineItem } from '../../../api/aiEvents';
import { useConversationTimeline } from './useConversationTimeline';

vi.mock('../../../api/aiConversations', () => ({
  getConversationTimeline: vi.fn(),
}));

import { getConversationTimeline, type AiActiveRunRef } from '../../../api/aiConversations';

/**
 * The timeline endpoint is a forward cursor while a chat view needs the whole transcript, so the
 * property worth pinning is the walk: page forward until the cursor is exhausted, stop at the bound,
 * and never let a response for a conversation the user already left land on screen.
 */

const timelineMock = vi.mocked(getConversationTimeline);

function item(seq: number, event: TimelineEvent, runId = 41): TimelineItem {
  return { id: seq, turn: 3, seq, runId, createdAt: '2026-09-20T02:12:00', event };
}

function page(
  items: TimelineItem[],
  nextAfter: number | null,
  activeRun: AiActiveRunRef | null = null,
): AiTimelineVO {
  return { items, nextAfter, activeRun };
}

function render(conversationId: number | null = 7, limit?: number, maxPages?: number) {
  return renderHook(
    ({ id }: { id: number | null }) => useConversationTimeline(id, { limit, maxPages }),
    {
      initialProps: { id: conversationId },
    },
  );
}

describe('useConversationTimeline', () => {
  beforeEach(() => {
    timelineMock.mockReset();
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it('pagesForwardUntilTheCursorIsExhaustedTest', async () => {
    timelineMock
      .mockResolvedValueOnce(page([item(1, { type: 'user', text: '查看集群状态' })], 1))
      .mockResolvedValueOnce(
        page([item(2, { type: 'text', text: '集群当前有 2 个 broker。' })], null),
      );

    const { result } = render();

    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(timelineMock).toHaveBeenNthCalledWith(1, 7, { after: 0, limit: 200 });
    expect(timelineMock).toHaveBeenNthCalledWith(2, 7, { after: 1, limit: 200 });
    expect(result.current.items.map((row) => row.seq)).toEqual([1, 2]);
    expect(result.current.lastSeq).toBe(2);
    expect(result.current.hasMore).toBe(false);
  });

  it('foldsRowsIntoBubblesAndExposesTheActiveRunTest', async () => {
    timelineMock.mockResolvedValue(
      page(
        [
          item(1, { type: 'user', text: '查看集群状态' }),
          item(2, { type: 'thinking', text: '先查 topic 路由', source: 'model' }),
          item(3, { type: 'text', text: '集群当前有 2 个 broker。' }),
        ],
        null,
        { id: 41, status: 'RUNNING' },
      ),
    );

    const { result } = render();

    await waitFor(() => expect(result.current.activeRun).toEqual({ id: 41, status: 'RUNNING' }));
    expect(result.current.bubbles).toEqual([
      {
        role: 'user',
        blocks: [{ kind: 'text', text: '查看集群状态' }],
        turn: 3,
        createdAt: '2026-09-20T02:12:00',
      },
      {
        role: 'assistant',
        blocks: [
          { kind: 'thinking', text: '先查 topic 路由', source: 'model' },
          { kind: 'text', text: '集群当前有 2 个 broker。' },
        ],
        turn: 3,
        createdAt: '2026-09-20T02:12:00',
      },
    ]);
  });

  it('stopsAtTheMaxPagesBoundAndReportsThatThereIsMoreTest', async () => {
    // A pathological conversation must not turn the initial load into an unbounded request loop.
    timelineMock.mockImplementation(async (_id, params) =>
      page([item((params?.after ?? 0) + 1, { type: 'text', text: 'x' })], (params?.after ?? 0) + 1),
    );

    const { result } = render(7, 5, 3);

    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(timelineMock).toHaveBeenCalledTimes(3);
    expect(result.current.items).toHaveLength(3);
    expect(result.current.hasMore).toBe(true);
  });

  it('appendsTheNextCursorPageOnLoadMoreTest', async () => {
    timelineMock
      .mockResolvedValueOnce(page([item(1, { type: 'text', text: 'first' })], 1))
      .mockResolvedValueOnce(page([item(2, { type: 'text', text: 'second' })], null));

    // maxPages 1 bounds the initial walk so a live cursor survives for loadMore to continue from;
    // left unbounded, the initial load would page forward and drain both mocked pages itself.
    const { result } = render(7, 1, 1);

    await waitFor(() => expect(result.current.hasMore).toBe(true));
    await act(async () => {
      await result.current.loadMore();
    });

    await waitFor(() => expect(result.current.hasMore).toBe(false));
    expect(timelineMock).toHaveBeenLastCalledWith(7, { after: 1, limit: 1 });
    expect(result.current.items.map((row) => row.seq)).toEqual([1, 2]);
  });

  it('discardsAResponseForAConversationTheUserAlreadyLeftTest', async () => {
    let releaseStale: (value: AiTimelineVO) => void = () => undefined;
    const stale = new Promise<AiTimelineVO>((resolve) => {
      releaseStale = resolve;
    });
    timelineMock.mockReturnValueOnce(stale);
    timelineMock.mockResolvedValue(
      page([item(9, { type: 'text', text: 'from conversation 8' })], null),
    );

    const { result, rerender } = render(7);
    rerender({ id: 8 });

    await waitFor(() => expect(result.current.items.map((row) => row.seq)).toEqual([9]));

    releaseStale(page([item(1, { type: 'text', text: 'from conversation 7' })], null));
    await waitFor(() => expect(timelineMock).toHaveBeenCalledTimes(2));
    // The late response is for a transcript that is no longer on screen.
    expect(result.current.items.map((row) => row.event)).toEqual([
      { type: 'text', text: 'from conversation 8' },
    ]);
    expect(result.current.loading).toBe(false);
  });

  it('refetchReloadsTheWholeTranscriptAndIsAwaitableTest', async () => {
    timelineMock
      .mockResolvedValueOnce(page([item(1, { type: 'text', text: 'before' })], null))
      .mockResolvedValueOnce(
        page(
          [item(1, { type: 'text', text: 'before' }), item(2, { type: 'text', text: 'after' })],
          null,
        ),
      );

    const { result } = render();

    await waitFor(() => expect(result.current.items).toHaveLength(1));
    // `useAgentRun` awaits this in its finally block, so it has to be a real promise.
    await act(async () => {
      await expect(result.current.refetch()).resolves.toBeUndefined();
    });

    expect(timelineMock).toHaveBeenLastCalledWith(7, { after: 0, limit: 200 });
    expect(result.current.items).toHaveLength(2);
  });

  it('reportsTheServerMessageOnFailureTest', async () => {
    timelineMock.mockRejectedValue(new Error('会话不存在'));

    const { result } = render();

    await waitFor(() => expect(result.current.error).toBe('会话不存在'));
    expect(result.current.loading).toBe(false);
    expect(result.current.items).toEqual([]);
  });

  it('staysEmptyWithoutAConversationTest', async () => {
    const { result } = render(null);

    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(timelineMock).not.toHaveBeenCalled();
    expect(result.current.bubbles).toEqual([]);
    expect(result.current.lastSeq).toBe(0);
  });
});
