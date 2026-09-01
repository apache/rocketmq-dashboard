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
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from 'antd';
import { MemoryRouter } from 'react-router-dom';
import { LangProvider } from '../../../i18n/LangContext';
import { chatStream, executeTool, listTools, type McpTool } from '../../../api/ai';
import { listClusters, type ClusterInfo } from '../../../api/cluster';
import { getLlmConfig, getLlmModels } from '../../../api/llm';
import { useAiChatHistoryStore } from '../../../stores/aiChatHistoryStore';
import useAuthStore from '../../../stores/authStore';
import AiPage from '../index';

const dataModeMocks = vi.hoisted(() => ({ useMock: false }));

vi.mock('../../../api/ai', () => ({
  AiStreamError: class AiStreamError extends Error {},
  chatStream: vi.fn(),
  executeTool: vi.fn(),
  listTools: vi.fn(),
}));

vi.mock('../../../api/llm', () => ({
  getLlmConfig: vi.fn(),
  getLlmModels: vi.fn(),
}));

vi.mock('../../../api/cluster', () => ({
  listClusters: vi.fn(),
}));

vi.mock('../../../stores/dataModeStore', () => ({
  useDataModeStore: (selector: (state: typeof dataModeMocks) => unknown) => selector(dataModeMocks),
}));

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
  Element.prototype.scrollIntoView = vi.fn();
});

const renderPage = (state?: unknown) =>
  render(
    <App>
      <LangProvider>
        <MemoryRouter initialEntries={[state === undefined ? '/ai' : { pathname: '/ai', state }]}>
          <AiPage />
        </MemoryRouter>
      </LangProvider>
    </App>,
  );

