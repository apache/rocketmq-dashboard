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

export type ChatMode = 'chat' | 'diagnose' | 'manage' | 'query';

const CHAT_MODES = new Set<ChatMode>(['chat', 'diagnose', 'manage', 'query']);

export interface ChatDraft {
  prompt: string;
  model?: string;
  mode?: ChatMode;
  enhance?: boolean;
  newConversation?: boolean;
  conversationId?: string;
}

export function getChatDraft(state: unknown): ChatDraft | null {
  if (typeof state !== 'object' || state === null) return null;
  const candidate = state as Record<string, unknown>;
  const prompt = typeof candidate.prompt === 'string' ? candidate.prompt.trim() : '';
  const conversationId =
    typeof candidate.conversationId === 'string' && candidate.conversationId.trim()
      ? candidate.conversationId
      : undefined;
  if (!prompt && !conversationId) return null;
  const model = typeof candidate.model === 'string' ? candidate.model.trim() : '';
  const mode =
    typeof candidate.mode === 'string' && CHAT_MODES.has(candidate.mode as ChatMode)
      ? (candidate.mode as ChatMode)
      : undefined;

  return {
    prompt,
    ...(model ? { model } : {}),
    ...(mode ? { mode } : {}),
    ...(candidate.enhance === true ? { enhance: true } : {}),
    ...(candidate.newConversation === true ? { newConversation: true } : {}),
    ...(conversationId ? { conversationId } : {}),
  };
}
