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

import { create } from 'zustand';
import { createJSONStorage, persist, type StateStorage } from 'zustand/middleware';

export interface AiChatMessage {
  id: string;
  role: 'user' | 'ai';
  createdAt?: number;
  text?: string;
  summary?: string;
  thinking?: string;
  pending?: boolean;
}

export type AiChatDataMode = 'mock' | 'real';

export interface AiChatConversation {
  id: string;
  messages: AiChatMessage[];
  updatedAt: number;
}

export interface AiChatHistory {
  conversations: AiChatConversation[];
  activeConversationId: string | null;
}

export interface RecentAiChatConversation extends AiChatConversation {
  prompt: string;
}

export const MAX_AI_CHAT_CONVERSATIONS = 20;
export const MAX_AI_CHAT_MESSAGES = 100;
export const MAX_AI_CHAT_HISTORY_BYTES = 512 * 1024;
export const MAX_AI_CHAT_MESSAGE_FIELD_LENGTH = 16 * 1024;

const AI_CHAT_HISTORY_STORAGE_KEY = 'rocketmq-studio-ai-chat-history';
const AI_CHAT_HISTORY_PERSIST_INTERVAL_MS = 250;

interface AiChatHistoryState {
  histories: Record<AiChatDataMode, AiChatHistory>;
  startConversation: (mode: AiChatDataMode, conversationId: string) => void;
  selectConversation: (mode: AiChatDataMode, conversationId: string) => void;
  setMessages: (
    mode: AiChatDataMode,
    conversationId: string,
    messages: AiChatMessage[] | ((messages: AiChatMessage[]) => AiChatMessage[]),
  ) => void;
  clearHistories: () => void;
}

const emptyHistory = (): AiChatHistory => ({ conversations: [], activeConversationId: null });

const isRecord = (value: unknown): value is Record<string, unknown> =>
  typeof value === 'object' && value !== null;

const truncateMessageField = (value: unknown): string | undefined => {
  if (typeof value !== 'string') return undefined;
  return value.length <= MAX_AI_CHAT_MESSAGE_FIELD_LENGTH
    ? value
    : `${value.slice(0, MAX_AI_CHAT_MESSAGE_FIELD_LENGTH)}\n\n[Truncated]`;
};

const boundMessageFields = (messages: AiChatMessage[]): AiChatMessage[] =>
  messages.slice(-MAX_AI_CHAT_MESSAGES).map((message) => ({
    ...message,
    text: truncateMessageField(message.text),
    summary: truncateMessageField(message.summary),
    thinking: truncateMessageField(message.thinking),
  }));

const restoreMessages = (messages: unknown): AiChatMessage[] =>
  (Array.isArray(messages) ? messages : [])
    .filter(isRecord)
    .flatMap((message) => {
      if (typeof message.id !== 'string' || (message.role !== 'user' && message.role !== 'ai')) return [];
      return [{
        id: message.id,
        role: message.role as AiChatMessage['role'],
        createdAt: typeof message.createdAt === 'number' ? message.createdAt : undefined,
        text: truncateMessageField(message.text),
        summary: truncateMessageField(message.summary),
        thinking: truncateMessageField(message.thinking),
        pending: false,
      }];
    })
    .slice(-MAX_AI_CHAT_MESSAGES);

const limitHistorySize = (history: AiChatHistory): AiChatHistory => {
  const conversations = history.conversations.map((conversation) => ({ ...conversation, messages: [...conversation.messages] }));
  while (conversations.length > 0 && JSON.stringify({ conversations }).length > MAX_AI_CHAT_HISTORY_BYTES) {
    const oldestIndex = conversations.length - 1;
    const oldest = conversations[oldestIndex];
    if (oldest.messages.length > 1) {
      conversations[oldestIndex] = { ...oldest, messages: oldest.messages.slice(1) };
    } else {
      conversations.pop();
    }
  }
  return {
    conversations,
    activeConversationId: conversations.some((item) => item.id === history.activeConversationId)
      ? history.activeConversationId
      : conversations[0]?.id ?? null,
  };
};

export const getRecentAiChatConversations = (
  conversations: AiChatConversation[],
  limit = 8,
): RecentAiChatConversation[] =>
  conversations
    .map((conversation) => ({
      ...conversation,
      prompt: conversation.messages.find((item) => item.role === 'user' && item.text?.trim())?.text,
    }))
    .filter((conversation): conversation is RecentAiChatConversation => Boolean(conversation.prompt))
    .slice(0, limit);

