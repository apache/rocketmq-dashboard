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
import { executeTool, listTools } from '../../../api/ai';
import { listClusters, type ClusterInfo } from '../../../api/cluster';
import { getLlmConfig, getLlmModels } from '../../../api/llm';
import AiPage from '../index';

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

const renderPage = () =>
  render(
    <App>
      <LangProvider>
        <MemoryRouter initialEntries={['/ai']}>
          <AiPage />
        </MemoryRouter>
      </LangProvider>
    </App>,
  );

describe('AiPage tool runner', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
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

  it('records successful tool executions in recent history', async () => {
    const user = userEvent.setup();
    vi.mocked(executeTool).mockResolvedValue({
      cluster: 'cluster-a',
      status: 'READY',
    });
    renderPage();

    await user.click(screen.getByRole('button', { name: '工具' }));
    const dialog = await screen.findByRole('dialog', { name: 'AI 工具' });
    await waitFor(() => expect(listTools).toHaveBeenCalledWith('cluster-a'));
    await user.click(within(dialog).getByRole('button', { name: /执\s*行/ }));

    expect(await within(dialog).findByText('最近执行')).toBeInTheDocument();
    expect(within(dialog).getByText('成功')).toBeInTheDocument();

    const stored = JSON.parse(
      localStorage.getItem('rocketmq-studio-ai-tool-execution-history') || '[]',
    ) as Array<{ toolName: string; status: string; inputKeys: string[] }>;
    expect(stored[0]).toMatchObject({
      toolName: 'rmq.capabilities',
      status: 'SUCCESS',
      inputKeys: ['cluster'],
    });
  });

  it('records failed tool executions in recent history', async () => {
    const user = userEvent.setup();
    vi.mocked(executeTool).mockRejectedValue(new Error('broker rejected request'));
    renderPage();

    await user.click(screen.getByRole('button', { name: '工具' }));
    const dialog = await screen.findByRole('dialog', { name: 'AI 工具' });
    await waitFor(() => expect(listTools).toHaveBeenCalledWith('cluster-a'));
    await user.click(within(dialog).getByRole('button', { name: /执\s*行/ }));

    expect(await within(dialog).findByText('最近执行')).toBeInTheDocument();
    expect(within(dialog).getByText('失败')).toBeInTheDocument();

    const stored = JSON.parse(
      localStorage.getItem('rocketmq-studio-ai-tool-execution-history') || '[]',
    ) as Array<{ toolName: string; status: string; inputKeys: string[]; errorMessage?: string }>;
    expect(stored[0]).toMatchObject({
      toolName: 'rmq.capabilities',
      status: 'FAILED',
      inputKeys: ['cluster'],
      errorMessage: 'broker rejected request',
    });
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
});
