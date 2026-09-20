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
import { message } from 'antd';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import client from './client';
import type {
  AiAgentCapabilitiesVO,
  AiConversationListItemVO,
  AiConversationVO,
  AiRmqctlConfigVO,
  AiRunVO,
  AiTimelineVO,
} from './aiEvents';
import {
  AI_AGENT_CAPABILITIES_PATH,
  AI_CONVERSATIONS_PATH,
  conversationEventsPath,
  conversationMessagesPath,
  conversationPath,
  conversationRmqctlConfigPath,
  createConversation,
  deleteConversation,
  getAgentCapabilities,
  getConversation,
  getConversationTimeline,
  getRmqctlConfig,
  listConversations,
  runStopPath,
  runStreamPath,
  stopRun,
  updateConversation,
  type AiConversationDetailVO,
} from './aiConversations';

/**
 * The 11 endpoints of `AiConversationController`.
 *
 * Paths are asserted literally because a typo here is invisible until runtime: axios happily POSTs
 * to a path nobody serves and the interceptor turns the 404 into a generic toast. The two SSE
 * endpoints (7 and 8) are asserted through their path builders — `api/ai.ts` opens them with
 * `fetch`, and `api/ai.test.ts` covers the URLs it actually requests.
 */

// The api client toasts every business error through antd. Stubbing it keeps a rejected request
// from rendering a React tree (and its act warning) inside an api test — same as client.test.ts.
vi.mock('antd', () => ({
  message: {
    error: vi.fn(),
  },
}));

const mock = new MockAdapter(client);

const conversation: AiConversationVO = {
  id: 7,
  title: '查看集群状态',
  owner: 'admin',
  engine: 'claude-code',
  model: 'claude-sonnet-4-5',
  mode: 'chat',
  instanceId: 'open-source-local',
  runtimeSessionId: null,
  lastSeq: 12,
  archived: false,
  createdAt: '2026-09-20T02:11:00',
  updatedAt: '2026-09-20T02:12:31',
};

const listItem: AiConversationListItemVO = {
  id: 7,
  title: '查看集群状态',
  engine: 'claude-code',
  model: 'claude-sonnet-4-5',
  mode: 'chat',
  instanceId: 'open-source-local',
  lastRunId: 41,
  lastRunStatus: 'RUNNING',
  updatedAt: '2026-09-20T02:12:31',
  createdAt: '2026-09-20T02:11:00',
};

const run: AiRunVO = {
  id: 41,
  conversationId: 7,
  turn: 3,
  status: 'STOPPED',
  engine: 'claude-code',
  model: 'claude-sonnet-4-5',
  startedAt: '2026-09-20T02:12:00',
  finishedAt: '2026-09-20T02:12:31',
  durationMs: 31000,
  inputTokens: 1200,
  outputTokens: 340,
  stopReason: 'USER_STOP',
  errorCode: null,
  errorMessage: null,
};

const timeline: AiTimelineVO = {
  items: [
    {
      id: 101,
      turn: 3,
      seq: 9,
      runId: 41,
      createdAt: '2026-09-20T02:12:01',
      event: { type: 'user', text: '查看集群状态' },
    },
    {
      id: 102,
      turn: 3,
      seq: 10,
      runId: 41,
      createdAt: '2026-09-20T02:12:02',
      event: { type: 'text', text: '集群当前有 2 个 broker。' },
    },
  ],
  nextAfter: 10,
  activeRun: { id: 41, status: 'RUNNING' },
};

const capabilities: AiAgentCapabilitiesVO = {
  rmqctlAvailable: true,
  claudeAvailable: true,
  qoderAvailable: false,
  mcpEnabled: true,
  l3ToolsAllowed: false,
};

const rmqctlConfig: AiRmqctlConfigVO = {
  snippet: '{"mcpServers":{"rocketmq-studio":{"command":"rmqctl"}}}',
  instanceId: 'open-source-local',
  server: 'http://127.0.0.1:8888',
};

