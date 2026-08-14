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

const MAX_SSE_EVENT_CHARS = 1024 * 1024;

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

export interface AiExecuteRequest {
  message: string;
  mode: string;
  model: string;
  engine?: string;
  tools?: string[];
}

export interface AiChatRequest {
  message: string;
  mode: string;
  model: string;
  engine?: string;
  enhance?: boolean;
  conversationId?: string;
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

function emitEvent(
  event: string,
  onChunk: (text: string) => void,
  onEnhance?: (prompt: string) => void,
): boolean {
  const payload = getEventData(event);
  if (payload === null) return false;
  if (payload === '[DONE]') return true;
  if (getEventName(event) === 'error') {
    throw parseStreamError(payload);
  }
  if (getEventName(event) === 'enhance') {
    try {
      const parsed = JSON.parse(payload) as { delta?: unknown; prompt?: unknown };
      const delta =
        typeof parsed.delta === 'string'
          ? parsed.delta
          : typeof parsed.prompt === 'string'
            ? parsed.prompt
            : null;
      if (delta !== null) onEnhance?.(delta);
    } catch {
      onEnhance?.(payload);
    }
    return false;
  }

  try {
    const parsed = JSON.parse(payload) as AiStreamPayload;
    const text =
      typeof parsed.content === 'string'
        ? parsed.content
        : typeof parsed.text === 'string'
          ? parsed.text
          : null;
    if (text !== null) onChunk(text);
  } catch {
    onChunk(payload);
  }

  return false;
}

// ─── AI ─────────────────────────────────────────────────────────
export async function chatStream(
  data: AiChatRequest,
  onChunk: (text: string) => void,
  signal?: AbortSignal,
  onEnhance?: (prompt: string) => void,
) {
  const response = await fetch('/api/ai/chat', {
    method: 'POST',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(data),
    signal,
  });

  if (!response.ok || !response.body) {
    throw new Error(`AI chat failed: ${response.statusText}`);
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';

  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;

      buffer += decoder.decode(value, { stream: true });
      if (buffer.length > MAX_SSE_EVENT_CHARS && !getEventBoundary(buffer)) {
        throw new AiStreamError('AI stream event exceeds 1 MiB', 'llm.stream.event_too_large');
      }
      let boundary = getEventBoundary(buffer);
      while (boundary) {
        const event = buffer.slice(0, boundary.index);
        buffer = buffer.slice(boundary.index + boundary.length);
        if (event.length > MAX_SSE_EVENT_CHARS) {
          throw new AiStreamError('AI stream event exceeds 1 MiB', 'llm.stream.event_too_large');
        }
        if (emitEvent(event, onChunk, onEnhance)) return;
        boundary = getEventBoundary(buffer);
      }
    }

    buffer += decoder.decode();
    if (buffer.length > MAX_SSE_EVENT_CHARS) {
      throw new AiStreamError('AI stream event exceeds 1 MiB', 'llm.stream.event_too_large');
    }
    if (buffer && emitEvent(buffer, onChunk, onEnhance)) return;
  } finally {
    await reader.cancel().catch(() => undefined);
  }
}

export async function executeAiCommand(data: AiExecuteRequest) {
  const res = await client.post<{ data: { result: string; toolCalls: unknown[] } }>(
    '/ai/execute',
    data,
  );
  return res.data.data;
}

export async function listTools(cluster?: string) {
  const res = await client.get<{ data: McpTool[] }>('/ai/tools', {
    params: cluster ? { cluster } : undefined,
  });
  return res.data.data;
}

export async function executeTool(name: string, input: Record<string, unknown>) {
  const res = await client.post<{ data: unknown }>(
    `/ai/tools/${encodeURIComponent(name)}/execute`,
    input,
  );
  return res.data.data;
}
