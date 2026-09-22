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

import { useCallback, useEffect, useReducer, useRef, useState } from 'react';
import { attachRunStream, openRunStream, type RunStreamHandlers } from '../../../api/ai';
import { reportRunSpeed, stopRun, type AiMessageRequest } from '../../../api/aiConversations';
import type {
  ChatSseEvent,
  LiveRunFinishedEvent,
  LiveRunStartedEvent,
  RunStatus,
} from '../../../api/aiEvents';
import type { RenderBlock } from '../render/blocks';
import { reduceLiveBlocks } from '../render/reduceLive';
import { createStreamSpeedTracker } from '../render/streamSpeed';
import { describeThrownMessage } from '../../../utils/apiError';

/**
 * Drives one agent run: open the stream, fold frames into render blocks, stop on request, and hand
 * the finished run back to the persisted timeline.
 *
 * ─── Why the blocks live in a ref ───────────────────────────────
 * A run streams thousands of `text_delta` frames. Putting them in state would mean one React update
 * per token, i.e. one reconciliation of the whole transcript per token. Instead every frame mutates
 * {@link UseAgentRunResult.blocksRef} and asks for a single `requestAnimationFrame`-coalesced tick,
 * so the cost per frame is one reducer call plus (at most) one integer increment — the transcript
 * repaints at display rate no matter how fast the model talks. Reading a ref during render is safe
 * here precisely because the tick is what schedules that render.
 *
 * ─── Two counters, two jobs ─────────────────────────────────────
 * - `generationRef` identifies the (re)attach. Every callback compares against it, so frames from a
 *   conversation the user navigated away from are dropped instead of repainting another
 *   transcript.
 * - `streamRequestIdRef` identifies the request, the `MessageQueryHistoryDrawer` idiom: it is
 *   re-read after every await, so a superseded stream can neither flip `isStreaming` nor clear the
 *   blocks that the current run owns.
 *
 * ─── Stop is not abort ──────────────────────────────────────────
 * `stop()` POSTs `/api/ai/runs/{id}/stop` and keeps reading. The open stream is what delivers the
 * terminal `run_status` frame and `done`; aborting on stop would leave the button spinning forever
 * and the transcript without its 已停止 marker. The `AbortController` is for real navigation away
 * only — and even then the run keeps generating server-side, which is the point of decoupling
 * generation from the HTTP connection.
 */

export interface UseAgentRunOptions {
  /**
   * Reload the persisted transcript. Awaited in the finally block BEFORE the live blocks are
   * cleared, normally `useConversationTimeline().refetch`.
   */
  refetchTimeline: () => Promise<void> | void;
  /** Any failure the caller should also toast about. The message itself lands in `error`. */
  onError?: (error: unknown) => void;
  /** Fired with the `run_started` frame; the runId is also exposed as state. */
  onRunStarted?: (event: LiveRunStartedEvent) => void;
  /** Fired with the terminal `run_finished` frame. */
  onRunFinished?: (event: LiveRunFinishedEvent) => void;
}

/**
 * The live render blocks of the run in flight.
 *
 * Declared here rather than as React's `RefObject<RenderBlock[]>` because that type's `current` is
 * nullable under `@types/react` 18, while this hook always initialises it: readers should not have
 * to defend against a null that cannot occur.
 */
export interface LiveBlocks {
  readonly current: RenderBlock[];
}

export interface UseAgentRunResult {
  /**
   * Live render blocks of the run in flight. Read during render; a mutation is always followed by a
   * {@link UseAgentRunResult.renderTick} bump.
   */
  blocksRef: LiveBlocks;
  /** Bumped once per animation frame that has new blocks. The only per-token state in this hook. */
  renderTick: number;
  /** A stream is open: either generating or waiting for the terminal frames after a stop. */
  isStreaming: boolean;
  /** Stop was requested and the terminal frames have not arrived yet. */
  stopRequested: boolean;
  /** True while the stop button should be reachable. */
  canStop: boolean;
  runId: number | null;
  /** Status of the last terminal frame; drives the 已停止/失败 marker on the bubble. */
  lastStatus: RunStatus | null;
  /** Server-supplied message, or `''`; the caller pairs it with an i18n fallback. */
  error: string;
  /**
   * Estimated generation speed of the run in flight (read during render, like `blocksRef`), or
   * null while there is not enough streaming to measure.
   */
  liveTokensPerSecond: number | null;
  /** Final speed of the most recent finished run; null when it produced no measurable text. */
  lastRunTokensPerSecond: number | null;
  /**
   * The prompt the run in flight was admitted with, or null. The stream carries no user frame and
   * the persisted transcript only gains the row at the end-of-run refetch, so the caller renders
   * this as an optimistic user bubble while the run is alive.
   */
  pendingUserMessage: string | null;
  /**
   * Send a message and stream the run it starts. Resolves once the terminal frames were processed
   * and the timeline refetch settled. A no-op while another run is in flight.
   */
  send: (conversationId: number, request: AiMessageRequest) => Promise<void>;
  /**
   * Re-attach to a run that is already generating (a reload, or a run started in another tab).
   * `after` must be the highest `seq` the caller already rendered — `useConversationTimeline().lastSeq`
   * — otherwise the replayed rows are rendered twice: once from the timeline and once as live blocks.
   */
  attach: (conversationId: number, runId: number, after: number) => Promise<void>;
  /** Ask the server to stop the current run. Does NOT abort the stream. */
  stop: () => Promise<void>;
  /**
   * Invalidate everything the current stream owns. Navigation away ONLY — never for stopping a run.
   */
  abort: () => void;
}

