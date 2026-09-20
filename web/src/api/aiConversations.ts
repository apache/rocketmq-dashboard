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

import client from './client';
import type {
  AiAgentCapabilitiesVO,
  AiConversationListItemVO,
  AiConversationVO,
  AiRmqctlConfigVO,
  AiRunVO,
  AiTimelineVO,
  RunStatus,
} from './aiEvents';

/**
 * The conversation REST surface (`AiConversationController`).
 *
 * Every JSON endpoint answers with the shared `Result<T>` envelope, which `api/client.ts` already
 * unwraps and turns a non-success `code` into a rejected promise plus an antd toast — so the
 * functions below return the bare `data` payload like every other api module does.
 *
 * The two SSE endpoints (POST `/ai/conversations/{id}/messages` and GET `/ai/runs/{runId}/stream`)
 * are NOT here: axios cannot stream, so `api/ai.ts` opens them with `fetch` + `ReadableStream`.
 * Their path builders live in this module anyway, so the whole conversation URL surface is
 * discoverable in one place and the streaming client cannot drift from it.
 */

// ─── Envelopes and paths ────────────────────────────────────────

/** Mirrors `common.domain.PageResult`: the cursor-free, offset-based page the list endpoint returns. */
export interface PageResult<T> {
  items: T[];
  total: number;
  page: number;
  size: number;
}

/** The run still streaming, so a reload can re-attach instead of showing a dead transcript. */
export interface AiActiveRunRef {
  id: number;
  status: RunStatus;
}

/**
 * `AiConversationDetailVO` — the conversation plus its active run. The detail VO has no mirror in
 * `api/aiEvents.ts` (that module only carries the shapes the cross-language event fixture pins), so
 * it is declared next to the endpoint that returns it.
 */
export interface AiConversationDetailVO extends AiConversationVO {
  activeRun: AiActiveRunRef | null;
}

export const AI_CONVERSATIONS_PATH = '/ai/conversations';
export const AI_AGENT_CAPABILITIES_PATH = '/ai/agent-capabilities';
export const AI_RUNS_PATH = '/ai/runs';

/** Conversation ids are numeric (`bigint unsigned AUTO_INCREMENT`), so no escaping is needed. */
export function conversationPath(conversationId: number): string {
  return `${AI_CONVERSATIONS_PATH}/${conversationId}`;
}

/** GET — persisted timeline, cursor-paged on `seq`. */
export function conversationEventsPath(conversationId: number): string {
  return `${conversationPath(conversationId)}/events`;
}

/** POST (SSE) — send a message and stream the run it starts. */
export function conversationMessagesPath(conversationId: number): string {
  return `${conversationPath(conversationId)}/messages`;
}

/** GET — the paste-ready `{"mcpServers":{…}}` snippet for an external agent. */
export function conversationRmqctlConfigPath(conversationId: number): string {
  return `${conversationPath(conversationId)}/rmqctl-config`;
}

export function runPath(runId: number): string {
  return `${AI_RUNS_PATH}/${runId}`;
}

/** GET (SSE) — attach to a run that is already generating, replaying events after `after`. */
export function runStreamPath(runId: number): string {
  return `${runPath(runId)}/stream`;
}

/** POST — ask the server to stop a run; idempotent, and a stale run is a 409. */
export function runStopPath(runId: number): string {
  return `${runPath(runId)}/stop`;
}

/** POST — persist the generation speed the client measured while the run streamed. */
export function runSpeedPath(runId: number): string {
  return `${runPath(runId)}/speed`;
}

// ─── Request bodies ─────────────────────────────────────────────

/** `AiConversationCreateDTO`. Both fields are optional: a conversation can start unbound. */
export interface AiConversationCreateRequest {
  /** The RocketMQ instance the agent's tools are pinned to (`rmqctl --instance-id`). */
  instanceId?: string | null;
  /** `chat` by default; the backend rejects an unknown mode. */
  mode?: string;
}

/** `AiConversationUpdateDTO` — rename and/or archive. Omitted fields are left untouched. */
export interface AiConversationUpdateRequest {
  title?: string;
  archived?: boolean;
}

/**
 * `AiMessageDTO` — the body of the streaming POST. `model`/`engine`/`mode` are optional overrides
 * that the run row snapshots at admission time, so a later settings change never rewrites history.
 */
export interface AiMessageRequest {
  message: string;
  model?: string;
  engine?: string;
  mode?: string;
  /** Rewrite the prompt before handing it to the agent; streamed back as `source:'enhance'`. */
  enhance?: boolean;
  /** Resume the provider session (`claude --resume`). Defaults to the conversation's own state. */
  resume?: boolean;
}

/** Query of the list endpoint. `page` is 1-based, matching antd's pagination. */
export interface AiConversationQuery {
  page?: number;
  size?: number;
  /** Substring match on the title; the backend escapes the LIKE wildcards. */
  search?: string;
  archived?: boolean;
}

