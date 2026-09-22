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

import { API_BASE_URL } from '../config';
import client, { handleSessionUnauthorized } from './client';
import { conversationMessagesPath, runStreamPath, type AiMessageRequest } from './aiConversations';
import { LIVE_EVENT_TYPES, type ChatSseEvent } from './aiEvents';

/**
 * Upper bound for a single SSE frame. A tool result is capped at 32 KiB server-side, so 1 MiB is
 * already generous; the bound exists to stop a body that never terminates a frame from growing the
 * buffer without limit.
 */
const MAX_SSE_EVENT_CHARS = 1024 * 1024;

/** The server writes a `comment("hb")` heartbeat this often (`AgentStreamSession`). */
const SERVER_HEARTBEAT_INTERVAL_MS = 15_000;

/**
 * Client-side idle timeout: twice the server heartbeat, so one lost or late heartbeat is not fatal
 * but a buffered/half-open connection is. Derived rather than hard-coded so the relation to the
 * heartbeat stays executable.
 */
const RUN_STREAM_IDLE_TIMEOUT_MS = SERVER_HEARTBEAT_INTERVAL_MS * 2;

const EVENT_STREAM_CONTENT_TYPE = 'text/event-stream';

/** SSE event names of the run stream. `agent` carries every domain frame, discriminated on `type`. */
const AGENT_EVENT_NAME = 'agent';
const ERROR_EVENT_NAME = 'error';
/** Terminal control frame. Replaces the old `data: [DONE]` sentinel, which is gone. */
const DONE_EVENT_NAME = 'done';

const LIVE_EVENT_TYPE_SET: ReadonlySet<string> = new Set<string>(LIVE_EVENT_TYPES);

// ─── Types ──────────────────────────────────────────────────────
export interface McpTool {
  name: string;
  description: string;
  parameters: Record<string, unknown>;
  riskLevel?: string;
  permission?: string;
  requiredCapabilities?: string[];
  outputSchema?: Record<string, unknown>;
  viewHint?: string;
  deprecated?: boolean;
  replacement?: string;
}

/**
 * Callbacks of a run stream.
 *
 * One `onEvent` rather than a callback per event type on purpose: the frames form a sealed,
 * exhaustively-switched union (`ChatSseEvent`), and the reducers in `pages/ai/render/` already
 * dispatch on `event.type`. Fanning that out here would duplicate the switch and silently drop a
 * frame type added later.
 */
export interface RunStreamHandlers {
  /**
   * Every `event: agent` frame in arrival order, including the run-level ones (`run_started`,
   * `run_finished`) that render nothing. The payload has been validated against `LIVE_EVENT_TYPES`
   * before it reaches this callback.
   */
  onEvent: (event: ChatSseEvent) => void;
}

interface AiStreamPayload {
  content?: unknown;
  text?: unknown;
  code?: unknown;
  message?: unknown;
  hint?: unknown;
  status?: unknown;
}

export class AiStreamError extends Error {
  code?: string;
  hint?: string;
  status?: number;

  constructor(message: string, code?: string, hint?: string, status?: number) {
    super(message);
    this.name = 'AiStreamError';
    this.code = code;
    this.hint = hint;
    this.status = status;
  }
}

function getEventBoundary(buffer: string): { index: number; length: number } | null {
  const match = /\r\n\r\n|\n\n|\r\r/.exec(buffer);
  return match ? { index: match.index, length: match[0].length } : null;
}

function getEventData(event: string): string | null {
  const dataLines = event
    .split(/\r\n|\r|\n/)
    .filter((line) => line.startsWith('data:'))
    .map((line) => {
      const value = line.slice(5);
      return value.startsWith(' ') ? value.slice(1) : value;
    });

  return dataLines.length ? dataLines.join('\n') : null;
}

function getEventName(event: string): string {
  const eventLine = event.split(/\r\n|\r|\n/).find((line) => line.startsWith('event:'));
  if (!eventLine) return 'message';
  const value = eventLine.slice(6);
  return value.startsWith(' ') ? value.slice(1) : value;
}

/** Keep a malformed payload readable in an error message instead of dumping a megabyte of it. */
function describePayload(payload: string): string {
  return payload.length > 200 ? `${payload.slice(0, 200)}…` : payload;
}

function parseStreamError(payload: string): AiStreamError {
  try {
    const parsed = JSON.parse(payload) as AiStreamPayload;
    const message = typeof parsed.message === 'string' ? parsed.message : payload;
    const code = typeof parsed.code === 'string' ? parsed.code : undefined;
    const hint = typeof parsed.hint === 'string' ? parsed.hint : undefined;
    const status = typeof parsed.status === 'number' ? parsed.status : undefined;
    return new AiStreamError(message, code, hint, status);
  } catch {
    return new AiStreamError(payload);
  }
}

