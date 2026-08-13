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

import { App } from 'antd';
import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { LangProvider } from '../../../i18n/LangContext';
import LlmSettingsPage from '../LlmSettings';

const llmApiMocks = vi.hoisted(() => ({
  getLlmConfig: vi.fn(),
  saveLlmConfig: vi.fn(),
  testLlmConnection: vi.fn(),
  getLlmModels: vi.fn(),
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

const renderPage = () =>
  render(
    <App>
      <LangProvider>
        <LlmSettingsPage />
      </LangProvider>
    </App>,
  );

describe('LlmSettingsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    llmApiMocks.getLlmConfig.mockResolvedValue({
      provider: 'tongyi',
      apiBase: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
      model: 'qwen3.8-max',
      maxTokens: 4096,
      temperature: 0.7,
      enabled: true,
      apiKeyConfigured: true,
    });
    llmApiMocks.getLlmModels.mockResolvedValue({
      status: 0,
      data: [{ id: 'qwen3.8-max' }, { id: 'qwen-max' }],
    });
    llmApiMocks.saveLlmConfig.mockResolvedValue({ status: 0 });
    llmApiMocks.testLlmConnection.mockResolvedValue({ status: 0, msg: 'ok' });
  });

  it('loads the saved config and shows the configured-key badge', async () => {
    renderPage();

    expect(await screen.findByText('密钥已配置')).toBeInTheDocument();
    expect(screen.getByText('qwen3.8-max')).toBeInTheDocument();
  });

  it('saves without sending an apiKey when the input stays empty', async () => {
    const user = userEvent.setup();
    renderPage();

    await screen.findByText('密钥已配置');
    await user.click(screen.getByRole('button', { name: /保\s*存/ }));

    await waitFor(() => expect(llmApiMocks.saveLlmConfig).toHaveBeenCalledTimes(1));
    const payload = llmApiMocks.saveLlmConfig.mock.calls[0][0];
    expect(payload).toMatchObject({ provider: 'tongyi', model: 'qwen3.8-max' });
    expect(payload.apiKey).toBeUndefined();
  });

  it('submits the Azure deployment fields required by the backend', async () => {
    const user = userEvent.setup();
    const { container } = renderPage();

    await waitFor(() => expect(llmApiMocks.getLlmConfig).toHaveBeenCalledTimes(1));
    await user.click(screen.getAllByRole('combobox')[1]);
    await user.click(
      await screen.findByText('Azure OpenAI', { selector: '.ant-select-item-option-content' }),
    );

    const apiBase = await screen.findByLabelText('API Base URL');
    await user.type(apiBase, 'https://example.openai.azure.com/openai');
    await user.type(screen.getByLabelText('Azure Deployment Name'), 'production-gpt');
    expect(screen.getByLabelText('Azure API Version')).toHaveValue('2024-02-15-preview');
    const saveButton = container.querySelector('.ant-btn-primary');
    expect(saveButton).not.toBeNull();
    await user.click(saveButton as HTMLButtonElement);

    await waitFor(() => expect(llmApiMocks.saveLlmConfig).toHaveBeenCalledTimes(1));
    expect(llmApiMocks.saveLlmConfig.mock.calls[0][0]).toMatchObject({
      provider: 'azure',
      apiBase: 'https://example.openai.azure.com/openai',
      deploymentName: 'production-gpt',
      apiVersion: '2024-02-15-preview',
    });
    expect(llmApiMocks.saveLlmConfig.mock.calls[0][0].awsRegion).toBeUndefined();
  });

  it('ignores a connection result after the tested configuration changes', async () => {
    let resolveTest!: (result: { status: number; msg: string }) => void;
    llmApiMocks.testLlmConnection.mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          resolveTest = resolve;
        }),
    );
    const user = userEvent.setup();
    renderPage();

    await screen.findByText('密钥已配置');
    await user.click(screen.getByRole('button', { name: /测试连接/ }));
    await waitFor(() => expect(llmApiMocks.testLlmConnection).toHaveBeenCalledTimes(1));

    await user.click(screen.getAllByRole('combobox')[1]);
    await user.click(
      await screen.findByText('OpenAI', { selector: '.ant-select-item-option-content' }),
    );
    await act(async () => resolveTest({ status: 0, msg: 'old provider succeeded' }));

    expect(screen.queryByText('old provider succeeded')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: /测试连接/ })).not.toHaveClass('ant-btn-loading');
  });
});
