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
import {
  AiStreamError,
  attachRunStream,
  executeTool,
  listTools,
  openRunStream,
  type McpTool,
} from './ai';
import type { ChatSseEvent } from './aiEvents';

vi.mock('../config', () => ({
  API_BASE_URL: 'https://backend.example.com/studio-api',
}));

const mock = new MockAdapter(client);
const encoder = new TextEncoder();

/**
 * A 200 SSE response. The content type matters: `openRunStream`/`attachRunStream` reject anything
 * that is not `text/event-stream`, which is how a buffering gateway gets caught instead of hanging.
 * Passing `onCancel` leaves the stream open (and spies on the reader cancellation).
 */
function eventStreamResponse(chunks: string[], onCancel?: () => void): Response {
  const body = new ReadableStream<Uint8Array>({
    start(controller) {
      chunks.forEach((chunk) => controller.enqueue(encoder.encode(chunk)));
      if (!onCancel) controller.close();
    },
    cancel: onCancel,
  });
  return new Response(body, {
    status: 200,
    headers: { 'content-type': 'text/event-stream;charset=UTF-8' },
  });
}

/** The run stream carries every domain frame under one event name, discriminated on `type`. */
function agentFrame(event: Record<string, unknown>): string {
  return `event: agent\ndata: ${JSON.stringify(event)}\n\n`;
}

function doneFrame(): string {
  return 'event: done\ndata: {}\n\n';
}

function jsonResponse(payload: unknown, status: number, statusText = ''): Response {
  return new Response(JSON.stringify(payload), {
    status,
    statusText,
    headers: { 'content-type': 'application/json' },
  });
}

