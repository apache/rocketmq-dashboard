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

import type {
  ChatSseEvent,
  ErrorFields,
  RunStatus,
  StopReason,
  ThinkingSource,
  TimelineEvent,
  TimelineItem,
  TimelineToolResultEvent,
  ToolResultFields,
} from '../../../api/aiEvents';
import type { Bubble, RenderBlock } from './blocks';
import {
  appendText,
  appendThinking,
  errorBlock,
  noticeBlock,
  runningToolBlock,
  withToolResult,
} from './blocks';

/**
 * Fold one persisted timeline event into render blocks — the REPLAY path.
 *
 * Pure: the input array is never mutated. Events that carry no renderable content return the same
 * reference: `user` (the caller renders user bubbles separately) and `run_status` (run-level
 * metadata, carried by {@link groupIntoBubbles} instead).
 *
 * The coalescing rules are exactly the ones `reduceLive.ts` applies, and both go through the same
 * primitives in `blocks.ts`. That is the whole point of splitting live from persisted: two wire
 * vocabularies, one render target.
 */
export function foldTimelineBlock(blocks: RenderBlock[], event: TimelineEvent): RenderBlock[] {
  switch (event.type) {
    case 'user':
    case 'run_status':
      return blocks;

    case 'thinking':
      return appendThinking(blocks, event.text, event.source);

    case 'text':
      return appendText(blocks, event.text);

    case 'tool_use':
      return [...blocks, runningToolBlock(event.tcId, event.tool, event.input)];

    case 'tool_result':
      return withToolResult(blocks, event);

    case 'notice':
      return [...blocks, noticeBlock(event)];

    case 'error':
      return [...blocks, errorBlock(event)];

    default: {
      // Exhaustiveness guard: a new persisted event type that nobody folds is a compile error.
      const _never: never = event;
      throw new Error(`Unhandled timeline event type: ${JSON.stringify(_never)}`);
    }
  }
}

/** Fold a whole persisted timeline into render blocks. */
export function foldTimelineBlocks(events: TimelineEvent[]): RenderBlock[] {
  return events.reduce<RenderBlock[]>((blocks, event) => foldTimelineBlock(blocks, event), []);
}

/**
 * Group persisted rows into transcript bubbles, using the `user` event as the boundary.
 *
 * - a `user` row closes the open run and becomes its own bubble. Its `enhancedPrompt` is not a
 *   render block (the block vocabulary has no slot for it); callers that want to show the rewrite
 *   read it straight off the event.
 * - everything else folds into the open assistant bubble, creating one when history starts mid-run
 *   (a paging window that opens after the `user` row).
 * - `run_status` stamps the terminal status onto the open bubble and closes it, so a following run
 *   becomes a new bubble. The bubble is created even when the run rendered nothing, which is how a
 *   reloaded conversation shows 已停止 for a run that was stopped before it emitted a block.
 *
 * `turn` and `createdAt` come from the row that opened the bubble and are never overwritten.
 *
 * `runSpeeds` maps a run id to the generation speed the client reported for it; the value is
 * stamped onto the assistant bubble the run produced, so a replayed transcript shows the same
 * number the user watched live.
 */
export function groupIntoBubbles(
  items: TimelineItem[],
  runSpeeds?: ReadonlyMap<number, number>,
): Bubble[] {
  const bubbles: Bubble[] = [];
  let open: Bubble | null = null;

  for (const item of items) {
    const event = item.event;

    if (event.type === 'user') {
      open = null;
      bubbles.push({
        role: 'user',
        blocks: [{ kind: 'text', text: event.text }],
        turn: item.turn,
        createdAt: item.createdAt,
      });
      continue;
    }

    if (!open) {
      open = { role: 'assistant', blocks: [], turn: item.turn, createdAt: item.createdAt };
      const speed = runSpeeds?.get(item.runId);
      if (speed !== undefined) open.tokensPerSecond = speed;
      bubbles.push(open);
    }

    if (event.type === 'run_status') {
      open.runStatus = event.status;
      open = null;
      continue;
    }

    open.blocks = foldTimelineBlock(open.blocks, event);
  }

  return bubbles;
}

/**
 * Translate ONE live frame into its persisted form, for reconnect replay.
 *
 * `null` means "this frame has no per-event persisted counterpart", and each case is deliberate:
 * - `run_started` -> null: run-level metadata. The `rmq_ai_run` row is the truth; nothing about it
 *   belongs in the event timeline.
 * - `text_delta` -> null: the server coalesces a run of deltas into a single persisted `text`
 *   event, so no individual delta maps to a row. Use {@link coalesceLiveToTimeline} when the whole
 *   sequence is available.
 * - `tool_start` -> null: the persisted `tool_use` is written when the tool input is complete, and
 *   a single frame cannot say whether it is. Same remedy: {@link coalesceLiveToTimeline}.
 * - `run_finished` -> `run_status`, lossily: the live frame has no stop reason, so `reason` stays
 *   unset. Rendering does not care (`run_status` produces no block); the run row keeps the reason.
 */