const restoreHistory = (history?: Partial<AiChatHistory> & { messages?: AiChatMessage[]; conversationId?: string | null }): AiChatHistory => {
  if (Array.isArray(history?.conversations)) {
    const conversations = history.conversations
      .filter((conversation): conversation is AiChatConversation => isRecord(conversation) && typeof conversation.id === 'string')
      .slice(0, MAX_AI_CHAT_CONVERSATIONS)
      .map((conversation) => ({
        id: conversation.id,
        messages: restoreMessages(conversation.messages),
        updatedAt: typeof conversation.updatedAt === 'number' ? conversation.updatedAt : 0,
      }));
    return limitHistorySize({
      conversations,
      activeConversationId: conversations.some((item) => item.id === history.activeConversationId)
        ? history.activeConversationId ?? null
        : conversations[0]?.id ?? null,
    });
  }

  if (history?.conversationId || history?.messages?.length) {
    const id = history.conversationId ?? 'legacy-conversation';
    return limitHistorySize({
      conversations: [{ id, messages: restoreMessages(history.messages), updatedAt: 0 }],
      activeConversationId: id,
    });
  }
  return emptyHistory();
};

let pendingPersist: { name: string; value: string } | null = null;
let persistTimer: ReturnType<typeof setTimeout> | null = null;

export const flushAiChatHistoryPersistence = (): void => {
  if (persistTimer) {
    clearTimeout(persistTimer);
    persistTimer = null;
  }
  const pending = pendingPersist;
  pendingPersist = null;
  if (!pending) return;
  try {
    sessionStorage.setItem(pending.name, pending.value);
  } catch {
    // The in-memory conversation remains available if browser storage is unavailable.
  }
};

const throttledHistoryStorage: StateStorage = {
  getItem: (name) => sessionStorage.getItem(name),
  setItem: (name, value) => {
    pendingPersist = { name, value };
    if (persistTimer) return;
    persistTimer = setTimeout(flushAiChatHistoryPersistence, AI_CHAT_HISTORY_PERSIST_INTERVAL_MS);
  },
  removeItem: (name) => {
    if (persistTimer) {
      clearTimeout(persistTimer);
      persistTimer = null;
    }
    pendingPersist = null;
    sessionStorage.removeItem(name);
  },
};

export const useAiChatHistoryStore = create<AiChatHistoryState>()(
  persist(
    (set) => ({
      histories: { mock: emptyHistory(), real: emptyHistory() },
      startConversation: (mode, conversationId) =>
        set((state) => {
          const history = state.histories[mode];
          const exists = history.conversations.some((item) => item.id === conversationId);
          const nextHistory = limitHistorySize({
            conversations: exists
              ? history.conversations
              : [
                  { id: conversationId, messages: [], updatedAt: Date.now() },
                  ...history.conversations,
                ].slice(0, MAX_AI_CHAT_CONVERSATIONS),
            activeConversationId: conversationId,
          });
          return {
            histories: {
              ...state.histories,
              [mode]: nextHistory,
            },
          };
        }),
      selectConversation: (mode, conversationId) =>
        set((state) => ({
          histories: {
            ...state.histories,
            [mode]: { ...state.histories[mode], activeConversationId: conversationId },
          },
        })),
      setMessages: (mode, conversationId, messages) =>
        set((state) => {
          const history = state.histories[mode];
          const conversation = history.conversations.find((item) => item.id === conversationId);
          const nextMessages = boundMessageFields(
            typeof messages === 'function' ? messages(conversation?.messages ?? []) : messages,
          );
          const updatedConversation = { id: conversationId, messages: nextMessages, updatedAt: Date.now() };
          const nextHistory = limitHistorySize({
            conversations: [updatedConversation, ...history.conversations.filter((item) => item.id !== conversationId)].slice(
              0,
              MAX_AI_CHAT_CONVERSATIONS,
            ),
            activeConversationId: history.activeConversationId ?? conversationId,
          });
          return {
            histories: {
              ...state.histories,
              [mode]: nextHistory,
            },
          };
        }),
      clearHistories: () => set({ histories: { mock: emptyHistory(), real: emptyHistory() } }),
    }),
    {
      name: AI_CHAT_HISTORY_STORAGE_KEY,
      storage: createJSONStorage(() => throttledHistoryStorage),
      partialize: (state) => ({ histories: state.histories }),
      merge: (persisted, current) => {
        const saved = persisted as Partial<AiChatHistoryState> & { messages?: AiChatMessage[]; conversationId?: string | null };
        return {
          ...current,
          histories: {
            mock: restoreHistory(saved.histories?.mock),
            real: restoreHistory(saved.histories?.real ?? saved),
          },
        };
      },
    },
  ),
);

export const clearAiChatHistories = (): void => {
  useAiChatHistoryStore.getState().clearHistories();
  flushAiChatHistoryPersistence();
};