describe('AI conversation API', () => {
  beforeEach(() => {
    mock.reset();
    vi.mocked(message.error).mockClear();
  });
  afterEach(() => mock.reset());

  it('createConversationPostsTheCreateDtoTest', async () => {
    mock.onPost(AI_CONVERSATIONS_PATH).reply((config) => {
      expect(config.url).toBe('/ai/conversations');
      expect(JSON.parse(config.data as string)).toEqual({
        instanceId: 'open-source-local',
        mode: 'chat',
      });
      return [200, { code: 200, message: 'success', data: conversation }];
    });

    await expect(
      createConversation({ instanceId: 'open-source-local', mode: 'chat' }),
    ).resolves.toEqual(conversation);
  });

  it('createConversationSendsAnEmptyBodyByDefaultTest', async () => {
    mock.onPost(AI_CONVERSATIONS_PATH).reply((config) => {
      expect(JSON.parse(config.data as string)).toEqual({});
      return [200, { code: 200, data: conversation }];
    });

    await expect(createConversation()).resolves.toMatchObject({ id: 7 });
  });

  it('listConversationsForwardsPagingSearchAndArchivedTest', async () => {
    mock.onGet(AI_CONVERSATIONS_PATH).reply((config) => {
      expect(config.url).toBe('/ai/conversations');
      expect(config.params).toEqual({ page: 2, size: 20, search: '集群', archived: true });
      return [200, { code: 200, data: { items: [listItem], total: 21, page: 2, size: 20 } }];
    });

    const page = await listConversations({ page: 2, size: 20, search: '集群', archived: true });
    expect(page.total).toBe(21);
    expect(page.items[0].lastRunStatus).toBe('RUNNING');
  });

  it('getConversationUnwrapsTheDetailEnvelopeWithItsActiveRunTest', async () => {
    const detail: AiConversationDetailVO = {
      ...conversation,
      activeRun: { id: 41, status: 'RUNNING' },
    };
    mock.onGet(conversationPath(7)).reply((config) => {
      expect(config.url).toBe('/ai/conversations/7');
      return [200, { code: 200, data: detail }];
    });

    await expect(getConversation(7)).resolves.toEqual(detail);
  });

  it('updateConversationPatchesTitleAndArchivedTest', async () => {
    mock.onPatch(conversationPath(7)).reply((config) => {
      expect(config.url).toBe('/ai/conversations/7');
      expect(JSON.parse(config.data as string)).toEqual({ title: '堆积排查', archived: true });
      return [200, { code: 200, data: { ...conversation, title: '堆积排查', archived: true } }];
    });

    await expect(
      updateConversation(7, { title: '堆积排查', archived: true }),
    ).resolves.toMatchObject({ title: '堆积排查', archived: true });
  });

  it('deleteConversationDeletesWithoutExpectingAPayloadTest', async () => {
    mock.onDelete(conversationPath(7)).reply((config) => {
      expect(config.url).toBe('/ai/conversations/7');
      return [200, { code: 200, data: null }];
    });

    await expect(deleteConversation(7)).resolves.toBeUndefined();
  });

  it('getConversationTimelineForwardsTheCursorTest', async () => {
    mock.onGet(conversationEventsPath(7)).reply((config) => {
      expect(config.url).toBe('/ai/conversations/7/events');
      expect(config.params).toEqual({ after: 10, limit: 200 });
      return [200, { code: 200, data: timeline }];
    });

    const result = await getConversationTimeline(7, { after: 10, limit: 200 });
    expect(result.nextAfter).toBe(10);
    expect(result.activeRun).toEqual({ id: 41, status: 'RUNNING' });
    // run_id is NOT NULL in rmq_ai_event, so the envelope carries it on every row.
    expect(result.items.map((item) => item.runId)).toEqual([41, 41]);
  });

  it('stopRunPostsToTheRunStopPathTest', async () => {
    mock.onPost(runStopPath(41)).reply((config) => {
      expect(config.url).toBe('/ai/runs/41/stop');
      expect(config.data).toBeUndefined();
      return [200, { code: 200, data: run }];
    });

    await expect(stopRun(41)).resolves.toMatchObject({
      status: 'STOPPED',
      stopReason: 'USER_STOP',
    });
  });

  it('stopRunSurfacesTheStaleStopConflictTest', async () => {
    // A stale stop must fail closed (409, nothing killed) rather than stop the newer run. Only the
    // numeric status and the message reach the client — `ai.run.stale_stop` is a server-log-only
    // diagnostic, since the JSON `Result` envelope has no string-code channel — and the interceptor
    // turns that into a rejected promise carrying the server message.
    mock.onPost(runStopPath(41)).reply(409, { code: 409, message: '该会话已有正在进行的回答' });

    await expect(stopRun(41)).rejects.toThrow('该会话已有正在进行的回答');
    expect(vi.mocked(message.error)).toHaveBeenCalledWith('该会话已有正在进行的回答');
  });

  it('getAgentCapabilitiesUnwrapsTheProbeResultTest', async () => {
    mock.onGet(AI_AGENT_CAPABILITIES_PATH).reply((config) => {
      expect(config.url).toBe('/ai/agent-capabilities');
      return [200, { code: 200, data: capabilities }];
    });

    await expect(getAgentCapabilities()).resolves.toEqual(capabilities);
  });

  it('getRmqctlConfigReturnsThePasteReadySnippetTest', async () => {
    mock.onGet(conversationRmqctlConfigPath(7)).reply((config) => {
      expect(config.url).toBe('/ai/conversations/7/rmqctl-config');
      return [200, { code: 200, data: rmqctlConfig }];
    });

    await expect(getRmqctlConfig(7)).resolves.toEqual(rmqctlConfig);
  });

  it('buildsTheTwoStreamingPathsTest', () => {
    // Endpoint 7: POST — send a message and stream the run it starts.
    expect(conversationMessagesPath(7)).toBe('/ai/conversations/7/messages');
    // Endpoint 8: GET — attach to a run that is already generating.
    expect(runStreamPath(41)).toBe('/ai/runs/41/stream');
  });

  it('surfacesAServerSideOwnerRejectionAsA404Test', async () => {
    // Somebody else's conversation id is a 404, not a 403: the owner filter doubles as an
    // anti-enumeration guard and the client must not distinguish the two.
    mock.onGet(conversationPath(8)).reply(404, { code: 404, message: '会话不存在' });

    await expect(getConversation(8)).rejects.toThrow('会话不存在');
  });
});
