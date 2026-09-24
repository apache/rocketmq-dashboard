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

/**
 * Mirror of the cross-language AI event contract.
 *
 * Source of truth: `server/src/test/resources/ai/ai-event-contract.json`. The Java
 * `AiEventContractTest` and `src/api/aiEvents.contract.test.ts` both read that file; adding,
 * renaming or dropping an event type or field has to be reflected there and on both sides.
 *
 * Three vocabularies exist on purpose:
 * - {@link ChatSseEvent} — the ephemeral, delta-oriented payload pushed over SSE.
 * - {@link TimelineEvent} — the coalesced payload persisted into `rmq_ai_event.payload`.
 * - the provider-neutral Java `AgentEvent` SPI, which is never serialised and has no mirror here.
 *
 * Live and persisted events reduce into the SAME render target (`pages/ai/render/blocks.ts`), so
 * the UI never knows whether it is watching a live stream or replaying history. Name shifts
 * between the two vocabularies are deliberate: `text_delta` -> `text`, `tool_start` -> `tool_use`,
 * `tool_done` -> `tool_result`. `run_started`/`run_finished` are live-only (the `rmq_ai_run` row is
 * the truth), but a terminal `run_status` IS persisted so a reloaded conversation can render
 * "已停止" without joining the run table.
 *
 * This module is a pure contract mirror: type definitions and `as const` tuples, no logic.
 */

/**
 * Where a `thinking` event came from. `model` is real chain-of-thought reasoning; `enhance` is the
 * prompt-enhancement rewrite. They must never be coalesced into one block, otherwise the UI labels
 * a rewritten prompt as 思维链.
 */
export type ThinkingSource = 'model' | 'enhance';

/** `rmq_ai_run.status` — QUEUED/RUNNING are active, the rest are terminal. */
export type RunStatus = 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'STOPPED' | 'FAILED';

/** `rmq_ai_run.stop_reason` — why a run reached its terminal status. */
export type StopReason =
  | 'USER_STOP'
  | 'SHUTDOWN'
  | 'TIMEOUT'
  | 'OUTPUT_LIMIT'
  | 'PROVIDER_ERROR'
  | 'SERVER_RESTART'
  | 'OVERLOADED'
  | 'ORPHANED';

/**
 * Severity of an advisory (non-fatal) notice. Mirrors the Java `AgentEventProjector.LEVEL_INFO` /
 * `LEVEL_WARN` / `LEVEL_ERROR` constants — all three reach the wire, so all three must be typable
 * here: `ClaudeCodeStreamParser` emits `error`-level notices for a `result` frame that carries an
 * `errors` array and for a non-null `api_error_status`.
 *
 * `error` is a notice level rather than an `error` event because the run survives it. A failure that
 * ends the run arrives as an `error` event and renders through `ErrorBlock` (a semantic antd Alert);
 * a notice, at any level, renders through `NoticeBlock` (the neutral InfoBanner treatment).
 */
export type NoticeLevel = 'info' | 'warn' | 'error';

// ─── Live (SSE) events ──────────────────────────────────────────

/** Run-level metadata emitted once the run row is admitted; produces no render block. */
export interface LiveRunStartedEvent {
  type: 'run_started';
  runId: number;
  conversationId: number;
  title: string;
  turn: number;
}

/** A streamed fragment of assistant text. Deltas are coalesced server-side before persisting. */
export interface LiveTextDeltaEvent {
  type: 'text_delta';
  content: string;
}

/** A streamed fragment of reasoning or of the prompt-enhancement rewrite. */
export interface LiveThinkingEvent {
  type: 'thinking';
  content: string;
  source: ThinkingSource;
}

/** Fields shared by the live and the persisted "a tool call started" event. */
export interface ToolCallFields {
  /** Provider tool-call id; joins the start to its result. */
  tcId: string;
  tool: string;
  /**
   * Assembled tool arguments. Always a JSON object per the provider protocol — the backend emits
   * `{}` rather than null for a no-arg tool, which is what makes this type safe.
   */
  input: Record<string, unknown>;
}