/**
 * @param conversationId the conversation on screen (the `/ai/c/:conversationId` route parameter, or
 *   null on the bare `/ai` route). It drives invalidation only: whenever it changes, the blocks,
 *   run id and reader of the previous conversation are dropped. `send`/`attach` still take an
 *   explicit id because a brand-new conversation is created by POST before the route knows about it;
 *   starting a stream for a conversation the route has already moved past is refused. Call `send`
 *   after the navigation that installs the new id, not before it.
 */
export function useAgentRun(
  conversationId: number | null,
  options: UseAgentRunOptions,
): UseAgentRunResult {
  const blocksRef = useRef<RenderBlock[]>([]);
  const speedTrackerRef = useRef(createStreamSpeedTracker());
  const [renderTick, bump] = useReducer((tick: number) => tick + 1, 0);

  const [isStreaming, setIsStreaming] = useState(false);
  const [stopRequested, setStopRequested] = useState(false);
  const [runId, setRunId] = useState<number | null>(null);
  const [lastStatus, setLastStatus] = useState<RunStatus | null>(null);
  const [error, setError] = useState('');
  const [lastRunTokensPerSecond, setLastRunTokensPerSecond] = useState<number | null>(null);
  const [liveTokensPerSecond, setLiveTokensPerSecond] = useState<number | null>(null);
  /**
   * The prompt the admitted run was started with, or null. The live stream carries no `user`
   * frame and the persisted transcript is only refetched when the run FINISHES, so without this
   * optimistic copy the operator's own question stays invisible for the whole run — minutes on a
   * long tool-heavy answer. Rendered as a user bubble ahead of the live assistant bubble; the
   * final refetch replaces it with the persisted row in one commit.
   */
  const [pendingUserMessage, setPendingUserMessage] = useState<string | null>(null);

  const generationRef = useRef(0);  const streamRequestIdRef = useRef(0);
  const chatInFlightRef = useRef(false);
  const abortControllerRef = useRef<AbortController | null>(null);
  const frameRef = useRef<number | null>(null);
  const runIdRef = useRef<number | null>(null);
  // Whether the in-flight run was admitted server-side: the `run_started` frame is the proof. A
  // stream that died before any frame arrived never admitted the run, so the transcript cannot
  // have gained the user row and the optimistic question must stay on screen.
  const admittedRef = useRef(false);
  const conversationIdRef = useRef<number | null>(conversationId);

  // Callbacks arrive as fresh closures on every render; keeping them in a ref is what lets `send`,
  // `attach` and `stop` stay referentially stable instead of being rebuilt (and re-firing effects)
  // whenever the caller inlines an arrow function.
  const optionsRef = useRef(options);
  useEffect(() => {
    optionsRef.current = options;
  });

  const cancelFrame = useCallback(() => {
    if (frameRef.current === null) return;
    cancelAnimationFrame(frameRef.current);
    frameRef.current = null;
  }, []);

  /** One repaint per frame, not per token: a scheduled frame absorbs every delta until it runs. */
  const scheduleTick = useCallback(() => {
    if (frameRef.current !== null) return;
    frameRef.current = requestAnimationFrame(() => {
      frameRef.current = null;
      // The speed label repaints with the blocks; equal values bail out of the extra render.
      setLiveTokensPerSecond(speedTrackerRef.current.tokensPerSecond());
      bump();
    });
  }, []);

  const handleEvent = useCallback(
    (event: ChatSseEvent, requestId: number, generation: number) => {
      // Frames of a conversation the user already left, or of a run a newer stream superseded.
      if (generation !== generationRef.current) return;
      if (requestId !== streamRequestIdRef.current) return;

      switch (event.type) {
        case 'run_started':
          admittedRef.current = true;
          runIdRef.current = event.runId;
          setRunId(event.runId);
          optionsRef.current.onRunStarted?.(event);
          break;
        case 'run_finished':
          setLastStatus(event.status);
          optionsRef.current.onRunFinished?.(event);
          break;
        case 'text_delta':
          speedTrackerRef.current.recordDelta(event.content);
          break;
        default:
          break;
      }

      const next = reduceLiveBlocks(blocksRef.current, event);
      // Run-level frames render nothing and come back as the same reference; skipping the tick keeps
      // a `run_started`/`run_finished` pair from costing two repaints.
      if (next === blocksRef.current) return;
      blocksRef.current = next;
      scheduleTick();
    },
    [scheduleTick],
  );

  /**
   * Persist the measured generation speed before the timeline refetch, so the refetched page
   * already carries it and the replayed bubble shows the same number without a fallback. The
   * report is best-effort: a failure only loses the persisted speed, never the answer — the live
   * fallback (`lastRunTokensPerSecond`) still shows the number for this session.
   */
  const persistRunSpeed = useCallback(async (): Promise<void> => {
    const targetRunId = runIdRef.current;
    const speed = speedTrackerRef.current.tokensPerSecond();
    if (targetRunId === null || speed === null) return;
    try {
      await reportRunSpeed(targetRunId, Math.round(speed * 10) / 10);
    } catch {
      // best-effort: keep the in-session fallback, drop nothing else
    }
  }, []);

  const finishStream = useCallback(
    async (requestId: number, controller: AbortController, streamFailure: unknown | null) => {
      if (abortControllerRef.current === controller) abortControllerRef.current = null;
      // Released before the refetch is awaited on purpose: a reload that hangs must not leave the
      // composer unable to send. A send that overtakes this finally block bumps the request id, and
      // the guard after the await below then keeps this run's cleanup off the newer one's state.
      chatInFlightRef.current = false;
      // A newer stream (or a navigation) owns the UI now; touching state here would clobber it.
      if (requestId !== streamRequestIdRef.current) return;

      setIsStreaming(false);
      // The stream is over, so there is nothing left to wait for whichever way it ended: clearing
      // this here keeps a failed reload from pinning the button in `stopping`.
      setStopRequested(false);

      // Refetch FIRST, clear the live blocks SECOND. The persisted rows already hold everything the
      // stream just rendered, so this order swaps the live bubble for its durable twin in one paint
      // instead of flashing an empty transcript. It is also what makes a run that died mid-stream
      // render correctly after a reload: whatever reached the database comes back.
      await persistRunSpeed();
      try {
        await optionsRef.current.refetchTimeline();
      } catch (refetchError) {
        if (requestId !== streamRequestIdRef.current) return;
        optionsRef.current.onError?.(refetchError);
        // Keep the live blocks: with the refetch failed they are the only copy of this answer. When
        // the stream failed too, that message is the informative one — a dead connection explains
        // the truncated answer, a failed reload only explains why it did not move into history.
        // The optimistic user bubble stays for the same reason: the transcript never got the row.
        if (streamFailure === null) setError(describeThrownMessage(refetchError));
        scheduleTick();
        return;
      }

      if (requestId !== streamRequestIdRef.current) return;
      // The speed of this run is final the moment the stream closes; persist it before the
      // refetch swaps the live bubble for its persisted twin, which is what displays it.
      setLastRunTokensPerSecond(speedTrackerRef.current.tokensPerSecond());
      // The refetched transcript now renders the persisted user row; drop the optimistic twin in
      // the same commit so it never paints twice. When the stream failed WITHOUT a `run_started`
      // frame the run was never admitted: the transcript cannot have gained the user row, so the
      // optimistic question stays instead of vanishing from the screen.
      if (streamFailure === null || admittedRef.current) setPendingUserMessage(null);
      blocksRef.current = [];
      scheduleTick();
    },
    [persistRunSpeed, scheduleTick],
  );

  const startStream = useCallback(
    async (
      targetConversationId: number,
      open: (handlers: RunStreamHandlers, signal: AbortSignal) => Promise<void>,
      knownRunId: number | null = null,
      userMessage?: string,
    ): Promise<void> => {
      // Double-submit guard: Enter twice in one tick must not admit two runs (the server would
      // reject the second with 409 anyway, but the UI should not even try).
      if (chatInFlightRef.current) return;
      // The route already moved on to another conversation: refuse to start a stream whose frames
      // the generation guard would drop anyway.
      if (
        conversationIdRef.current !== null &&
        conversationIdRef.current !== targetConversationId
      ) {
        return;
      }
      chatInFlightRef.current = true;
      // Only `send` carries a prompt; a re-attach finds the user row already in the timeline.
      if (userMessage !== undefined) setPendingUserMessage(userMessage);

      const requestId = ++streamRequestIdRef.current;
      const generation = ++generationRef.current;
      const controller = new AbortController();
      abortControllerRef.current = controller;

      cancelFrame();
      blocksRef.current = [];
      admittedRef.current = false;
      speedTrackerRef.current = createStreamSpeedTracker();
      // A fresh run starts: the speed of the previous one no longer belongs to the bubble on
      // screen once this run's answer lands.
      setLastRunTokensPerSecond(null);
      setLiveTokensPerSecond(null);
      // `send` learns the id from the live `run_started` frame; an attach already knows it from
      // the URL, and the server never replays `run_started` — without seeding it here the stop
      // button would render but address nothing until the run finished.
      runIdRef.current = knownRunId;
      setRunId(knownRunId);
      setLastStatus(null);
      setError('');
      setStopRequested(false);
      setIsStreaming(true);
      bump();

      let streamFailure: unknown | null = null;
      try {
        await open(
          { onEvent: (event) => handleEvent(event, requestId, generation) },
          controller.signal,
        );
      } catch (streamError) {
        // Aborted means "the user navigated away": the run keeps going server-side and a later
        // attach picks it up, so this is not an error to report.
        if (!controller.signal.aborted) {
          streamFailure = streamError;
          if (requestId === streamRequestIdRef.current) {
            setError(describeThrownMessage(streamError));
            optionsRef.current.onError?.(streamError);
          }
        }
      } finally {
        await finishStream(requestId, controller, streamFailure);
      }
    },
    [cancelFrame, finishStream, handleEvent],
  );

  const send = useCallback(
    (targetConversationId: number, request: AiMessageRequest): Promise<void> =>
      startStream(
        targetConversationId,
        (handlers, signal) => openRunStream(targetConversationId, request, handlers, signal),
        null,
        request.message,
      ),
    [startStream],
  );

  const attach = useCallback(
    (targetConversationId: number, targetRunId: number, after: number): Promise<void> =>
      startStream(
        targetConversationId,
        (handlers, signal) => attachRunStream(targetRunId, after, handlers, signal),
        targetRunId,
      ),
    [startStream],
  );

  const stop = useCallback(async (): Promise<void> => {
    const targetRunId = runIdRef.current;
    // No run id yet: the run row is admitted before `run_started` is emitted, so there is a brief
    // window with nothing to address. The button stays disabled until then (`canStop`).
    if (targetRunId === null) return;

    setStopRequested(true);
    try {
      // The response is the run row, which the stream reports authoritatively anyway; the point of
      // the call is the side effect on the server (SIGTERM to the CLI and its descendants).
      await stopRun(targetRunId);
      // Deliberately NOT aborting the fetch: the open stream is what delivers the terminal
      // `run_status` frame and `done`, and aborting would leave the button in `stopping` forever.
    } catch (stopError) {
      setStopRequested(false);
      setError(describeThrownMessage(stopError));
      optionsRef.current.onError?.(stopError);
    }
  }, []);

  const abort = useCallback(() => {
    generationRef.current += 1;
    streamRequestIdRef.current += 1;
    chatInFlightRef.current = false;
    cancelFrame();
    abortControllerRef.current?.abort();
    abortControllerRef.current = null;
    blocksRef.current = [];
    runIdRef.current = null;
    setRunId(null);
    setLastStatus(null);
    setIsStreaming(false);
    setStopRequested(false);
    setError('');
    setPendingUserMessage(null);
    bump();
  }, [cancelFrame]);

  // Kept in an effect declared before the invalidation one so a `send` triggered by a later effect
  // in the same commit already sees the new conversation.
  useEffect(() => {
    conversationIdRef.current = conversationId;
  }, [conversationId]);

  useEffect(() => {
    // Another conversation is on screen now: its transcript has nothing to do with the blocks the
    // previous stream was filling. The abort only closes our reader — the run keeps generating.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    abort();
  }, [abort, conversationId]);

  useEffect(
    () => () => {
      // Unmount: refs and timers only. Touching state here would warn about an update on an
      // unmounted component.
      generationRef.current += 1;
      streamRequestIdRef.current += 1;
      chatInFlightRef.current = false;
      if (frameRef.current !== null) cancelAnimationFrame(frameRef.current);
      frameRef.current = null;
      abortControllerRef.current?.abort();
      abortControllerRef.current = null;
    },
    [],
  );

  return {
    blocksRef,
    renderTick,
    isStreaming,
    stopRequested,
    canStop: isStreaming && !stopRequested && runId !== null,
    runId,
    lastStatus,
    error,
    liveTokensPerSecond,
    lastRunTokensPerSecond,
    pendingUserMessage,
    send,
    attach,
    stop,
    abort,
  };
}
