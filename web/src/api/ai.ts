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

// ─── Types ──────────────────────────────────────────────────────
export interface McpTool {
  name: string;
  description: string;
  parameters: Record<string, unknown>;
}

export interface AiExecuteRequest {
  message: string;
  mode: string;
  model: string;
  tools?: string[];
}

// ─── AI API ─────────────────────────────────────────────────────
// Backend: SkillRegistryController at /api/skill
// Note: The AI/LLM endpoints are still in development.
// These endpoints are kept for mock compatibility.

export async function chatStream(
  data: AiExecuteRequest,
  onChunk: (text: string) => void,
  signal?: AbortSignal,
) {
  const response = await fetch('/api/ai/chat', {
    method: 'POST',
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
  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    const chunk = decoder.decode(value, { stream: true });
    // SSE format: "data: ..."
    const lines = chunk.split('\n').filter((l) => l.startsWith('data: '));
    for (const line of lines) {
      const payload = line.slice(6);
      if (payload === '[DONE]') return;
      try {
        const parsed = JSON.parse(payload);
        if (parsed.content) onChunk(parsed.content);
      } catch {
        // raw text chunk
        onChunk(payload);
      }
    }
  }
}

export async function executeAiCommand(data: AiExecuteRequest) {
  const res = await client.post<{ result: string; toolCalls: unknown[] }>('/ai/execute', data);
  return res.data;
}

export async function listTools() {
  const res = await client.get('/api/skill/list');
  return res.data;
}