describe('AI API', () => {
  beforeEach(() => {
    mock.reset();
    localStorage.clear();
  });

  afterEach(() => {
    mock.reset();
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  describe('openRunStream / attachRunStream (SSE)', () => {
    const body = { message: '列出 topic', model: 'claude-sonnet-4-5' };

    it('postsTheMessageToTheConversationEndpointTest', async () => {
      const fetchMock = vi.fn().mockResolvedValue(eventStreamResponse([doneFrame()]));
      vi.stubGlobal('fetch', fetchMock);

      await expect(openRunStream(7, body, { onEvent: vi.fn() })).resolves.toBeUndefined();

      expect(fetchMock).toHaveBeenCalledWith(
        'https://backend.example.com/studio-api/ai/conversations/7/messages',
        expect.objectContaining({
          method: 'POST',
          credentials: 'include',
          headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
          body: JSON.stringify(body),
        }),
      );
    });

    it('attachesToTheRunStreamWithTheReplayCursorAndTheAbortSignalTest', async () => {
      const fetchMock = vi.fn().mockResolvedValue(eventStreamResponse([doneFrame()]));
      vi.stubGlobal('fetch', fetchMock);
      const controller = new AbortController();

      await expect(
        attachRunStream(41, 12, { onEvent: vi.fn() }, controller.signal),
      ).resolves.toBeUndefined();

      expect(fetchMock).toHaveBeenCalledWith(
        'https://backend.example.com/studio-api/ai/runs/41/stream?after=12',
        expect.objectContaining({
          method: 'GET',
          credentials: 'include',
          headers: { Accept: 'text/event-stream' },
          signal: controller.signal,
        }),
      );
    });

    it('dispatchesEveryAgentFrameInArrivalOrderTest', async () => {
      vi.stubGlobal(
        'fetch',
        vi.fn().mockResolvedValue(
          eventStreamResponse([
            agentFrame({
              type: 'run_started',
              runId: 41,
              conversationId: 7,
              title: '查看集群状态',
              turn: 3,
            }),
            agentFrame({ type: 'thinking', content: '先查 topic 路由', source: 'model' }),
            agentFrame({
              type: 'tool_start',
              tcId: 'toolu_01A',
              tool: 'rmq.topic.list',
              input: {},
            }),
            agentFrame({
              type: 'tool_done',
              tcId: 'toolu_01A',
              tool: 'rmq.topic.list',
              output: '{"items":[]}',
              outputBytes: 13,
              truncated: false,
              durationMs: 212,
              success: true,
            }),
            agentFrame({ type: 'text_delta', content: '集群当前有 2 个 broker。' }),
            agentFrame({ type: 'run_finished', runId: 41, status: 'COMPLETED', durationMs: 8123 }),
            doneFrame(),
          ]),
        ),
      );
      const events: ChatSseEvent[] = [];

      await openRunStream(7, body, { onEvent: (event) => events.push(event) });

      // Run-level frames are delivered too: the caller needs the runId to stop or re-attach.
      expect(events.map((event) => event.type)).toEqual([
        'run_started',
        'thinking',
        'tool_start',
        'tool_done',
        'text_delta',
        'run_finished',
      ]);
      expect(events[4]).toEqual({ type: 'text_delta', content: '集群当前有 2 个 broker。' });
    });

    it('terminatesOnTheDoneEventNameAndCancelsTheReaderTest', async () => {
      const onCancel = vi.fn();
      vi.stubGlobal(
        'fetch',
        vi
          .fn()
          .mockResolvedValue(
            eventStreamResponse(
              [
                agentFrame({ type: 'text_delta', content: 'hi' }),
                doneFrame(),
                agentFrame({ type: 'text_delta', content: 'never delivered' }),
              ],
              onCancel,
            ),
          ),
      );
      const onEvent = vi.fn();

      await openRunStream(7, body, { onEvent });

      expect(onEvent).toHaveBeenCalledTimes(1);
      expect(onEvent).toHaveBeenCalledWith({ type: 'text_delta', content: 'hi' });
      expect(onCancel).toHaveBeenCalledTimes(1);
    });

    it('noLongerTreatsTheDoneSentinelAsTerminalTest', async () => {
      // `data: [DONE]` has no `event:` line, so it arrives as the legacy `message` name — which the
      // run stream rejects instead of silently ending on.
      vi.stubGlobal('fetch', vi.fn().mockResolvedValue(eventStreamResponse(['data: [DONE]\n\n'])));

      await expect(openRunStream(7, body, { onEvent: vi.fn() })).rejects.toMatchObject({
        name: 'AiStreamError',
        code: 'llm.stream.unknown_event',
      } satisfies Partial<AiStreamError>);
    });

    it('rejectsTheLegacyMessageAndEnhanceEventNamesTest', async () => {
      vi.stubGlobal(
        'fetch',
        vi
          .fn()
          .mockResolvedValue(
            eventStreamResponse(['event: message\ndata: {"content":"hello"}\n\n']),
          ),
      );

      await expect(openRunStream(7, body, { onEvent: vi.fn() })).rejects.toMatchObject({
        message: 'Unknown AI stream event: message',
        code: 'llm.stream.unknown_event',
        hint: 'Expected one of: agent, error, done.',
      } satisfies Partial<AiStreamError>);
    });

    it('reassemblesAnAgentFrameSplitAcrossNetworkChunksTest', async () => {
      vi.stubGlobal(
        'fetch',
        vi
          .fn()
          .mockResolvedValue(
            eventStreamResponse([
              'event: agent\r\ndata: {"type":"text_del',
              'ta","content":"hello"}\r\n\r\nevent: done\r\ndata: {}\r\n\r\n',
            ]),
          ),
      );
      const events: ChatSseEvent[] = [];

      await openRunStream(7, body, { onEvent: (event) => events.push(event) });

      expect(events).toEqual([{ type: 'text_delta', content: 'hello' }]);
    });

    it('dispatchesMultipleAgentFramesDeliveredInOneChunkTest', async () => {
      vi.stubGlobal(
        'fetch',
        vi
          .fn()
          .mockResolvedValue(
            eventStreamResponse([
              agentFrame({ type: 'text_delta', content: 'first' }) +
                agentFrame({ type: 'text_delta', content: 'second' }) +
                doneFrame(),
            ]),
          ),
      );
      const contents: string[] = [];

      await openRunStream(7, body, {
        onEvent: (event) => {
          if (event.type === 'text_delta') contents.push(event.content);
        },
      });

      expect(contents).toEqual(['first', 'second']);
    });

    it('joinsMultilineDataBeforeParsingTest', async () => {
      vi.stubGlobal(
        'fetch',
        vi
          .fn()
          .mockResolvedValue(
            eventStreamResponse([
              'event: agent\ndata: {"type":"text_delta",\ndata: "content":"hello"}\n\n',
              doneFrame(),
            ]),
          ),
      );
      const events: ChatSseEvent[] = [];

      await openRunStream(7, body, { onEvent: (event) => events.push(event) });

      expect(events).toEqual([{ type: 'text_delta', content: 'hello' }]);
    });

    it('ignoresHeartbeatCommentFramesTest', async () => {
      // The server writes `comment("hb")` every 15s; a comment frame carries no data and renders
      // nothing, but it must still reach the reader so the idle watchdog is reset.
      vi.stubGlobal(
        'fetch',
        vi
          .fn()
          .mockResolvedValue(
            eventStreamResponse([
              ':hb\n\n',
              agentFrame({ type: 'text_delta', content: 'hi' }),
              ':hb\n\n',
              doneFrame(),
            ]),
          ),
      );
      const onEvent = vi.fn();

      await openRunStream(7, body, { onEvent });

      expect(onEvent).toHaveBeenCalledTimes(1);
      expect(onEvent).toHaveBeenCalledWith({ type: 'text_delta', content: 'hi' });
    });

    it('rejectsAnUnboundedAgentFrameAndCancelsTheReaderTest', async () => {
      const onCancel = vi.fn();
      vi.stubGlobal(
        'fetch',
        vi
          .fn()
          .mockResolvedValue(
            eventStreamResponse([`event: agent\ndata: ${'x'.repeat(1024 * 1024)}`], onCancel),
          ),
      );

      await expect(openRunStream(7, body, { onEvent: vi.fn() })).rejects.toMatchObject({
        message: 'AI stream event exceeds 1 MiB',
        code: 'llm.stream.event_too_large',
      } satisfies Partial<AiStreamError>);
      expect(onCancel).toHaveBeenCalledTimes(1);
    });

    it('throwsStructuredErrorsFromErrorFramesTest', async () => {
      vi.stubGlobal(
        'fetch',
        vi
          .fn()
          .mockResolvedValue(
            eventStreamResponse([
              'event: error\n',
              'data: {"status":409,"code":"ai.run.busy","message":"该会话已有正在进行的回答","hint":"刷新后重试"}\n\n',
            ]),
          ),
      );

      await expect(openRunStream(7, body, { onEvent: vi.fn() })).rejects.toMatchObject({
        name: 'AiStreamError',
        message: '该会话已有正在进行的回答',
        code: 'ai.run.busy',
        hint: '刷新后重试',
        status: 409,
      } satisfies Partial<AiStreamError>);
    });

    it('rejectsAnAgentFrameWhoseTypeIsNotInTheContractTest', async () => {
      // A server that adds a live event type without shipping a new web bundle must fail visibly
      // rather than render a silently truncated answer.
      vi.stubGlobal(
        'fetch',
        vi.fn().mockResolvedValue(eventStreamResponse([agentFrame({ type: 'artifact', id: 1 })])),
      );

      await expect(openRunStream(7, body, { onEvent: vi.fn() })).rejects.toMatchObject({
        message: 'Unknown AI stream event type: artifact',
        code: 'llm.stream.unknown_event_type',
      } satisfies Partial<AiStreamError>);
    });

    it('rejectsAMalformedAgentPayloadTest', async () => {
      vi.stubGlobal(
        'fetch',
        vi
          .fn()
          .mockResolvedValue(
            eventStreamResponse(['event: agent\ndata: {"type":"text_delta",\n\n']),
          ),
      );

      await expect(openRunStream(7, body, { onEvent: vi.fn() })).rejects.toMatchObject({
        code: 'llm.stream.malformed_event',
      } satisfies Partial<AiStreamError>);
    });

    it('rejectsAResponseThatIsNotAnEventStreamTest', async () => {
      // A buffering gateway answers 200 with HTML and then yields nothing: without this guard the
      // UI spins forever with an empty console.
      vi.stubGlobal(
        'fetch',
        vi.fn().mockImplementation(
          async () =>
            new Response('<html><body>502 Bad Gateway</body></html>', {
              status: 200,
              headers: { 'content-type': 'text/html;charset=utf-8' },
            }),
        ),
      );

      await expect(openRunStream(7, body, { onEvent: vi.fn() })).rejects.toMatchObject({
        name: 'AiStreamError',
        code: 'llm.stream.unexpected_content_type',
        status: 200,
      } satisfies Partial<AiStreamError>);
      await expect(openRunStream(7, body, { onEvent: vi.fn() })).rejects.toThrow(
        /text\/html;charset=utf-8/,
      );
    });

    it('rejectsAResponseWithoutAnyContentTypeTest', async () => {
      // A null body is the only way to get a Response with no content-type at all: a string body
      // makes the Fetch API default the header to text/plain.
      vi.stubGlobal(
        'fetch',
        vi.fn().mockImplementation(async () => new Response(null, { status: 200 })),
      );

      await expect(openRunStream(7, body, { onEvent: vi.fn() })).rejects.toMatchObject({
        code: 'llm.stream.unexpected_content_type',
      } satisfies Partial<AiStreamError>);
      await expect(openRunStream(7, body, { onEvent: vi.fn() })).rejects.toThrow(/no content-type/);
    });

    it('acceptsAnEventStreamContentTypeWithOrWithoutACharsetTest', async () => {
      const onEvent = vi.fn();
      vi.stubGlobal(
        'fetch',
        vi.fn().mockImplementation(
          async () =>
            new Response(
              new ReadableStream<Uint8Array>({
                start(controller) {
                  controller.enqueue(encoder.encode(doneFrame()));
                  controller.close();
                },
              }),
              { status: 200, headers: { 'content-type': 'text/event-stream' } },
            ),
        ),
      );

      await expect(openRunStream(7, body, { onEvent })).resolves.toBeUndefined();
      expect(onEvent).not.toHaveBeenCalled();
    });

    it('surfacesTheBackendMessageOnAFailedHttpRequestTest', async () => {
      vi.stubGlobal(
        'fetch',
        vi
          .fn()
          .mockImplementation(async () =>
            jsonResponse({ code: 409, message: '该会话已有正在进行的回答', data: null }, 409),
          ),
      );

      await expect(openRunStream(7, body, { onEvent: vi.fn() })).rejects.toMatchObject({
        name: 'AiStreamError',
        message: '该会话已有正在进行的回答',
        status: 409,
      } satisfies Partial<AiStreamError>);
    });

    it('labelsAnEmptyErrorBodyWithTheStreamItWasOpeningTest', async () => {
      vi.stubGlobal(
        'fetch',
        vi
          .fn()
          .mockImplementation(
            async () => new Response('', { status: 502, statusText: 'Bad Gateway' }),
          ),
      );

      await expect(openRunStream(7, body, { onEvent: vi.fn() })).rejects.toMatchObject({
        message: 'AI message stream failed: Bad Gateway',
        status: 502,
      } satisfies Partial<AiStreamError>);
      await expect(attachRunStream(41, 0, { onEvent: vi.fn() })).rejects.toMatchObject({
        message: 'AI run stream failed: Bad Gateway',
        status: 502,
      } satisfies Partial<AiStreamError>);
    });

    it('clearsTheSessionWhenTheStreamRequestReturns401Test', async () => {
      localStorage.setItem('rocketmq-studio-user', 'admin');
      localStorage.setItem('rocketmq-studio-user-admin', 'true');
      vi.stubGlobal(
        'fetch',
        vi
          .fn()
          .mockImplementation(async () =>
            jsonResponse({ code: 401, message: 'Unauthorized', data: null }, 401, 'Unauthorized'),
          ),
      );

      await expect(openRunStream(7, body, { onEvent: vi.fn() })).rejects.toMatchObject({
        status: 401,
      });

      expect(localStorage.getItem('rocketmq-studio-user')).toBeNull();
      expect(localStorage.getItem('rocketmq-studio-user-admin')).toBeNull();
    });

    it('timesOutWhenNoByteArrivesForTwiceTheServerHeartbeatTest', async () => {
      vi.useFakeTimers();
      const onCancel = vi.fn();
      vi.stubGlobal(
        'fetch',
        vi
          .fn()
          .mockResolvedValue(
            eventStreamResponse([agentFrame({ type: 'text_delta', content: 'hi' })], onCancel),
          ),
      );
      const onEvent = vi.fn();
      const failure = vi.fn();
      const stream = openRunStream(7, body, { onEvent }).catch(failure);

      // The first frame arrives, then the connection goes quiet.
      await vi.advanceTimersByTimeAsync(1_000);
      expect(onEvent).toHaveBeenCalledTimes(1);

      // 29s of silence is survivable: one late heartbeat must not kill a run.
      await vi.advanceTimersByTimeAsync(28_000);
      expect(failure).not.toHaveBeenCalled();

      await vi.advanceTimersByTimeAsync(2_000);
      expect(failure).toHaveBeenCalledWith(
        expect.objectContaining({
          name: 'AiStreamError',
          code: 'llm.stream.idle_timeout',
          message: 'AI stream idle for more than 30s',
        }),
      );
      expect(onCancel).toHaveBeenCalledTimes(1);
      await stream;
    });

    it('keepsTheStreamAliveWhileHeartbeatsArriveTest', async () => {
      vi.useFakeTimers();
      let controller: ReadableStreamDefaultController<Uint8Array> | undefined;
      const response = new Response(
        new ReadableStream<Uint8Array>({
          start(streamController) {
            controller = streamController;
          },
        }),
        { status: 200, headers: { 'content-type': 'text/event-stream' } },
      );
      vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response));
      const failure = vi.fn();
      const stream = openRunStream(7, body, { onEvent: vi.fn() }).catch(failure);

      // The server heartbeats every 15s; beat every 20s here, still inside the 30s watchdog.
      const beat = window.setInterval(() => controller?.enqueue(encoder.encode(':hb\n\n')), 20_000);
      await vi.advanceTimersByTimeAsync(120_000);
      expect(failure).not.toHaveBeenCalled();

      // Once the beats stop the same watchdog fires, which is what makes it a watchdog.
      window.clearInterval(beat);
      await vi.advanceTimersByTimeAsync(31_000);
      expect(failure).toHaveBeenCalledWith(
        expect.objectContaining({ code: 'llm.stream.idle_timeout' }),
      );
      await stream;
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

    it('scopes tool discovery to the selected cluster', async () => {
      mock.onGet('/ai/tools', { params: { cluster: 'cluster-a' } }).reply(200, { data: [] });

      await expect(listTools('cluster-a')).resolves.toEqual([]);
    });

    it('should handle server error', async () => {
      mock.onGet('/ai/tools').reply(500);
      await expect(listTools()).rejects.toThrow();
    });
  });

  describe('executeTool', () => {
    it('posts structured input with the instanceId query param and returns structured output', async () => {
      const input = { instanceId: 'cluster-a', topicName: 'orders' };
      const output = { items: [{ name: 'orders' }], total: 1 };
      mock
        .onPost('/ai/tools/rmq.topic.list/execute', input, {
          params: { instanceId: 'cluster-a' },
        })
        .reply(200, { data: output });

      await expect(executeTool('rmq.topic.list', input, 'cluster-a')).resolves.toEqual(output);
    });
  });
});
