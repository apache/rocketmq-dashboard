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

import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from 'antd';
import { LangProvider } from '../../../i18n/LangContext';
import { useAiChatHistoryStore } from '../../../stores/aiChatHistoryStore';
import HomePage from '../index';

const navigateMock = vi.hoisted(() => vi.fn());
const llmApiMocks = vi.hoisted(() => ({
  getLlmConfig: vi.fn(),
}));

vi.mock('react-router-dom', () => ({
  useNavigate: () => navigateMock,
}));

vi.mock('../../../api/llm', () => llmApiMocks);

beforeAll(() => {
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: vi.fn().mockImplementation((query: string) => ({
      matches: false,
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    })),
  });
});

beforeEach(() => {
  vi.clearAllMocks();
  sessionStorage.clear();
  useAiChatHistoryStore.setState({
    histories: {
      mock: { conversations: [], activeConversationId: null },
      real: { conversations: [], activeConversationId: null },
    },
  });
  llmApiMocks.getLlmConfig.mockResolvedValue({
    provider: 'tongyi',
    apiBase: 'https://token-plan.cn-beijing.maas.aliyuncs.com/compatible-mode/v1',
    model: 'qwen3.8-max',
    maxTokens: 4096,
    temperature: 0.7,
    enabled: true,
    ready: true,
  });
});

const renderHome = () =>
  render(
    <App>
      <LangProvider>
        <HomePage />
      </LangProvider>
    </App>,
  );

describe('HomePage LLM models', () => {
  it('shows the fixed home model list with qwen3.8-max selected', async () => {
    renderHome();

    expect(await screen.findByText('qwen3.8-max')).toBeInTheDocument();
  });

  it('does not fetch LLM config in Mock mode', async () => {
    const { useDataModeStore } = await import('../../../stores/dataModeStore');
    useDataModeStore.getState().toggle();

    renderHome();

    expect(llmApiMocks.getLlmConfig).not.toHaveBeenCalled();
    expect(await screen.findByText('qwen3.8-max')).toBeInTheDocument();

    useDataModeStore.getState().toggle();
  });

  it('submits the selected model and engine to the AI page', async () => {
    const user = userEvent.setup();
    renderHome();
    await screen.findByText('qwen3.8-max');

    await user.type(
      screen.getByPlaceholderText('向 RocketMQ Bot 提问，全程加密、安全、可信'),
      '查看集群状态{enter}',
    );

    await waitFor(() => {
      expect(navigateMock).toHaveBeenCalledWith('/ai', {
        state: {
          prompt: '查看集群状态',
          model: 'qwen3.8-max',
          engine: 'claude-code',
          mode: 'chat',
          newConversation: true,
        },
      });
    });
  });

  it('shows only the current data mode history and opens the conversation', async () => {
    useAiChatHistoryStore.setState({
      histories: {
        mock: {
          conversations: [
            {
              id: 'mock-conversation',
              messages: [{ id: 'mock-1', role: 'user', text: 'Mock conversation' }],
              updatedAt: 1,
            },
          ],
          activeConversationId: 'mock-conversation',
        },
        real: {
          conversations: [
            {
              id: 'real-conversation',
              messages: [{ id: 'real-1', role: 'user', text: 'Real conversation' }],
              updatedAt: 1,
            },
          ],
          activeConversationId: 'real-conversation',
        },
      },
    });
    const user = userEvent.setup();
    renderHome();

    await user.click(await screen.findByRole('button', { name: 'AI 对话历史' }));
    expect(screen.getByText('Real conversation')).toBeInTheDocument();
    expect(screen.queryByText('Mock conversation')).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /^Real conversation/ }));
    expect(navigateMock).toHaveBeenCalledWith('/ai', { state: { conversationId: 'real-conversation' } });
  });

  it('lists multiple conversations instead of flattening their messages', async () => {
    useAiChatHistoryStore.setState({
      histories: {
        mock: { conversations: [], activeConversationId: null },
        real: {
          conversations: [
            {
              id: 'latest',
              messages: [{ id: 'latest-message', role: 'user', text: 'Latest conversation' }],
              updatedAt: 2,
            },
            {
              id: 'previous',
              messages: [{ id: 'previous-message', role: 'user', text: 'Previous conversation' }],
              updatedAt: 1,
            },
          ],
          activeConversationId: 'latest',
        },
      },
    });
    const user = userEvent.setup();
    renderHome();

    await user.click(await screen.findByRole('button', { name: 'AI 对话历史' }));
    expect(screen.getByRole('button', { name: /^Latest conversation/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /^Previous conversation/ })).toBeInTheDocument();
  });

  it('shows an explicit empty state when the current data mode has no history', async () => {
    const user = userEvent.setup();
    renderHome();

    await user.click(await screen.findByRole('button', { name: 'AI 对话历史' }));
    expect(screen.getByText('当前模式暂无对话记录')).toBeInTheDocument();
  });
});
