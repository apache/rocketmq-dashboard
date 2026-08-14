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
import { createJSONStorage, persist } from 'zustand/middleware';

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

interface AiChatHistoryState {
  histories: Record<AiChatDataMode, AiChatHistory>;
  startConversation: (mode: AiChatDataMode, conversationId: string) => void;
  selectConversation: (mode: AiChatDataMode, conversationId: string) => void;
  setMessages: (
    mode: AiChatDataMode,
    conversationId: string,
    messages: AiChatMessage[] | ((messages: AiChatMessage[]) => AiChatMessage[]),
  ) => void;
}

const emptyHistory = (): AiChatHistory => ({ conversations: [], activeConversationId: null });

const restoreMessages = (messages?: AiChatMessage[]): AiChatMessage[] =>
  (messages ?? [])
    .slice(-MAX_AI_CHAT_MESSAGES)
    .map((message) => (message.pending ? { ...message, pending: false } : message));

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
  if (history?.conversations) {
    const conversations = history.conversations.slice(0, MAX_AI_CHAT_CONVERSATIONS).map((conversation) => ({
        ...conversation,
        messages: restoreMessages(conversation.messages),
      }));
    return {
      conversations,
      activeConversationId: conversations.some((item) => item.id === history.activeConversationId)
        ? history.activeConversationId ?? null
        : conversations[0]?.id ?? null,
    };
  }

  if (history?.conversationId || history?.messages?.length) {
    const id = history.conversationId ?? 'legacy-conversation';
    return {
      conversations: [{ id, messages: restoreMessages(history.messages), updatedAt: 0 }],
      activeConversationId: id,
    };
  }
  return emptyHistory();
};

export const useAiChatHistoryStore = create<AiChatHistoryState>()(
  persist(
    (set) => ({
      histories: { mock: emptyHistory(), real: emptyHistory() },
      startConversation: (mode, conversationId) =>
        set((state) => {
          const history = state.histories[mode];
          const exists = history.conversations.some((item) => item.id === conversationId);
          return {
            histories: {
              ...state.histories,
              [mode]: {
                conversations: exists
                  ? history.conversations
                  : [
                      { id: conversationId, messages: [], updatedAt: Date.now() },
                      ...history.conversations,
                    ].slice(0, MAX_AI_CHAT_CONVERSATIONS),
                activeConversationId: conversationId,
              },
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
          const nextMessages = (
            typeof messages === 'function' ? messages(conversation?.messages ?? []) : messages
          ).slice(-MAX_AI_CHAT_MESSAGES);
          const updatedConversation = { id: conversationId, messages: nextMessages, updatedAt: Date.now() };
          return {
            histories: {
              ...state.histories,
              [mode]: {
                conversations: [updatedConversation, ...history.conversations.filter((item) => item.id !== conversationId)].slice(
                  0,
                  MAX_AI_CHAT_CONVERSATIONS,
                ),
                activeConversationId: history.activeConversationId ?? conversationId,
              },
            },
          };
        }),
    }),
    {
      name: 'rocketmq-studio-ai-chat-history',
      storage: createJSONStorage(() => sessionStorage),
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