/** Query of the timeline endpoint: `seq > after`, at most `limit` rows (backend cap 500). */
export interface AiTimelineQuery {
  after?: number;
  limit?: number;
}

// ─── Endpoints ──────────────────────────────────────────────────

/** 1. POST `/api/ai/conversations` */
export async function createConversation(
  body: AiConversationCreateRequest = {},
): Promise<AiConversationVO> {
  const res = await client.post<{ data: AiConversationVO }>(AI_CONVERSATIONS_PATH, body);
  return res.data.data;
}

/** 2. GET `/api/ai/conversations` */
export async function listConversations(
  params: AiConversationQuery = {},
): Promise<PageResult<AiConversationListItemVO>> {
  const res = await client.get<{ data: PageResult<AiConversationListItemVO> }>(
    AI_CONVERSATIONS_PATH,
    { params },
  );
  return res.data.data;
}

/**
 * 3. GET `/api/ai/conversations/{id}`
 *
 * A conversation owned by somebody else is a 404, not a 403 — the owner filter doubles as an
 * anti-enumeration guard, so callers must not distinguish the two.
 */
export async function getConversation(conversationId: number): Promise<AiConversationDetailVO> {
  const res = await client.get<{ data: AiConversationDetailVO }>(conversationPath(conversationId));
  return res.data.data;
}

/** 4. PATCH `/api/ai/conversations/{id}` */
export async function updateConversation(
  conversationId: number,
  body: AiConversationUpdateRequest,
): Promise<AiConversationVO> {
  const res = await client.patch<{ data: AiConversationVO }>(
    conversationPath(conversationId),
    body,
  );
  return res.data.data;
}

/**
 * 5. DELETE `/api/ai/conversations/{id}` — hard delete (events, then runs, then the conversation)
 * plus the per-conversation agent workspace directory. `Result<Void>` carries no payload.
 */
export async function deleteConversation(conversationId: number): Promise<void> {
  await client.delete(conversationPath(conversationId));
}

/** 6. GET `/api/ai/conversations/{id}/events` */
export async function getConversationTimeline(
  conversationId: number,
  params: AiTimelineQuery = {},
): Promise<AiTimelineVO> {
  const res = await client.get<{ data: AiTimelineVO }>(conversationEventsPath(conversationId), {
    params,
  });
  return res.data.data;
}

/**
 * 9. POST `/api/ai/runs/{runId}/stop`
 *
 * Idempotent: stopping an already-terminal run is a 200 no-op. Stopping a run that is no longer the
 * conversation's active one is a **409** and must fail closed — the caller must never kill the newer
 * run, because the `runId` it holds may come from a turn ago and the one irreversible mistake here is
 * stopping the answer the user is currently watching.
 *
 * The 409 carries only the numeric status and the server's message: this is a JSON endpoint, and the
 * `Result` envelope has no string-code channel. `ai.run.stale_stop` exists server-side but is a
 * **log-only diagnostic** — do not branch on it, it never reaches a client. The stable string codes
 * (`ai.run.busy`, `ai.conversation.not_found`, `ai.request.invalid`, `ai.run.refused`) exist only on
 * the two SSE endpoints, where a refusal cannot be an HTTP status because the caller sent
 * `Accept: text/event-stream`; those arrive as an in-stream `event: error` frame instead.
 *
 * Stopping does NOT close the SSE stream. The stream stays open to deliver the terminal
 * `run_status` frame and `done`, which is exactly what lets the UI leave the "stopping" state.
 */
export async function stopRun(runId: number): Promise<AiRunVO> {
  const res = await client.post<{ data: AiRunVO }>(runStopPath(runId));
  return res.data.data;
}

/**
 * 9a. POST `/api/ai/runs/{runId}/speed` — persist the client-measured generation speed so a
 * replayed transcript shows the same number. Fire-and-forget by design: a failed report only
 * loses the persisted speed, never the answer itself.
 */
export async function reportRunSpeed(runId: number, tokensPerSecond: number): Promise<void> {
  await client.post(runSpeedPath(runId), { tokensPerSecond });
}

/** 10. GET `/api/ai/agent-capabilities` */
export async function getAgentCapabilities(): Promise<AiAgentCapabilitiesVO> {
  const res = await client.get<{ data: AiAgentCapabilitiesVO }>(AI_AGENT_CAPABILITIES_PATH);
  return res.data.data;
}

/** 11. GET `/api/ai/conversations/{id}/rmqctl-config` */
export async function getRmqctlConfig(conversationId: number): Promise<AiRmqctlConfigVO> {
  const res = await client.get<{ data: AiRmqctlConfigVO }>(
    conversationRmqctlConfigPath(conversationId),
  );
  return res.data.data;
}
