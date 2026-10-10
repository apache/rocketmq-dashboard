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
import { useAgentRun } from './useAgentRun';
import { useConversationTimeline } from './useConversationTimeline';
import { openRunStream, type RunStreamHandlers } from '../../../api/ai';
import { getConversationTimeline, reportRunSpeed } from '../../../api/aiConversations';

vi.mock('../../../api/ai', () => ({ openRunStream: vi.fn(), attachRunStream: vi.fn() }));
vi.mock('../../../api/aiConversations', () => ({
  getConversationTimeline: vi.fn(),
  stopRun: vi.fn(),
  reportRunSpeed: vi.fn(),
}));

it('keepsTheLiveAnswerWhenTheRealTimelineRefreshFailsTest', async () => {
  vi.mocked(getConversationTimeline)
    .mockResolvedValueOnce({ items: [], nextAfter: null, activeRun: null })
    .mockRejectedValueOnce(new Error('timeline unavailable'));
  let handlers!: RunStreamHandlers;
  let finish!: () => void;
  vi.mocked(openRunStream).mockImplementation((_id, _body, callbacks) => {
    handlers = callbacks;
    return new Promise<void>((resolve) => {
      finish = resolve;
    });
  });
  const { result } = renderHook(() => {
    const timeline = useConversationTimeline(7);
    const run = useAgentRun(7, { refetchTimeline: timeline.refetch });
    return { timeline, run };
  });
  await waitFor(() => expect(result.current.timeline.loading).toBe(false));
  let sent!: Promise<void>;
  await act(async () => {
    sent = result.current.run.send(7, { message: 'hello' });
  });
  await act(async () => {
    handlers.onEvent({ type: 'text_delta', content: 'answer not yet in history' });
  });
  const liveAnswer = result.current.run.blocksRef.current;
  expect(liveAnswer.length).toBeGreaterThan(0);
  await act(async () => {
    finish();
    await sent;
  });
  expect(result.current.timeline.error).toBe('timeline unavailable');
  expect(result.current.run.blocksRef.current).toEqual(liveAnswer);
  expect(result.current.run.error).toBe('timeline unavailable');
});

describe('timeline ownership while reporting run speed', () => {
  beforeEach(() => {
    vi.mocked(getConversationTimeline).mockReset().mockResolvedValue({
      items: [],
      nextAfter: null,
      activeRun: null,
    });
    vi.mocked(reportRunSpeed).mockReset();
    vi.mocked(openRunStream).mockReset();
  });
  afterEach(() => vi.restoreAllMocks());

  async function finishMeasuredRun() {
    let settleReport!: (failure?: Error) => void;
    vi.mocked(reportRunSpeed).mockImplementation(
      () =>
        new Promise<void>((resolve, reject) => {
          settleReport = (failure) => (failure ? reject(failure) : resolve());
        }),
    );
    const streams: { handlers: RunStreamHandlers; finish: () => void }[] = [];
    vi.mocked(openRunStream).mockImplementation(
      (_id, _body, handlers) =>
        new Promise<void>((finish) => {
          streams.push({ handlers, finish });
        }),
    );
    const view = renderHook(
      ({ id }) => {
        const timeline = useConversationTimeline(id);
        return { timeline, run: useAgentRun(id, { refetchTimeline: timeline.refetch }) };
      },
      { initialProps: { id: 7 } },
    );
    await waitFor(() => expect(view.result.current.timeline.loading).toBe(false));
    let sent!: Promise<void>;
    await act(async () => {
      sent = view.result.current.run.send(7, { message: 'first question' });
      const { handlers } = streams[0];
      handlers.onEvent({
        type: 'run_started',
        runId: 41,
        conversationId: 7,
        turn: 1,
        title: 'first',
      });
      const clock = vi.spyOn(Date, 'now').mockReturnValue(10000);
      handlers.onEvent({ type: 'text_delta', content: 'first ' });
      clock.mockReturnValue(13000);
      handlers.onEvent({ type: 'text_delta', content: 'answer' });
      clock.mockRestore();
      streams[0].finish();
    });
    expect(reportRunSpeed).toHaveBeenCalledWith(41, expect.any(Number));
    expect(getConversationTimeline).toHaveBeenCalledTimes(1);
    return { ...view, streams, sent, settleReport };
  }

  it.each(['navigation', 'unmount', 'new run'] as const)(
    'doesNotReloadAfterAReportOutlivesItsContextTest (%s)',
    async (context) => {
      const view = await finishMeasuredRun();
      let nextSend: Promise<void> | undefined;
      if (context === 'navigation') {
        view.rerender({ id: 8 });
        await waitFor(() => expect(view.result.current.timeline.loading).toBe(false));
        expect(getConversationTimeline).toHaveBeenLastCalledWith(8, { after: 0, limit: 200 });
      } else if (context === 'unmount') {
        view.unmount();
      } else {
        await act(async () => {
          nextSend = view.result.current.run.send(7, { message: 'next question' });
          view.streams[1].handlers.onEvent({ type: 'text_delta', content: 'next answer' });
        });
      }
      const calls = vi.mocked(getConversationTimeline).mock.calls.length;
      await act(async () => {
        view.settleReport();
        await view.sent;
      });
      expect(getConversationTimeline).toHaveBeenCalledTimes(calls);
      if (context === 'new run') {
        expect(view.result.current.run.isStreaming).toBe(true);
        expect(view.result.current.run.blocksRef.current).toEqual([
          { kind: 'text', text: 'next answer' },
        ]);
        await act(async () => {
          view.streams[1].finish();
          await nextSend;
        });
        expect(getConversationTimeline).toHaveBeenCalledTimes(calls + 1);
        expect(view.result.current.run.isStreaming).toBe(false);
        expect(view.result.current.run.blocksRef.current).toEqual([]);
      }
    },
  );

  it.each([false, true])(
    'reloadsTheOwnedRunAfterReportSettlesTest (failure=%s)',
    async (failed) => {
      const view = await finishMeasuredRun();
      await act(async () => {
        view.settleReport(failed ? new Error('speed unavailable') : undefined);
        await view.sent;
      });
      expect(getConversationTimeline).toHaveBeenCalledTimes(2);
      expect(getConversationTimeline).toHaveBeenLastCalledWith(7, { after: 0, limit: 200 });
      expect(view.result.current.run.blocksRef.current).toEqual([]);
      expect(view.result.current.run.pendingUserMessage).toBeNull();
    },
  );
});
