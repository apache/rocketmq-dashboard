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

import MockAdapter from 'axios-mock-adapter';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import client from './client';
import { chatStream, executeAiCommand, listTools, type AiExecuteRequest, type McpTool } from './ai';

vi.mock('../config', () => ({
  API_BASE_URL: 'https://api.example.test/custom-api',
}));

const mock = new MockAdapter(client);
const encoder = new TextEncoder();

function streamResponse(chunks: string[]): Response {
  const body = new ReadableStream<Uint8Array>({
    start(controller) {
      chunks.forEach((chunk) => controller.enqueue(encoder.encode(chunk)));
      controller.close();
    },
  });
  return new Response(body, { status: 200 });
}

describe('AI API', () => {
  beforeEach(() => {
    mock.reset();
    vi.stubGlobal('localStorage', {
      getItem: vi.fn().mockReturnValue('test-token'),
      setItem: vi.fn(),
      removeItem: vi.fn(),
    });
  });

  afterEach(() => {
    mock.reset();
    vi.unstubAllGlobals();
  });

  describe('chatStream (SSE)', () => {
    it('uses the configured API base URL and stored token', async () => {
      const fetchMock = vi
        .fn()
        .mockResolvedValue(streamResponse(['event: done\ndata: [DONE]\n\n']));
      vi.stubGlobal('fetch', fetchMock);

      await chatStream({ message: 'hello', mode: 'chat', model: 'stub' }, vi.fn());

      expect(fetchMock).toHaveBeenCalledWith(
        'https://api.example.test/custom-api/ai/chat',
        expect.objectContaining({
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            Authorization: 'Bearer test-token',
          },
        }),
      );
    });

    it('omits the authorization header when no token is stored', async () => {
      vi.mocked(localStorage.getItem).mockReturnValue(null);
      const fetchMock = vi
        .fn()
        .mockResolvedValue(streamResponse(['event: done\ndata: [DONE]\n\n']));
      vi.stubGlobal('fetch', fetchMock);

      await chatStream({ message: 'hello', mode: 'chat', model: 'stub' }, vi.fn());

      expect(fetchMock).toHaveBeenCalledWith(
        'https://api.example.test/custom-api/ai/chat',
        expect.objectContaining({
          headers: {
            'Content-Type': 'application/json',
          },
        }),
      );
    });

    it('reports the gateway error message for a failed response', async () => {
      vi.stubGlobal(
        'fetch',
        vi.fn().mockResolvedValue(
          new Response(JSON.stringify({ code: 502, message: 'LLM provider unavailable' }), {
            status: 502,
            statusText: 'Bad Gateway',
            headers: { 'Content-Type': 'application/json' },
          }),
        ),
      );

      await expect(
        chatStream({ message: 'hello', mode: 'chat', model: 'stub' }, vi.fn()),
      ).rejects.toThrow('AI chat failed: LLM provider unavailable');
    });

    it('falls back to the HTTP status for a non-JSON error response', async () => {
      vi.stubGlobal(
        'fetch',
        vi
          .fn()
          .mockResolvedValue(
            new Response('Bad gateway', { status: 502, statusText: 'Bad Gateway' }),
          ),
      );

      await expect(
        chatStream({ message: 'hello', mode: 'chat', model: 'stub' }, vi.fn()),
      ).rejects.toThrow('AI chat failed: Bad Gateway');
    });

    it('rejects a successful response without a stream body', async () => {
      vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 200 })));

      await expect(
        chatStream({ message: 'hello', mode: 'chat', model: 'stub' }, vi.fn()),
      ).rejects.toThrow('AI chat failed: empty response body');
    });

    it('reassembles an event split across network chunks', async () => {
      vi.stubGlobal(
        'fetch',
        vi
          .fn()
          .mockResolvedValue(
            streamResponse([
              'event: message\r\ndata: {"text":"hel',
              'lo"}\r\n\r\nevent: done\r\ndata: [DONE]\r\n\r\n',
            ]),
          ),
      );
      const chunks: string[] = [];

      await chatStream({ message: 'hello', mode: 'chat', model: 'stub' }, (text) =>
        chunks.push(text),
      );

      expect(chunks).toEqual(['hello']);
    });

    it('dispatches multiple events delivered in one network chunk', async () => {
      vi.stubGlobal(
        'fetch',
        vi
          .fn()
          .mockResolvedValue(
            streamResponse([
              'data: {"content":"first"}\n\ndata: {"content":"second"}\n\ndata: [DONE]\n\n',
            ]),
          ),
      );
      const chunks: string[] = [];

      await chatStream({ message: 'hello', mode: 'chat', model: 'stub' }, (text) =>
        chunks.push(text),
      );

      expect(chunks).toEqual(['first', 'second']);
    });

    it('supports multiline and raw SSE data at end of stream', async () => {
      vi.stubGlobal(
        'fetch',
        vi
          .fn()
          .mockResolvedValue(
            streamResponse(['data: {"content":\ndata: "hello"}\n\ndata: raw text']),
          ),
      );
      const chunks: string[] = [];

      await chatStream({ message: 'hello', mode: 'chat', model: 'stub' }, (text) =>
        chunks.push(text),
      );

      expect(chunks).toEqual(['hello', 'raw text']);
    });
  });

  describe('executeAiCommand', () => {
    it('should post the backend command contract and return its result', async () => {
      const request: AiExecuteRequest = {
        command: 'list_topics',
        mode: 'agent',
        model: 'gpt-4',
        conversationId: 'conversation-1',
        prompt: 'List all topics',
        context: { cluster: 'cluster-a' },
      };
      const mockResult = { success: true, result: 'Found 5 topics' };
      mock.onPost('/ai/execute', request).reply(200, { data: mockResult });

      const result = await executeAiCommand(request);

      expect(result.success).toBe(true);
      expect(result.result).toBe('Found 5 topics');
    });

    it('should return an unsuccessful execution result', async () => {
      const request: AiExecuteRequest = {
        command: 'unknown_command',
        mode: 'agent',
        model: 'gpt-4',
      };
      mock
        .onPost('/ai/execute', request)
        .reply(200, { data: { success: false, result: 'Unsupported command' } });

      const result = await executeAiCommand(request);

      expect(result.success).toBe(false);
      expect(result.result).toBe('Unsupported command');
    });

    it('should handle server error', async () => {
      mock.onPost('/ai/execute').reply(500);
      await expect(executeAiCommand({ command: 'test' })).rejects.toThrow();
    });
  });

  describe('listTools', () => {
    it('should return list of MCP tools', async () => {
      const mockTools: McpTool[] = [
        { name: 'listTopics', description: 'List all topics', parameters: {} },
        { name: 'createTopic', description: 'Create a topic', parameters: { type: 'object' } },
      ];
      mock.onGet('/ai/tools').reply(200, { data: mockTools });

      const result = await listTools();
      expect(result).toHaveLength(2);
      expect(result[0].name).toBe('listTopics');
      expect(result[1].name).toBe('createTopic');
    });

    it('should return empty list when no tools available', async () => {
      mock.onGet('/ai/tools').reply(200, { data: [] });

      const result = await listTools();
      expect(result).toEqual([]);
    });

    it('should handle server error', async () => {
      mock.onGet('/ai/tools').reply(500);
      await expect(listTools()).rejects.toThrow();
    });
  });
});
