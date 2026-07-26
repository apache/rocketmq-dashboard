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
import { API_BASE_URL } from '../config';
import { TOKEN_STORAGE_KEY } from '../stores/authStorage';

// ─── Types ──────────────────────────────────────────────────────
export interface McpTool {
  name: string;
  description: string;
  parameters: Record<string, unknown>;
}

export interface AiExecuteRequest {
  command: string;
  mode?: string;
  model?: string;
  conversationId?: string;
  prompt?: string;
  context?: Record<string, unknown>;
}

export interface AiExecuteResult {
  success: boolean;
  result: string;
}

export interface AiChatRequest {
  message: string;
  mode: string;
  model: string;
  conversationId?: string;
}

interface AiStreamPayload {
  content?: unknown;
  text?: unknown;
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

function emitEvent(event: string, onChunk: (text: string) => void): boolean {
  const payload = getEventData(event);
  if (payload === null) return false;
  if (payload === '[DONE]') return true;

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

async function getResponseError(response: Response): Promise<string> {
  try {
    const payload = (await response.json()) as { message?: unknown };
    if (typeof payload.message === 'string' && payload.message.trim()) {
      return payload.message;
    }
  } catch {
    // Fall back to the HTTP status when the gateway does not return JSON.
  }

  return response.statusText || `HTTP ${response.status}`;
}

// ─── AI ─────────────────────────────────────────────────────────
export async function chatStream(
  data: AiChatRequest,
  onChunk: (text: string) => void,
  signal?: AbortSignal,
) {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
  };
  const token = localStorage.getItem(TOKEN_STORAGE_KEY);
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  const response = await fetch(`${API_BASE_URL}/ai/chat`, {
    method: 'POST',
    headers,
    body: JSON.stringify(data),
    signal,
  });

  if (!response.ok) {
    throw new Error(`AI chat failed: ${await getResponseError(response)}`);
  }
  if (!response.body) {
    throw new Error('AI chat failed: empty response body');
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;

    buffer += decoder.decode(value, { stream: true });
    let boundary = getEventBoundary(buffer);
    while (boundary) {
      const event = buffer.slice(0, boundary.index);
      buffer = buffer.slice(boundary.index + boundary.length);
      if (emitEvent(event, onChunk)) return;
      boundary = getEventBoundary(buffer);
    }
  }

  buffer += decoder.decode();
  if (buffer && emitEvent(buffer, onChunk)) return;
}

export async function executeAiCommand(data: AiExecuteRequest) {
  const res = await client.post<{ data: AiExecuteResult }>('/ai/execute', data);
  return res.data.data;
}

export async function listTools() {
  const res = await client.get<{ data: McpTool[] }>('/ai/tools');
  return res.data.data;
}