export function liveToTimeline(event: ChatSseEvent): TimelineEvent | null {
  switch (event.type) {
    case 'run_started':
    case 'text_delta':
    case 'tool_start':
      return null;

    case 'thinking':
      return { type: 'thinking', text: event.content, source: event.source };

    case 'tool_done':
      return persistedToolResult(event);

    case 'notice':
      return { type: 'notice', level: event.level, message: event.message };

    case 'error':
      return persistedError(event);

    case 'run_finished':
      return { type: 'run_status', status: event.status };

    default: {
      const _never: never = event;
      throw new Error(`Unhandled live event type: ${JSON.stringify(_never)}`);
    }
  }
}

/** Buffer size the server flushes at when coalescing deltas into one persisted event. */
const DEFAULT_COALESCE_MAX_CHARS = 2048;

export interface CoalesceOptions {
  /**
   * Flush threshold in characters for the text/thinking buffers. Mirrors the server's buffered
   * write; the server also flushes on a time window, which a pure function cannot model. Splitting
   * never changes the rendered result because {@link foldTimelineBlock} re-coalesces adjacent
   * events of the same kind.
   */
  maxChars?: number;
  /**
   * `stop_reason` for the terminal `run_status`. Only the server knows why a run ended — the live
   * `run_finished` frame carries just the status — so the caller supplies it when it has it.
   */
  stopReason?: StopReason;
}

type PendingText = { kind: 'text'; parts: string[]; length: number };
type PendingThinking = {
  kind: 'thinking';
  source: ThinkingSource;
  parts: string[];
  length: number;
};
type PendingBuffer = PendingText | PendingThinking;

/**
 * Model the server's buffered write over a whole live sequence: what a run of SSE frames persists
 * as. This is the sequence-level counterpart of {@link liveToTimeline} and the primitive the
 * replay/live equivalence property is stated with — a per-event map cannot express coalescing.
 *
 * - consecutive `text_delta` -> one `text`
 * - consecutive same-source `thinking` -> one `thinking`; a source change always splits
 * - `tool_start` -> `tool_use`, `tool_done` -> `tool_result`, sharing the tcId
 * - `notice` / `error` -> their persisted twin
 * - `run_finished` -> terminal `run_status`
 * - `run_started` -> dropped
 *
 * Stage 3b's `AiEventSink` must apply the same rules; this function is the executable statement of
 * them.
 */
export function coalesceLiveToTimeline(
  events: ChatSseEvent[],
  options: CoalesceOptions = {},
): TimelineEvent[] {
  const maxChars = options.maxChars ?? DEFAULT_COALESCE_MAX_CHARS;
  const persisted: TimelineEvent[] = [];
  let pending: PendingBuffer | null = null;

  const flush = (): void => {
    if (!pending) return;
    const text = pending.parts.join('');
    persisted.push(
      pending.kind === 'text'
        ? { type: 'text', text }
        : { type: 'thinking', text, source: pending.source },
    );
    pending = null;
  };

  const buffered = (buffer: PendingBuffer, text: string): boolean => {
    buffer.parts.push(text);
    buffer.length += text.length;
    return buffer.length >= maxChars;
  };

  for (const event of events) {
    switch (event.type) {
      case 'run_started':
        flush();
        break;

      case 'text_delta': {
        if (pending && pending.kind !== 'text') flush();
        if (!pending) pending = { kind: 'text', parts: [], length: 0 };
        if (buffered(pending, event.content)) flush();
        break;
      }

      case 'thinking': {
        if (pending && (pending.kind !== 'thinking' || pending.source !== event.source)) flush();
        if (!pending) pending = { kind: 'thinking', source: event.source, parts: [], length: 0 };
        if (buffered(pending, event.content)) flush();
        break;
      }

      case 'tool_start':
        flush();
        persisted.push({
          type: 'tool_use',
          tcId: event.tcId,
          tool: event.tool,
          input: event.input,
        });
        break;

      case 'tool_done':
        flush();
        persisted.push(persistedToolResult(event));
        break;

      case 'notice':
        flush();
        persisted.push({ type: 'notice', level: event.level, message: event.message });
        break;

      case 'error':
        flush();
        persisted.push(persistedError(event));
        break;

      case 'run_finished':
        flush();
        persisted.push(terminalRunStatus(event.status, options.stopReason));
        break;

      default: {
        const _never: never = event;
        throw new Error(`Unhandled live event type: ${JSON.stringify(_never)}`);
      }
    }
  }

  flush();
  return persisted;
}

function terminalRunStatus(status: RunStatus, reason?: StopReason): TimelineEvent {
  return reason === undefined
    ? { type: 'run_status', status }
    : { type: 'run_status', status, reason };
}

/** `tool_done` and `tool_result` carry identical fields; only the type tag differs. */
function persistedToolResult(result: ToolResultFields): TimelineToolResultEvent {
  const event: TimelineToolResultEvent = {
    type: 'tool_result',
    tcId: result.tcId,
    tool: result.tool,
    output: result.output,
    outputBytes: result.outputBytes,
    truncated: result.truncated,
    durationMs: result.durationMs,
    success: result.success,
  };
  if (result.error !== undefined) event.error = result.error;
  return event;
}

/** Only set `hint` when it was sent, so the persisted twin stays deep-equal to the live frame. */
function persistedError(fields: ErrorFields): TimelineEvent {
  const event: TimelineEvent = { type: 'error', code: fields.code, message: fields.message };
  if (fields.hint !== undefined) event.hint = fields.hint;
  return event;
}