async function parseHttpError(
  response: Response,
  label = 'AI chat failed',
): Promise<AiStreamError> {
  const fallback = response.statusText || `HTTP ${response.status}`;
  try {
    const payload = await response.text();
    if (!payload.trim()) {
      return new AiStreamError(`${label}: ${fallback}`, undefined, undefined, response.status);
    }
    try {
      const parsed = JSON.parse(payload) as AiStreamPayload;
      const message = typeof parsed.message === 'string' ? parsed.message : payload;
      const code = typeof parsed.code === 'string' ? parsed.code : undefined;
      const hint = typeof parsed.hint === 'string' ? parsed.hint : undefined;
      const status = typeof parsed.status === 'number' ? parsed.status : response.status;
      return new AiStreamError(message, code, hint, status);
    } catch {
      return new AiStreamError(payload, undefined, undefined, response.status);
    }
  } catch {
    return new AiStreamError(`${label}: ${fallback}`, undefined, undefined, response.status);
  }
}

function eventTooLargeError(): AiStreamError {
  return new AiStreamError('AI stream event exceeds 1 MiB', 'llm.stream.event_too_large');
}

/**
 * Reject a response that is not an event stream.
 *
 * A buffering gateway (nginx without `X-Accel-Buffering: no` passed through, an ALB, a corporate
 * proxy) answers 200 with `text/html` or `application/json` and then hands the browser a body that
 * yields no frame for a long time — the UI spins forever and the console says nothing. This is the
 * most common production failure of an SSE feature and it is invisible without this guard, so it
 * fails loudly with the content type in the message.
 */
function assertEventStream(response: Response): void {
  const contentType = response.headers.get('content-type') ?? '';
  if (contentType.toLowerCase().includes(EVENT_STREAM_CONTENT_TYPE)) return;
  throw new AiStreamError(
    `AI stream rejected: expected ${EVENT_STREAM_CONTENT_TYPE} but got ${
      contentType.trim() ? contentType : '(no content-type)'
    }`,
    'llm.stream.unexpected_content_type',
    'A reverse proxy is buffering or rewriting the response. Disable proxy buffering for /api/ai/ and let X-Accel-Buffering: no through.',
    response.status,
  );
}

/**
 * One `reader.read()` bounded by the idle watchdog.
 *
 * The timer is per read, so any byte at all — a domain frame or just the server's `:hb` heartbeat —
 * resets it. A stream that goes quiet for {@link RUN_STREAM_IDLE_TIMEOUT_MS} is dead, not slow.
 */
async function readChunk(
  reader: ReadableStreamDefaultReader<Uint8Array>,
  idleTimeoutMs?: number,
): Promise<ReadableStreamReadResult<Uint8Array>> {
  const read = reader.read();
  if (idleTimeoutMs === undefined) return read;

  let timer: number | undefined;
  const idle = new Promise<never>((_resolve, reject) => {
    timer = window.setTimeout(() => {
      reject(
        new AiStreamError(
          `AI stream idle for more than ${Math.round(idleTimeoutMs / 1000)}s`,
          'llm.stream.idle_timeout',
          `The server heartbeats every ${Math.round(
            SERVER_HEARTBEAT_INTERVAL_MS / 1000,
          )}s; nothing arrived, so the connection is being buffered or was dropped.`,
        ),
      );
    }, idleTimeoutMs);
  });
  // The pending read is cancelled by the caller's finally block once the watchdog wins the race;
  // swallowing that rejection here keeps it from surfacing as an unhandled promise rejection.
  read.catch(() => undefined);

  try {
    return await Promise.race([read, idle]);
  } finally {
    if (timer !== undefined) window.clearTimeout(timer);
  }
}

/**
 * The hand-rolled SSE reader both the legacy chat stream and the run streams share: split frames on
 * a blank line, bound each frame, and let `onFrame` decide what a frame means and when to stop.
 *
 * Hand-rolled rather than `EventSource` because `EventSource` cannot POST, cannot send a JSON body
 * and cannot carry the session cookie cross-origin the way `fetch(..., {credentials:'include'})`
 * does.
 */
async function consumeEventStream(
  response: Response,
  onFrame: (frame: string) => boolean,
  idleTimeoutMs?: number,
): Promise<void> {
  const body = response.body;
  if (!body) {
    throw new AiStreamError(
      `AI stream failed: ${response.statusText || 'no response body'}`,
      'llm.stream.no_body',
      undefined,
      response.status,
    );
  }

  const reader = body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';

  const dispatch = (frame: string): boolean => {
    if (frame.length > MAX_SSE_EVENT_CHARS) throw eventTooLargeError();
    return onFrame(frame);
  };

  try {
    for (;;) {
      const { done, value } = await readChunk(reader, idleTimeoutMs);
      if (done) break;

      buffer += decoder.decode(value, { stream: true });
      if (buffer.length > MAX_SSE_EVENT_CHARS && !getEventBoundary(buffer)) {
        throw eventTooLargeError();
      }
      let boundary = getEventBoundary(buffer);
      while (boundary) {
        const frame = buffer.slice(0, boundary.index);
        buffer = buffer.slice(boundary.index + boundary.length);
        if (dispatch(frame)) return;
        boundary = getEventBoundary(buffer);
      }
    }

    // A server that closes without a trailing blank line still delivered its last frame.
    buffer += decoder.decode();
    if (buffer && dispatch(buffer)) return;
  } finally {
    await reader.cancel().catch(() => undefined);
  }
}

