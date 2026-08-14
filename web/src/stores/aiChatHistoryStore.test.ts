/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { afterEach, describe, expect, it, vi } from 'vitest';

const STORAGE_KEY = 'rocketmq-studio-ai-chat-history';

async function loadStore(persisted?: object) {
  vi.resetModules();
  if (persisted) sessionStorage.setItem(STORAGE_KEY, JSON.stringify({ state: persisted, version: 0 }));
  return (await import('./aiChatHistoryStore')).useAiChatHistoryStore;
}

describe('aiChatHistoryStore', () => {
  afterEach(() => {
    vi.resetModules();
    sessionStorage.clear();
  });

  it('keeps multiple conversations independently for each data mode', async () => {
    const store = await loadStore();
    store.getState().startConversation('real', 'real-1');
    store.getState().setMessages('real', 'real-1', [{ id: 'r1', role: 'user', text: 'First' }]);
    store.getState().startConversation('real', 'real-2');
    store.getState().setMessages('real', 'real-2', [{ id: 'r2', role: 'user', text: 'Second' }]);
    store.getState().startConversation('mock', 'mock-1');
    store.getState().setMessages('mock', 'mock-1', [{ id: 'm1', role: 'user', text: 'Mock' }]);

    const real = store.getState().histories.real;
    expect(real.conversations.map((conversation) => conversation.id)).toEqual(['real-2', 'real-1']);
    expect(real.conversations[1].messages[0]?.text).toBe('First');
    expect(store.getState().histories.mock.conversations).toHaveLength(1);
  });

  it('migrates the previous current conversation to the Real conversation list', async () => {
    const store = await loadStore({
      conversationId: 'conversation-2',
      messages: [{ id: 'message-2', role: 'ai', summary: '', pending: true }],
    });

    expect(store.getState().histories.mock).toEqual({ conversations: [], activeConversationId: null });
    expect(store.getState().histories.real.activeConversationId).toBe('conversation-2');
    expect(store.getState().histories.real.conversations[0]).toMatchObject({
      id: 'conversation-2',
      messages: [{ id: 'message-2', role: 'ai', summary: '', pending: false }],
    });
  });

  it('selects a previous conversation without deleting newer conversations', async () => {
    const store = await loadStore();
    store.getState().startConversation('real', 'first');
    store.getState().startConversation('real', 'second');

    store.getState().selectConversation('real', 'first');

    expect(store.getState().histories.real.activeConversationId).toBe('first');
    expect(store.getState().histories.real.conversations).toHaveLength(2);
  });

  it('bounds persisted conversations and messages', async () => {
    const store = await loadStore();
    for (let index = 0; index < 21; index += 1) {
      store.getState().startConversation('real', `conversation-${index}`);
    }
    const messages = Array.from({ length: 101 }, (_, index) => ({
      id: `message-${index}`,
      role: 'user' as const,
      text: `Message ${index}`,
    }));
    store.getState().setMessages('real', 'conversation-20', messages);

    const conversations = store.getState().histories.real.conversations;
    expect(conversations).toHaveLength(20);
    expect(conversations[0]?.id).toBe('conversation-20');
    expect(conversations[0]?.messages).toHaveLength(100);
    expect(conversations[0]?.messages[0]?.id).toBe('message-1');
  });

  it('derives recent conversations from the first user message', async () => {
    const { getRecentAiChatConversations } = await import('./aiChatHistoryStore');
    const recent = getRecentAiChatConversations([
      { id: 'empty', messages: [], updatedAt: 3 },
      { id: 'prompt', messages: [{ id: 'p', role: 'user', text: 'Inspect lag' }], updatedAt: 2 },
    ]);

    expect(recent).toEqual([expect.objectContaining({ id: 'prompt', prompt: 'Inspect lag' })]);
  });
});