describe('AiPage tool runner', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    dataModeMocks.useMock = false;
    sessionStorage.clear();
    useAiChatHistoryStore.setState({
      histories: {
        mock: { conversations: [], activeConversationId: null },
        real: { conversations: [], activeConversationId: null },
      },
    });
    useAuthStore.setState({ user: null, userId: null, admin: null });
    vi.mocked(getLlmConfig).mockResolvedValue({
      provider: 'openai',
      apiBase: 'https://api.openai.com/v1',
      model: 'gpt-4o',
      maxTokens: 1024,
      temperature: 0.2,
      enabled: true,
      ready: true,
    });
    vi.mocked(getLlmModels).mockResolvedValue({
      status: 0,
      data: [{ id: 'gpt-4o' }],
    });
    vi.mocked(listClusters).mockResolvedValue([
      { id: 'cluster-a', name: 'Cluster A' } as ClusterInfo,
      { id: 'cluster-b', name: 'Cluster B' } as ClusterInfo,
    ]);
    vi.mocked(listTools).mockResolvedValue([
      {
        name: 'rmq.capabilities',
        description: 'Describe cluster capabilities.',
        parameters: {
          type: 'object',
          required: ['cluster'],
          properties: { cluster: { type: 'string' } },
        },
        riskLevel: 'L1',
        permission: 'cluster:read',
      },
    ]);
  });

  it('does not load LLM configuration or tools in mock mode', async () => {
    dataModeMocks.useMock = true;
    const user = userEvent.setup();
    renderPage();

    expect(await screen.findByText('Mock 模式已禁用 AI Provider 调用')).toBeInTheDocument();
    expect(getLlmConfig).not.toHaveBeenCalled();
    expect(getLlmModels).not.toHaveBeenCalled();

    await user.click(screen.getByRole('button', { name: '工具' }));
    expect(listTools).not.toHaveBeenCalled();
    expect(listClusters).not.toHaveBeenCalled();
  });

  it('degrades for reader accounts without loading model configuration', async () => {
    useAuthStore.setState({ user: 'reader', userId: 9, admin: false });
    const user = userEvent.setup();
    renderPage();

    await waitFor(() => {
      expect(getLlmConfig).not.toHaveBeenCalled();
      expect(getLlmModels).not.toHaveBeenCalled();
    });
    await user.click(screen.getByRole('button', { name: '工具' }));
    await waitFor(() => expect(listClusters).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(listTools).toHaveBeenCalledWith('cluster-a'));
    expect(screen.queryByText('Failed to load AI configuration')).not.toBeInTheDocument();
    expect(screen.queryByText('加载 AI 配置失败')).not.toBeInTheDocument();
  });

  it('uses the mode carried from the home-page draft', async () => {
    vi.mocked(chatStream).mockResolvedValue(undefined);
    renderPage({ prompt: '检查集群状态', mode: 'diagnose' });

    await waitFor(() => {
      expect(chatStream).toHaveBeenCalledWith(
        expect.objectContaining({ message: '检查集群状态', mode: 'diagnose' }),
        expect.any(Function),
        expect.any(AbortSignal),
        expect.any(Function),
      );
    });
  });

  it('starts a new conversation when the home-page draft requests it', async () => {
    useAiChatHistoryStore.setState({
      histories: {
        mock: { conversations: [], activeConversationId: null },
        real: {
          conversations: [
            {
              id: 'previous-conversation',
              messages: [{ id: 'previous', role: 'user', text: 'Previous conversation' }],
              updatedAt: new Date(2026, 7, 13, 9, 45).getTime(),
            },
          ],
          activeConversationId: 'previous-conversation',
        },
      },
    });
    vi.mocked(chatStream).mockResolvedValue(undefined);

    renderPage({ prompt: 'New conversation', newConversation: true });

    await waitFor(() => {
      expect(chatStream).toHaveBeenCalledWith(
        expect.objectContaining({ message: 'New conversation' }),
        expect.any(Function),
        expect.any(AbortSignal),
        expect.any(Function),
      );
    });
    expect(useAiChatHistoryStore.getState().histories.real.conversations).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          messages: expect.arrayContaining([
            expect.objectContaining({ text: 'Previous conversation' }),
          ]),
        }),
      ]),
    );
  });

  it('uses the conversation update time for legacy messages without message timestamps', async () => {
    useAiChatHistoryStore.setState({
      histories: {
        mock: { conversations: [], activeConversationId: null },
        real: {
          conversations: [
            {
              id: 'previous-conversation',
              messages: [{ id: 'previous', role: 'user', text: 'Previous conversation' }],
              updatedAt: new Date(2026, 7, 13, 9, 45).getTime(),
            },
          ],
          activeConversationId: null,
        },
      },
    });

    renderPage({ conversationId: 'previous-conversation' });

    expect(await screen.findByText('Previous conversation')).toBeInTheDocument();
    expect(screen.getByText('09:45')).toBeInTheDocument();
    expect(chatStream).not.toHaveBeenCalled();
  });

  it('switches conversations from the AI-page history drawer without sending a request', async () => {
    const now = Date.now();
    useAiChatHistoryStore.setState({
      histories: {
        mock: { conversations: [], activeConversationId: null },
        real: {
          conversations: [
            {
              id: 'active',
              messages: [{ id: 'active-message', role: 'user', text: 'Active conversation' }],
              updatedAt: now,
            },
            {
              id: 'previous',
              messages: [{ id: 'previous-message', role: 'user', text: 'Previous conversation' }],
              updatedAt: now - 5 * 60_000,
            },
          ],
          activeConversationId: 'active',
        },
      },
    });
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole('button', { name: 'AI 对话历史' }));
    expect(screen.getByText('刚刚')).toBeInTheDocument();
    expect(screen.getByText('5 分钟前')).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: /^Previous conversation5 分钟前$/ }),
    ).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /^Previous conversation/ }));

    expect(
      await screen.findByText('Previous conversation', { selector: 'div[style*="max-width"]' }),
    ).toBeInTheDocument();
    expect(useAiChatHistoryStore.getState().histories.real.activeConversationId).toBe('previous');
    expect(chatStream).not.toHaveBeenCalled();
  });

  it('stops an in-flight response before switching conversations', async () => {
    useAiChatHistoryStore.setState({
      histories: {
        mock: { conversations: [], activeConversationId: null },
        real: {
          conversations: [
            {
              id: 'previous',
              messages: [{ id: 'previous-message', role: 'user', text: 'Previous conversation' }],
              updatedAt: Date.now() - 60_000,
            },
          ],
          activeConversationId: 'previous',
        },
      },
    });
    let requestSignal: AbortSignal | undefined;
    vi.mocked(chatStream).mockImplementation(
      (_request, _onChunk, signal) =>
        new Promise<void>((resolve) => {
          requestSignal = signal;
          if (signal) {
            signal.addEventListener('abort', () => resolve());
          } else {
            resolve();
          }
        }),
    );
    const user = userEvent.setup();
    renderPage({ prompt: 'Start streaming', newConversation: true });

    await waitFor(() => expect(requestSignal).toBeDefined());
    await user.click(screen.getByRole('button', { name: 'AI 对话历史' }));
    const historyDrawer = await screen.findByRole('dialog', { name: 'AI 对话历史' });
    await user.click(within(historyDrawer).getByRole('button', { name: /^Previous conversation/ }));

    expect(requestSignal?.aborted).toBe(true);
    expect(useAiChatHistoryStore.getState().histories.real.activeConversationId).toBe('previous');
    await waitFor(() =>
      expect(screen.queryByRole('button', { name: '停止' })).not.toBeInTheDocument(),
    );
  });

  it('does not send while an input method composition is being confirmed', async () => {
    renderPage();
    const input = await screen.findByPlaceholderText(
      '输入你的问题或指令，例如：查看集群状态、创建 Topic、诊断消费延迟...',
    );
    await waitFor(() => expect(getLlmModels).toHaveBeenCalled());

    fireEvent.change(input, { target: { value: '检查集群状态' } });
    fireEvent.keyDown(input, { key: 'Enter', isComposing: true });

    expect(chatStream).not.toHaveBeenCalled();
  });

  it('deduplicates prompt submissions before loading state is rendered', async () => {
    vi.mocked(chatStream).mockReturnValue(new Promise(() => {}));
    renderPage();
    const input = await screen.findByPlaceholderText(
      '输入你的问题或指令，例如：查看集群状态、创建 Topic、诊断消费延迟...',
    );
    await waitFor(() => expect(getLlmModels).toHaveBeenCalled());
    fireEvent.change(input, { target: { value: '检查集群状态' } });

    await act(async () => {
      input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
      input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
    });

    expect(chatStream).toHaveBeenCalledTimes(1);
  });

  it('loads the catalog, creates a schema template, and renders structured output', async () => {
    const user = userEvent.setup();
    vi.mocked(executeTool).mockResolvedValue({
      cluster: 'cluster-a',
      capabilities: ['GRPC'],
    });
    renderPage();

    await user.click(screen.getByRole('button', { name: '工具' }));

    const dialog = await screen.findByRole('dialog', { name: 'AI 工具' });
    await waitFor(() => expect(listTools).toHaveBeenCalledWith('cluster-a'));
    expect(within(dialog).getByText('Cluster A')).toBeInTheDocument();
    expect(within(dialog).getByText('rmq.capabilities')).toBeInTheDocument();
    expect(within(dialog).getByText('L1')).toBeInTheDocument();
    expect(within(dialog).getByText('cluster:read')).toBeInTheDocument();

    const input = within(dialog).getByRole('textbox', { name: '工具参数 JSON' });
    expect(input).toHaveValue('{\n  "cluster": "cluster-a"\n}');
    fireEvent.change(input, { target: { value: '{"cluster":"cluster-a"}' } });
    await user.click(within(dialog).getByRole('button', { name: /执\s*行/ }));

    await waitFor(() => {
      expect(executeTool).toHaveBeenCalledWith('rmq.capabilities', {
        cluster: 'cluster-a',
      });
    });
    expect(await within(dialog).findByTestId('tool-result')).toHaveTextContent('"capabilities": [');
    expect(within(dialog).getByTestId('tool-result')).toHaveTextContent('"GRPC"');
  });

  it('ignores an older tool catalog after the cluster changes', async () => {
    const oldTools = [{ name: 'rmq.old', description: 'old', parameters: {} }];
    const latestTools = [{ name: 'rmq.latest', description: 'latest', parameters: {} }];
    let resolveOld!: (value: typeof oldTools) => void;
    let resolveLatest!: (value: typeof latestTools) => void;
    vi.mocked(listTools)
      .mockReturnValueOnce(
        new Promise((resolve) => {
          resolveOld = resolve;
        }),
      )
      .mockReturnValueOnce(
        new Promise((resolve) => {
          resolveLatest = resolve;
        }),
      );
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole('button', { name: '工具' }));
    const dialog = await screen.findByRole('dialog', { name: 'AI 工具' });
    await waitFor(() => expect(listTools).toHaveBeenCalledWith('cluster-a'));
    await user.click(within(dialog).getByRole('combobox', { name: '选择集群' }));
    await user.click(
      await screen.findByText('Cluster B', { selector: '.ant-select-item-option-content' }),
    );
    await waitFor(() => expect(listTools).toHaveBeenCalledWith('cluster-b'));

    await act(async () => resolveLatest(latestTools));
    expect(await within(dialog).findByText('rmq.latest')).toBeInTheDocument();
    await act(async () => resolveOld(oldTools));
    expect(within(dialog).getByText('rmq.latest')).toBeInTheDocument();
    expect(within(dialog).queryByText('rmq.old')).not.toBeInTheDocument();
  });

  it('reloads the available tools and template when the cluster changes', async () => {
    const user = userEvent.setup();
    vi.mocked(listTools).mockImplementation(async (cluster) => [
      {
        name: `rmq.tool.${cluster}`,
        description: `Tool for ${cluster}`,
        parameters: {
          type: 'object',
          required: ['cluster'],
          properties: { cluster: { type: 'string' } },
        },
      },
    ]);
    renderPage();

    await user.click(screen.getByRole('button', { name: '工具' }));
    const dialog = await screen.findByRole('dialog', { name: 'AI 工具' });
    await waitFor(() => expect(listTools).toHaveBeenCalledWith('cluster-a'));

    const clusterSelect = within(dialog).getByRole('combobox', { name: '选择集群' });
    await user.click(clusterSelect);
    await user.click(
      await screen.findByText('Cluster B', { selector: '.ant-select-item-option-content' }),
    );

    await waitFor(() => expect(listTools).toHaveBeenCalledWith('cluster-b'));
    expect(within(dialog).getByText('rmq.tool.cluster-b')).toBeInTheDocument();
    expect(within(dialog).getByRole('textbox', { name: '工具参数 JSON' })).toHaveValue(
      '{\n  "cluster": "cluster-b"\n}',
    );
  });

  it('rejects input that is not a JSON object', async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole('button', { name: '工具' }));
    const dialog = await screen.findByRole('dialog', { name: 'AI 工具' });
    const input = await within(dialog).findByRole('textbox', { name: '工具参数 JSON' });
    fireEvent.change(input, { target: { value: '[]' } });
    await user.click(within(dialog).getByRole('button', { name: /执\s*行/ }));

    expect(await screen.findByText('工具参数必须是有效的 JSON 对象')).toBeInTheDocument();
    expect(executeTool).not.toHaveBeenCalled();
  });

  it('treats a non-array tool catalog payload as an empty catalog', async () => {
    const user = userEvent.setup();
    vi.mocked(listTools).mockResolvedValue(null as unknown as McpTool[]);
    renderPage();

    await user.click(screen.getByRole('button', { name: '工具' }));
    const dialog = await screen.findByRole('dialog', { name: 'AI 工具' });
    await waitFor(() => expect(listTools).toHaveBeenCalledWith('cluster-a'));

    expect(dialog).toBeInTheDocument();
    expect(screen.queryByText('AI 工具目录加载失败')).not.toBeInTheDocument();
  });
});