/**
 * Dispatch one frame of the RUN stream wire format:
 * `event: agent` (a `ChatSseEvent`, discriminated on `type`), `event: error` (throw) and
 * `event: done` (terminate). Comment frames — the `:hb` heartbeat — carry no data and are ignored.
 *
 * Unknown event names and unknown `type` values throw instead of being dropped: a client and server
 * that disagree on the contract must fail visibly rather than render a silently truncated answer.
 */
function emitRunFrame(frame: string, handlers: RunStreamHandlers): boolean {
  const name = getEventName(frame);
  if (name === DONE_EVENT_NAME) return true;

  const payload = getEventData(frame);
  if (payload === null) return false;

  if (name === ERROR_EVENT_NAME) throw parseStreamError(payload);
  if (name !== AGENT_EVENT_NAME) {
    throw new AiStreamError(
      `Unknown AI stream event: ${name}`,
      'llm.stream.unknown_event',
      'Expected one of: agent, error, done.',
    );
  }

  let parsed: unknown;
  try {
    parsed = JSON.parse(payload);
  } catch {
    throw new AiStreamError(
      `Malformed AI stream event: ${describePayload(payload)}`,
      'llm.stream.malformed_event',
    );
  }

  const event = parsed as ChatSseEvent;
  const type = (event as { type?: unknown }).type;
  if (typeof type !== 'string' || !LIVE_EVENT_TYPE_SET.has(type)) {
    throw new AiStreamError(
      `Unknown AI stream event type: ${String(type)}`,
      'llm.stream.unknown_event_type',
      'Client and server disagree on the AI event contract; reload the page, and if it persists redeploy the web bundle.',
    );
  }

  handlers.onEvent(event);
  return false;
}

async function consumeRunStream(
  response: Response,
  handlers: RunStreamHandlers,
  label: string,
): Promise<void> {
  if (response.status === 401) {
    handleSessionUnauthorized();
  }
  // A non-2xx body is a JSON `Result` envelope, not an event stream, so it is parsed before the
  // content-type guard gets a chance to complain about it.
  if (!response.ok) {
    throw await parseHttpError(response, label);
  }
  assertEventStream(response);
  await consumeEventStream(
    response,
    (frame) => emitRunFrame(frame, handlers),
    RUN_STREAM_IDLE_TIMEOUT_MS,
  );
}

// ─── Run streams ────────────────────────────────────────────────

/**
 * POST `/api/ai/conversations/{id}/messages` — send a user message and stream the run it starts.
 *
 * Replaces the old `POST /api/ai/chat`. The run outlives this request: closing the stream (or the
 * tab) does not cancel it, and {@link attachRunStream} picks it back up.
 *
 * `signal` is for navigation away only. Stopping a run goes through
 * `POST /api/ai/runs/{runId}/stop` and then keeps reading, because aborting here would mean never
 * receiving the terminal `run_status` frame and `done`.
 */
export async function openRunStream(
  conversationId: number,
  body: AiMessageRequest,
  handlers: RunStreamHandlers,
  signal?: AbortSignal,
): Promise<void> {
  const response = await fetch(`${API_BASE_URL}${conversationMessagesPath(conversationId)}`, {
    method: 'POST',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      Accept: EVENT_STREAM_CONTENT_TYPE,
    },
    body: JSON.stringify(body),
    signal,
  });

  await consumeRunStream(response, handlers, 'AI message stream failed');
}

/**
 * GET `/api/ai/runs/{runId}/stream?after={seq}` — attach to a run that is already generating.
 *
 * The server replays the persisted events with `seq > after`, then tails the live ones, dropping
 * anything at or below the seq it already replayed. `after: 0` therefore replays the whole run, and
 * a caller that has just loaded the timeline passes the highest seq it holds.
 */
export async function attachRunStream(
  runId: number,
  after: number,
  handlers: RunStreamHandlers,
  signal?: AbortSignal,
): Promise<void> {
  const query = new URLSearchParams({ after: String(after) });
  const response = await fetch(`${API_BASE_URL}${runStreamPath(runId)}?${query.toString()}`, {
    method: 'GET',
    credentials: 'include',
    headers: {
      Accept: EVENT_STREAM_CONTENT_TYPE,
    },
    signal,
  });

  await consumeRunStream(response, handlers, 'AI run stream failed');
}

export async function listTools(instanceId?: string) {
  const res = await client.get<{ data: McpTool[] }>('/ai/tools', {
    params: instanceId ? { instanceId } : undefined,
  });
  return res.data.data;
}

export async function executeTool(
  name: string,
  input: Record<string, unknown>,
  instanceId: string,
) {
  const res = await client.post<{ data: unknown }>(
    `/ai/tools/${encodeURIComponent(name)}/execute`,
    input,
    { params: { instanceId } },
  );
  return res.data.data;
}
