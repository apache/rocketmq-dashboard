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

import { describe, expect, it } from 'vitest';
import {
  buildMessageRequest,
  draftToMessageRequest,
  getChatDraft,
  parseConversationId,
  shouldOpenChatHistory,
  shouldOpenTools,
} from './chatDraft';

describe('AI chat draft navigation state', () => {
  it('normalizes a prompt and preserves a selected model', () => {
    expect(getChatDraft({ prompt: '  检查集群状态  ', model: '  qwen3.7-max  ' })).toEqual({
      prompt: '检查集群状态',
      model: 'qwen3.7-max',
    });
  });

  it('preserves supported home modes and drops unknown modes', () => {
    expect(getChatDraft({ prompt: '检查集群状态', mode: 'diagnose' })).toEqual({
      prompt: '检查集群状态',
      mode: 'diagnose',
    });
    expect(getChatDraft({ prompt: '检查集群状态', mode: 'unknown' })).toEqual({
      prompt: '检查集群状态',
    });
  });

  it('rejects invalid or empty navigation state', () => {
    expect(getChatDraft(null)).toBeNull();
    expect(getChatDraft({ prompt: '   ' })).toBeNull();
    expect(getChatDraft({ prompt: 42 })).toBeNull();
  });

  it('drops empty model values after normalization', () => {
    expect(getChatDraft({ prompt: '检查集群状态', model: '   ' })).toEqual({
      prompt: '检查集群状态',
    });
  });

  it('preserves a trimmed instanceId and drops empty or non-string ones', () => {
    expect(getChatDraft({ prompt: '检查集群状态', instanceId: ' rmq-instance-1 ' })).toEqual({
      prompt: '检查集群状态',
      instanceId: 'rmq-instance-1',
    });
    expect(getChatDraft({ prompt: '检查集群状态', instanceId: '   ' })).toEqual({
      prompt: '检查集群状态',
    });
    expect(getChatDraft({ prompt: '检查集群状态', instanceId: 42 })).toEqual({
      prompt: '检查集群状态',
    });
  });

  it('does not treat an instanceId without a prompt as a draft', () => {
    expect(getChatDraft({ instanceId: 'rmq-instance-1' })).toBeNull();
  });

  it('only opens history for the explicit history route intent', () => {
    expect(shouldOpenChatHistory({ historyIntent: 'open' })).toBe(true);
    expect(shouldOpenChatHistory({ historyIntent: 'closed' })).toBe(false);
    expect(shouldOpenChatHistory({ prompt: '检查集群状态' })).toBe(false);
    expect(shouldOpenChatHistory(null)).toBe(false);
  });

  it('only opens tools for the explicit tools route intent', () => {
    expect(shouldOpenTools({ toolsIntent: 'open' })).toBe(true);
    expect(shouldOpenTools({ toolsIntent: 'closed' })).toBe(false);
    expect(shouldOpenTools({ prompt: '检查集群状态' })).toBe(false);
    expect(shouldOpenTools(null)).toBe(false);
  });
});

describe('conversation id and message request helpers', () => {
  it('parses only positive integer conversation ids', () => {
    expect(parseConversationId('42')).toBe(42);
    expect(parseConversationId('7')).toBe(7);
    expect(parseConversationId('0')).toBeNull();
    expect(parseConversationId('-3')).toBeNull();
    expect(parseConversationId('1.5')).toBeNull();
    expect(parseConversationId('abc')).toBeNull();
    expect(parseConversationId('')).toBeNull();
    expect(parseConversationId(undefined)).toBeNull();
  });

  it('omits empty optional overrides from the message request', () => {
    expect(buildMessageRequest('检查集群状态', '', 'claude-code', 'chat')).toEqual({
      message: '检查集群状态',
      engine: 'claude-code',
      mode: 'chat',
    });
    expect(buildMessageRequest('检查集群状态', 'qwen3.8-max', 'qoder', 'diagnose', true)).toEqual({
      message: '检查集群状态',
      model: 'qwen3.8-max',
      engine: 'qoder',
      mode: 'diagnose',
      enhance: true,
    });
    // enhance:false is an omitted override, not a sent one
    expect(buildMessageRequest('hi', '', 'http', 'chat', false)).toEqual({
      message: 'hi',
      engine: 'http',
      mode: 'chat',
    });
  });

  it('derives the auto-send request from a draft with selector fallbacks', () => {
    const draft = getChatDraft({ prompt: '检查集群状态', mode: 'diagnose', enhance: true })!;
    expect(draftToMessageRequest(draft, 'claude-code')).toEqual({
      message: '检查集群状态',
      engine: 'claude-code',
      mode: 'diagnose',
      enhance: true,
    });

    const withEngine = getChatDraft({ prompt: 'hi', engine: 'qoder', model: 'qwen3.8-max' })!;
    expect(draftToMessageRequest(withEngine, 'claude-code')).toEqual({
      message: 'hi',
      model: 'qwen3.8-max',
      engine: 'qoder',
      mode: 'chat',
    });
  });
});