/** Emitted when the tool arguments are complete, i.e. together with the persisted `tool_use`. */
export interface LiveToolStartEvent extends ToolCallFields {
  type: 'tool_start';
}

/** Fields shared by the live and the persisted "a tool call finished" event. */
export interface ToolResultFields {
  tcId: string;
  tool: string;
  /**
   * Tool output as an opaque transport string: what the tool returned, usually holding serialised
   * JSON but never promised to. Deliberately NOT structured like {@link ToolCallFields.input} —
   * input comes from the model, output comes from the tool. The backend has already stripped inline
   * base64 and capped the text at 32 KiB before it reaches the wire; {@link ToolResultFields.outputBytes}
   * keeps the real size and {@link ToolResultFields.truncated} says whether that cap bit.
   */
  output: string;
  outputBytes: number;
  truncated: boolean;
  durationMs: number;
  success: boolean;
  /** Present only when the call failed. */
  error?: string;
}

export interface LiveToolDoneEvent extends ToolResultFields {
  type: 'tool_done';
}

/** Fields shared by the live and the persisted notice event. */
export interface NoticeFields {
  level: NoticeLevel;
  message: string;
}

export interface LiveNoticeEvent extends NoticeFields {
  type: 'notice';
}

/** Fields shared by the live and the persisted error event. */
export interface ErrorFields {
  code: string;
  message: string;
  /**
   * Optional advice for the operator. The contract fixture carries it on both sides, but a
   * provider error frequently has none, so the mirror keeps it optional rather than asserting a
   * field that the backend legitimately omits.
   */
  hint?: string;
}

export interface LiveErrorEvent extends ErrorFields {
  type: 'error';
}

/** Terminal run frame. Live-only: the persisted counterpart is `run_status`. */
export interface LiveRunFinishedEvent {
  type: 'run_finished';
  runId: number;
  status: RunStatus;
  durationMs: number;
}

/** Everything the SSE endpoint may push, discriminated on `type`. */
export type ChatSseEvent =
  | LiveRunStartedEvent
  | LiveTextDeltaEvent
  | LiveThinkingEvent
  | LiveToolStartEvent
  | LiveToolDoneEvent
  | LiveNoticeEvent
  | LiveErrorEvent
  | LiveRunFinishedEvent;

export const LIVE_EVENT_TYPES = [
  'run_started',
  'text_delta',
  'thinking',
  'tool_start',
  'tool_done',
  'notice',
  'error',
  'run_finished',
] as const satisfies readonly ChatSseEvent['type'][];

export type LiveEventType = (typeof LIVE_EVENT_TYPES)[number];

/**
 * Compile-time half of the drift alarm: resolves to `never` when {@link LIVE_EVENT_TYPES} drops a
 * member of the union. The `satisfies` clause on the tuple catches the opposite direction (a tuple
 * entry that is not a union member).
 */
export type LiveEventTypesComplete =
  Exclude<ChatSseEvent['type'], LiveEventType> extends never ? true : never;

// ─── Persisted timeline events ──────────────────────────────────

/**
 * The user's message. `enhancedPrompt` is only present when prompt enhancement actually rewrote
 * the message, hence optional even though the contract example carries it.
 */
export interface TimelineUserEvent {
  type: 'user';
  text: string;
  enhancedPrompt?: string;
}

/**
 * Persisted reasoning. Uses `text` (the coalesced whole) where the live frame uses `content`
 * (a delta).
 */
export interface TimelineThinkingEvent {
  type: 'thinking';
  text: string;
  source: ThinkingSource;
}

/** Persisted assistant text: the coalesced result of the `text_delta` stream. */
export interface TimelineTextEvent {
  type: 'text';
  text: string;
}

