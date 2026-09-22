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
import { afterEach, beforeEach, describe, expect, it, vi, type Mock } from 'vitest';
import type { ChatSseEvent } from '../../../api/aiEvents';
import type { RenderBlock } from '../render/blocks';
import { useAgentRun, type UseAgentRunOptions } from './useAgentRun';

vi.mock('../../../api/ai', () => ({
  openRunStream: vi.fn(),
  attachRunStream: vi.fn(),
}));

vi.mock('../../../api/aiConversations', () => ({
  stopRun: vi.fn(),
}));

import { attachRunStream, openRunStream, type RunStreamHandlers } from '../../../api/ai';
import { stopRun } from '../../../api/aiConversations';

/**
 * `useAgentRun` is the performance-critical hook of the AI page, so these tests assert the three
 * properties that a rewrite is most likely to lose:
 *
 * 1. frames mutate a ref and cost ONE coalesced tick, not one state update per token;
 * 2. frames of a generation the user navigated away from are dropped, not rendered;
 * 3. the finally block refetches the persisted timeline BEFORE clearing the live blocks — the
 *    ordering that makes the streaming bubble turn into history without a flash;
 * 4. stop calls the API and does NOT abort the reader, because the open stream is what delivers the
 *    terminal `run_status` frame and `done`.
 */

interface StreamCall {
  args: unknown[];
  handlers: RunStreamHandlers;
  signal: AbortSignal;
  emit: (event: ChatSseEvent) => void;
  /** Resolve the stream, as the `event: done` frame does. */
  finish: () => void;
  /** Reject the stream, as a transport error does. */
  fail: (error: unknown) => void;
}

function stubStreamFunction(fn: unknown): StreamCall[] {
  const streamMock = fn as Mock<(...args: unknown[]) => Promise<void>>;
  const calls: StreamCall[] = [];
  streamMock.mockImplementation((...args: unknown[]) => {
    const handlers = args[2] as RunStreamHandlers;
    const signal = args[3] as AbortSignal;
    let finish = (): void => undefined;
    let fail: (error: unknown) => void = () => undefined;
    const promise = new Promise<void>((resolve, reject) => {
      finish = resolve;
      fail = (error: unknown) => reject(error);
    });
    // An unhandled rejection would fail the run outside the assertion that expects it.
    promise.catch(() => undefined);
    calls.push({
      args,
      handlers,
      signal,
      emit: (event) => handlers.onEvent(event),
      finish,
      fail,
    });
    return promise;
  });
  return calls;
}

/** Let the requestAnimationFrame-coalesced tick fire (jsdom paints at ~16ms). */
async function flushFrame(): Promise<void> {
  await new Promise((resolve) => setTimeout(resolve, 32));
}

const textDelta = (content: string): ChatSseEvent => ({ type: 'text_delta', content });

const runStarted = (runId = 41): ChatSseEvent => ({
  type: 'run_started',
  runId,
  conversationId: 7,
  title: '查看集群状态',
  turn: 3,
});

const runFinished = (status: 'COMPLETED' | 'STOPPED' = 'COMPLETED'): ChatSseEvent => ({
  type: 'run_finished',
  runId: 41,
  status,
  durationMs: 8123,
});

let openedStreams: StreamCall[] = [];
let attachedStreams: StreamCall[] = [];

