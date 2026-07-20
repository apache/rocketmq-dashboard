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
    it('should post AI command and return result with tool calls', async () => {
      const request: AiExecuteRequest = {
        message: 'list topics',
        mode: 'agent',
        model: 'gpt-4',
      };
      const mockResult = { result: 'Found 5 topics', toolCalls: [{ name: 'listTopics' }] };
      mock.onPost('/ai/execute', request).reply(200, { data: mockResult });

      const result = await executeAiCommand(request);
      expect(result.result).toBe('Found 5 topics');
      expect(result.toolCalls).toHaveLength(1);
      expect(result.toolCalls[0].name).toBe('listTopics');
    });

    it('should handle empty tool calls', async () => {
      mock.onPost('/ai/execute').reply(200, { data: { result: 'Hi!', toolCalls: [] } });

      const result = await executeAiCommand({
        message: 'hello',
        mode: 'chat',
        model: 'gpt-4',
      });
      expect(result.result).toBe('Hi!');
      expect(result.toolCalls).toEqual([]);
    });

    it('should handle server error', async () => {
      mock.onPost('/ai/execute').reply(500);
      await expect(
        executeAiCommand({ message: 'test', mode: 'chat', model: 'gpt-4' }),
      ).rejects.toThrow();
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