export interface TimelineToolUseEvent extends ToolCallFields {
  type: 'tool_use';
}

export interface TimelineToolResultEvent extends ToolResultFields {
  type: 'tool_result';
}

export interface TimelineNoticeEvent extends NoticeFields {
  type: 'notice';
}

export interface TimelineErrorEvent extends ErrorFields {
  type: 'error';
}

/**
 * Terminal run status, persisted so history can show why a run ended. `reason` is optional: a
 * normally completed run has no stop reason (`rmq_ai_run.stop_reason` is NULL).
 */
export interface TimelineRunStatusEvent {
  type: 'run_status';
  status: RunStatus;
  reason?: StopReason;
}

/** Everything `rmq_ai_event.payload` may hold, discriminated on `type`. */
export type TimelineEvent =
  | TimelineUserEvent
  | TimelineThinkingEvent
  | TimelineTextEvent
  | TimelineToolUseEvent
  | TimelineToolResultEvent
  | TimelineNoticeEvent
  | TimelineErrorEvent
  | TimelineRunStatusEvent;

export const TIMELINE_EVENT_TYPES = [
  'user',
  'thinking',
  'text',
  'tool_use',
  'tool_result',
  'notice',
  'error',
  'run_status',
] as const satisfies readonly TimelineEvent['type'][];

export type TimelineEventType = (typeof TIMELINE_EVENT_TYPES)[number];

/** Compile-time half of the drift alarm for {@link TIMELINE_EVENT_TYPES}. */
export type TimelineEventTypesComplete =
  Exclude<TimelineEvent['type'], TimelineEventType> extends never ? true : never;

// ─── REST envelopes ─────────────────────────────────────────────

/**
 * One persisted event plus the envelope columns the history API returns per row.
 *
 * `runId` is required because `rmq_ai_event.run_id` is NOT NULL: the envelope must not be weaker
 * than the storage, and every event belongs to exactly one run.
 */
export interface TimelineItem {
  id: number;
  turn: number;
  seq: number;
  createdAt: string;
  event: TimelineEvent;
  /** The run that produced this event; joins the row to `rmq_ai_run.id`. */
  runId: number;
}

export interface AiConversationVO {
  id: number;
  title: string;
  owner: string;
  engine: string;
  model: string;
  mode: string;
  instanceId?: string | null;
  runtimeSessionId?: string | null;
  lastSeq: number;
  archived: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface AiConversationListItemVO {
  id: number;
  title: string;
  engine: string;
  model: string;
  mode: string;
  instanceId?: string | null;
  lastRunId?: number | null;
  lastRunStatus?: RunStatus | null;
  updatedAt: string;
  createdAt: string;
}

export interface AiRunVO {
  id: number;
  conversationId: number;
  turn: number;
  status: RunStatus;
  engine: string;
  model: string;
  startedAt?: string | null;
  finishedAt?: string | null;
  durationMs?: number | null;
  inputTokens?: number | null;
  outputTokens?: number | null;
  stopReason?: StopReason | null;
  errorCode?: string | null;
  errorMessage?: string | null;
}

export interface AiTimelineVO {
  items: TimelineItem[];
  /** Cursor for the next page, or null once the tail of the conversation has been reached. */
  nextAfter: number | null;
  /** The run still streaming, so a reload can re-attach instead of showing a dead transcript. */
  activeRun: { id: number; status: RunStatus } | null;
  /** Stats of the runs referenced by `items`; `tokensPerSecond` is null when never reported. */
  runs?: { id: number; tokensPerSecond: number | null }[];
}

export interface AiAgentCapabilitiesVO {
  rmqctlAvailable: boolean;
  claudeAvailable: boolean;
  qoderAvailable: boolean;
  mcpEnabled: boolean;
  l3ToolsAllowed: boolean;
}

export interface AiRmqctlConfigVO {
  snippet: string;
  instanceId: string;
  server: string;
}