describe('useAgentRun', () => {
  beforeEach(() => {
    openedStreams = stubStreamFunction(openRunStream);
    attachedStreams = stubStreamFunction(attachRunStream);
    vi.mocked(stopRun).mockReset();
    vi.mocked(stopRun).mockResolvedValue({
      id: 41,
      conversationId: 7,
      turn: 3,
      status: 'STOPPED',
      engine: 'claude-code',
      model: 'claude-sonnet-4-5',
      stopReason: 'USER_STOP',
    });
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  function render(options: Partial<UseAgentRunOptions> = {}, conversationId: number | null = 7) {
    const merged: UseAgentRunOptions = { refetchTimeline: vi.fn(), ...options };
    return renderHook(({ id }: { id: number | null }) => useAgentRun(id, merged), {
      initialProps: { id: conversationId },
    });
  }

  it('foldsFramesIntoTheBlocksRefAndBumpsOneCoalescedTickTest', async () => {
    const { result } = render();

    await act(async () => {
      void result.current.send(7, { message: '列出 topic' });
    });
    expect(result.current.isStreaming).toBe(true);
    const tickAfterSend = result.current.renderTick;

    await act(async () => {
      openedStreams[0].emit(runStarted());
      openedStreams[0].emit(textDelta('集群当前有 '));
      openedStreams[0].emit(textDelta('2 个 broker。'));
      await flushFrame();
    });

    // One ref mutation per frame, but ONE tick for the whole batch: three frames, one repaint.
    expect(result.current.renderTick).toBe(tickAfterSend + 1);
    expect(result.current.blocksRef.current).toEqual([
      { kind: 'text', text: '集群当前有 2 个 broker。' },
    ] satisfies RenderBlock[]);
    expect(result.current.runId).toBe(41);
    expect(openedStreams[0].args[0]).toBe(7);
    expect(openedStreams[0].args[1]).toEqual({ message: '列出 topic' });
  });

  it('skipsTheTickForFramesThatRenderNothingTest', async () => {
    const { result } = render();

    await act(async () => {
      void result.current.send(7, { message: 'hi' });
    });
    const tickAfterSend = result.current.renderTick;

    // run_started / run_finished carry run metadata and reduce to the very same array reference.
    await act(async () => {
      openedStreams[0].emit(runStarted());
      openedStreams[0].emit(runFinished());
      await flushFrame();
    });

    expect(result.current.renderTick).toBe(tickAfterSend);
    expect(result.current.blocksRef.current).toEqual([]);
    expect(result.current.lastStatus).toBe('COMPLETED');
  });

  it('opensOnlyOneStreamForADoubleSubmitTest', async () => {
    const { result } = render();

    await act(async () => {
      void result.current.send(7, { message: 'one' });
      void result.current.send(7, { message: 'two' });
    });

    // The server would answer the second one with 409; the UI must not even try.
    expect(openedStreams).toHaveLength(1);
    expect(openedStreams[0].args[1]).toEqual({ message: 'one' });
  });

  it('discardsFramesOfAGenerationTheUserNavigatedAwayFromTest', async () => {
    const { result, rerender } = render();

    await act(async () => {
      void result.current.send(7, { message: 'hi' });
    });
    const staleStream = openedStreams[0];
    await act(async () => {
      staleStream.emit(textDelta('from conversation 7'));
      await flushFrame();
    });
    expect(staleStream.signal.aborted).toBe(false);
    expect(result.current.blocksRef.current).toEqual([
      { kind: 'text', text: 'from conversation 7' },
    ]);

    // The route moved to another conversation: our reader is closed, the run keeps generating
    // server-side, and the blocks of the old transcript are dropped.
    await act(async () => {
      rerender({ id: 8 });
    });
    expect(staleStream.signal.aborted).toBe(true);
    expect(result.current.blocksRef.current).toEqual([]);
    expect(result.current.isStreaming).toBe(false);

    await act(async () => {
      staleStream.emit(textDelta('a late frame from conversation 7'));
      await flushFrame();
    });
    expect(result.current.blocksRef.current).toEqual([]);
  });

  it('refetchesTheTimelineBeforeClearingTheLiveBlocksTest', async () => {
    const order: string[] = [];
    let liveBlocksAtRefetch: RenderBlock[] = [];
    let releaseRefetch: () => void = () => undefined;
    const refetchTimeline = vi.fn(
      () =>
        new Promise<void>((resolve) => {
          order.push('refetch');
          liveBlocksAtRefetch = [...result.current.blocksRef.current];
          releaseRefetch = resolve;
        }),
    );
    const { result } = render({ refetchTimeline });

    let sent!: Promise<void>;
    await act(async () => {
      sent = result.current.send(7, { message: 'hi' });
    });
    await act(async () => {
      openedStreams[0].emit(textDelta('persisted answer'));
      await flushFrame();
    });

    // The done frame ends the stream, which runs the finally block.
    await act(async () => {
      openedStreams[0].finish();
      await flushFrame();
    });

    expect(refetchTimeline).toHaveBeenCalledTimes(1);
    // While the refetch is pending the live blocks are still on screen: clearing first would flash
    // an empty transcript.
    expect(liveBlocksAtRefetch).toEqual([{ kind: 'text', text: 'persisted answer' }]);
    expect(result.current.blocksRef.current).toEqual([{ kind: 'text', text: 'persisted answer' }]);
    expect(order).toEqual(['refetch']);

    await act(async () => {
      releaseRefetch();
      await sent;
    });
    order.push('liveBlocksCleared');

    expect(result.current.blocksRef.current).toEqual([]);
    expect(order).toEqual(['refetch', 'liveBlocksCleared']);
    expect(result.current.isStreaming).toBe(false);
  });

  it('keepsTheLiveBlocksWhenTheTimelineRefetchFailsTest', async () => {
    const onError = vi.fn();
    const refetchTimeline = vi.fn().mockRejectedValue(new Error('timeline unavailable'));
    const { result } = render({ refetchTimeline, onError });

    let sent!: Promise<void>;
    await act(async () => {
      sent = result.current.send(7, { message: 'hi' });
    });
    await act(async () => {
      openedStreams[0].emit(textDelta('the only copy of this answer'));
      openedStreams[0].finish();
      await flushFrame();
      await sent;
    });

    // With the reload failed the live blocks are the only copy left, so they must survive.
    expect(result.current.blocksRef.current).toEqual([
      { kind: 'text', text: 'the only copy of this answer' },
    ]);
    expect(result.current.error).toBe('timeline unavailable');
    expect(onError).toHaveBeenCalledTimes(1);
    expect(result.current.isStreaming).toBe(false);
  });

  it('clearsTheStoppingStateWhenTheStreamDiesWithoutTerminalFramesTest', async () => {
    const refetchTimeline = vi.fn().mockRejectedValue(new Error('timeline unavailable'));
    const { result } = render({ refetchTimeline });

    let sent!: Promise<void>;
    await act(async () => {
      sent = result.current.send(7, { message: 'hi' });
    });
    await act(async () => {
      openedStreams[0].emit(runStarted());
      openedStreams[0].emit(textDelta('partial'));
      await flushFrame();
    });
    await act(async () => {
      await result.current.stop();
    });
    expect(result.current.stopRequested).toBe(true);

    // The connection dies before `run_status` and `done` arrive, and the reload fails too.
    await act(async () => {
      openedStreams[0].fail(new Error('AI stream idle for more than 30s'));
      await flushFrame();
      await sent;
    });

    // There is nothing left to wait for, so the button must not stay pinned in `stopping`.
    expect(result.current.stopRequested).toBe(false);
    expect(result.current.isStreaming).toBe(false);
    expect(result.current.blocksRef.current).toEqual([{ kind: 'text', text: 'partial' }]);
    expect(result.current.error).toBe('AI stream idle for more than 30s');
  });

  it('stopsThroughTheApiWithoutAbortingTheStreamTest', async () => {
    const { result } = render();

    let sent!: Promise<void>;
    await act(async () => {
      sent = result.current.send(7, { message: 'hi' });
    });
    await act(async () => {
      openedStreams[0].emit(runStarted());
      openedStreams[0].emit(textDelta('partial'));
      await flushFrame();
    });
    expect(result.current.canStop).toBe(true);

    await act(async () => {
      await result.current.stop();
    });

    expect(stopRun).toHaveBeenCalledWith(41);
    // THE point: aborting here would mean never receiving run_status{STOPPED} and `done`, leaving
    // the button in `stopping` forever.
    expect(openedStreams[0].signal.aborted).toBe(false);
    expect(result.current.stopRequested).toBe(true);
    expect(result.current.canStop).toBe(false);

    // The terminal frames still arrive over the same reader.
    await act(async () => {
      openedStreams[0].emit(runFinished('STOPPED'));
      openedStreams[0].finish();
      await flushFrame();
      await sent;
    });

    expect(result.current.lastStatus).toBe('STOPPED');
    expect(result.current.stopRequested).toBe(false);
  });

  it('ignoresAStopBeforeTheRunStartedFrameTest', async () => {
    const { result } = render();

    await act(async () => {
      void result.current.send(7, { message: 'hi' });
    });

    // The run row exists before `run_started` is emitted, so there is a window with no id to stop.
    await act(async () => {
      await result.current.stop();
    });

    expect(stopRun).not.toHaveBeenCalled();
    expect(result.current.stopRequested).toBe(false);
    expect(result.current.canStop).toBe(false);
  });

  it('reportsAStaleStopRejectionAndLeavesTheStreamOpenTest', async () => {
    const onError = vi.fn();
    vi.mocked(stopRun).mockRejectedValue(new Error('该会话已有正在进行的回答'));
    const { result } = render({ onError });

    await act(async () => {
      void result.current.send(7, { message: 'hi' });
    });
    await act(async () => {
      openedStreams[0].emit(runStarted());
    });

    await act(async () => {
      await result.current.stop();
    });

    // A stale stop fails closed: the button returns to `stop` and the newer run is untouched.
    expect(result.current.error).toBe('该会话已有正在进行的回答');
    expect(result.current.stopRequested).toBe(false);
    expect(onError).toHaveBeenCalledTimes(1);
    expect(openedStreams[0].signal.aborted).toBe(false);
  });

  it('attachesToAnAlreadyRunningRunWithTheReplayCursorTest', async () => {
    const { result } = render();

    await act(async () => {
      void result.current.attach(7, 41, 12);
    });

    expect(attachedStreams).toHaveLength(1);
    expect(attachedStreams[0].args[0]).toBe(41);
    expect(attachedStreams[0].args[1]).toBe(12);
    expect(openedStreams).toHaveLength(0);

    await act(async () => {
      attachedStreams[0].emit(textDelta('resumed'));
      await flushFrame();
    });
    expect(result.current.blocksRef.current).toEqual([{ kind: 'text', text: 'resumed' }]);
    expect(result.current.isStreaming).toBe(true);
  });

  it('stopsAnAttachedRunThroughTheApiTest', async () => {
    const { result } = render();

    // The server replays persisted events on attach; the live vocabulary starts at run_started,
    // so no `run_started` frame ever arrives on this stream.
    await act(async () => {
      void result.current.attach(7, 41, 12);
    });
    await act(async () => {
      attachedStreams[0].emit(textDelta('resumed'));
      await flushFrame();
    });
    expect(result.current.canStop).toBe(true);

    await act(async () => {
      await result.current.stop();
    });

    expect(stopRun).toHaveBeenCalledWith(41);
    expect(result.current.stopRequested).toBe(true);
  });

  it('surfacesAStreamFailureAndStillRefetchesTheTimelineTest', async () => {
    const onError = vi.fn();
    const refetchTimeline = vi.fn().mockResolvedValue(undefined);
    const { result } = render({ refetchTimeline, onError });

    let sent!: Promise<void>;
    await act(async () => {
      sent = result.current.send(7, { message: 'hi' });
    });
    await act(async () => {
      openedStreams[0].emit(textDelta('partial'));
      openedStreams[0].fail(new Error('AI stream idle for more than 30s'));
      await flushFrame();
      await sent;
    });

    expect(result.current.error).toBe('AI stream idle for more than 30s');
    expect(onError).toHaveBeenCalledTimes(1);
    expect(refetchTimeline).toHaveBeenCalledTimes(1);
    // Whatever reached the database comes back on the reload; the live blocks are gone.
    expect(result.current.blocksRef.current).toEqual([]);
    expect(result.current.isStreaming).toBe(false);
  });

  it('doesNotReportAnAbortAsAnErrorTest', async () => {
    const onError = vi.fn();
    const { result, rerender } = render({ onError });

    await act(async () => {
      void result.current.send(7, { message: 'hi' });
    });
    const aborted = openedStreams[0];

    await act(async () => {
      rerender({ id: null });
    });
    await act(async () => {
      aborted.fail(new Error('The user aborted a request.'));
      await flushFrame();
    });

    expect(aborted.signal.aborted).toBe(true);
    expect(result.current.error).toBe('');
    expect(onError).not.toHaveBeenCalled();
  });

  it('keepsTheNewStreamGuardWhenAnAbortedStreamSettlesTest', async () => {
    const { result, rerender } = render();
    let firstSend!: Promise<void>;
    await act(async () => {
      firstSend = result.current.send(7, { message: 'first conversation' });
    });

    await act(async () => {
      rerender({ id: 8 });
    });
    expect(openedStreams[0].signal.aborted).toBe(true);

    await act(async () => {
      void result.current.send(8, { message: 'new conversation' });
    });
    await act(async () => {
      openedStreams[0].fail(new DOMException('Aborted', 'AbortError'));
      await firstSend;
    });

    await act(async () => {
      void result.current.send(8, { message: 'duplicate send' });
    });
    expect(openedStreams).toHaveLength(2);
    expect(result.current.isStreaming).toBe(true);
  });
});
