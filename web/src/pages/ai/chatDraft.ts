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

import type { AiMessageRequest } from '../../api/aiConversations';
import type { AgentEngine } from '../../stores/engineStore';

export type ChatMode = 'chat' | 'diagnose' | 'manage' | 'query';

const CHAT_MODES = new Set<ChatMode>(['chat', 'diagnose', 'manage', 'query']);
const AGENT_ENGINES = new Set<AgentEngine>(['claude-code', 'qoder', 'http']);

export interface ChatDraft {
  prompt: string;
  model?: string;
  engine?: AgentEngine;
  mode?: ChatMode;
  enhance?: boolean;
  /**
   * RocketMQ instance the created conversation is pinned to (`rmqctl --instance-id`). Optional:
   * an unbound conversation can still be created and answered without instance-scoped tools.
   */
  instanceId?: string;
}

export function getChatDraft(state: unknown): ChatDraft | null {
  if (typeof state !== 'object' || state === null) return null;
  const candidate = state as Record<string, unknown>;
  const prompt = typeof candidate.prompt === 'string' ? candidate.prompt.trim() : '';
  if (!prompt) return null;
  const model = typeof candidate.model === 'string' ? candidate.model.trim() : '';
  const instanceId = typeof candidate.instanceId === 'string' ? candidate.instanceId.trim() : '';
  const mode =
    typeof candidate.mode === 'string' && CHAT_MODES.has(candidate.mode as ChatMode)
      ? (candidate.mode as ChatMode)
      : undefined;
  const engine =
    typeof candidate.engine === 'string' && AGENT_ENGINES.has(candidate.engine as AgentEngine)
      ? (candidate.engine as AgentEngine)
      : undefined;

  return {
    prompt,
    ...(model ? { model } : {}),
    ...(engine ? { engine } : {}),
    ...(mode ? { mode } : {}),
    ...(candidate.enhance === true ? { enhance: true } : {}),
    ...(instanceId ? { instanceId } : {}),
  };
}

export function shouldOpenChatHistory(state: unknown): boolean {
  if (typeof state !== 'object' || state === null) return false;
  return (state as Record<string, unknown>).historyIntent === 'open';
}

export function shouldOpenTools(state: unknown): boolean {
  if (typeof state !== 'object' || state === null) return false;
  return (state as Record<string, unknown>).toolsIntent === 'open';
}

/**
 * The `/ai/c/:conversationId` route param (or any numeric id string) → a positive integer id;
 * anything else → null, which is the bare `/ai` route's "no conversation on screen yet".
 * Conversation ids are `bigint unsigned AUTO_INCREMENT`, so a non-positive or fractional value is
 * a hand-edited URL, not an id.
 */
export function parseConversationId(raw: string | undefined | null): number | null {
  if (!raw) return null;
  const parsed = Number(raw);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null;
}

/**
 * `AiMessageRequest` assembled from composer state. Empty optional overrides are OMITTED rather
 * than sent as empty strings: the run row snapshots the overrides it was actually admitted with,
 * and an empty `model` would snapshot a lie.
 */
export function buildMessageRequest(
  message: string,
  model: string,
  engine: string,
  mode: ChatMode,
  enhance?: boolean,
): AiMessageRequest {
  return {
    message,
    ...(model ? { model } : {}),
    engine,
    mode,
    ...(enhance ? { enhance: true } : {}),
  };
}

/** The auto-send request of a home-page draft; the engine selector is the engine fallback. */
export function draftToMessageRequest(
  draft: ChatDraft,
  fallbackEngine: AgentEngine,
): AiMessageRequest {
  return buildMessageRequest(
    draft.prompt,
    draft.model ?? '',
    draft.engine ?? fallbackEngine,
    draft.mode ?? 'chat',
    draft.enhance,
  );
}
