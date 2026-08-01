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
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { StrictMode } from 'react';
import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import type { LlmModelsResult } from '../../../api/llm';
import { LangProvider } from '../../../i18n/LangContext';
import LlmSettingsPage from '../LlmSettings';

const apiMocks = vi.hoisted(() => ({
  getLlmConfig: vi.fn(),
  getLlmModels: vi.fn(),
  saveLlmConfig: vi.fn(),
  testLlmConnection: vi.fn(),
}));

vi.mock('../../../api/llm', () => apiMocks);

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

const createDeferred = <T,>() => {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, resolve, reject };
};

const OPENAI_CONFIG = {
  provider: 'openai',
  apiBase: 'https://api.openai.com/v1',
  model: 'gpt-4o',
  maxTokens: 4096,
  temperature: 0.7,
  enabled: true,
  apiKeyConfigured: true,
};

const renderPage = (strict = false) => {
  const page = (
    <App>
      <LangProvider>
        <LlmSettingsPage />
      </LangProvider>
    </App>
  );
  return render(strict ? <StrictMode>{page}</StrictMode> : page);
};

describe('LlmSettingsPage async request ownership', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    apiMocks.getLlmConfig.mockResolvedValue(OPENAI_CONFIG);
    apiMocks.getLlmModels.mockResolvedValue({
      status: 0,
      data: [{ id: 'gpt-4o' }],
    });
    apiMocks.saveLlmConfig.mockResolvedValue({ status: 0 });
    apiMocks.testLlmConnection.mockResolvedValue({ status: 0 });
  });

  it('does not replace a newly selected provider model list with an older response', async () => {
    const oldProviderModels = createDeferred<LlmModelsResult>();
    apiMocks.getLlmModels.mockReturnValue(oldProviderModels.promise);
    const user = userEvent.setup();
    renderPage();

    await waitFor(() => expect(apiMocks.getLlmModels).toHaveBeenCalledTimes(1));
    await user.click(screen.getByText('DeepSeek', { selector: 'div' }));
    expect(await screen.findByText('deepseek-chat')).toBeInTheDocument();

    await act(async () => {
      oldProviderModels.resolve({
        status: 0,
        data: [{ id: 'openai-only-late-model' }],
      });
    });

    await user.click(screen.getByRole('combobox'));
    expect(
      screen.queryByText('openai-only-late-model', {
        selector: '.ant-select-item-option-content',
      }),
    ).not.toBeInTheDocument();
    expect(
      await screen.findByText('deepseek-reasoner', {
        selector: '.ant-select-item-option-content',
      }),
    ).toBeInTheDocument();
  });

  it('keeps the model selector loading while the provider model request is pending', async () => {
    const providerModels = createDeferred<LlmModelsResult>();
    apiMocks.getLlmModels.mockReturnValue(providerModels.promise);
    renderPage();

    await waitFor(() => expect(apiMocks.getLlmModels).toHaveBeenCalledTimes(1));

    expect(screen.getByRole('combobox').closest('.ant-select')).toHaveClass('ant-select-loading');
  });

  it('does not let a delayed initial config replace a provider selected after request start', async () => {
    const initialConfig = createDeferred<typeof OPENAI_CONFIG>();
    apiMocks.getLlmConfig.mockReturnValueOnce(initialConfig.promise);
    renderPage();

    expect(apiMocks.getLlmConfig).toHaveBeenCalledTimes(1);
    fireEvent.click(screen.getByText('DeepSeek', { selector: 'div' }));
    expect(screen.getByRole('textbox', { name: 'API Base URL' })).toHaveValue(
      'https://api.deepseek.com/v1',
    );

    await act(async () => {
      initialConfig.resolve(OPENAI_CONFIG);
    });

    expect(screen.getByRole('textbox', { name: 'API Base URL' })).toHaveValue(
      'https://api.deepseek.com/v1',
    );
    expect(
      screen.getByText('DeepSeek', { selector: 'div' }).parentElement?.parentElement,
    ).toHaveStyle('border: 2px solid rgb(77, 107, 254)');
    expect(apiMocks.getLlmModels).not.toHaveBeenCalled();
  });

  it('does not start an old-provider model request after an earlier connection test completes', async () => {
    const oldProviderTest = createDeferred<{ status: number }>();
    apiMocks.testLlmConnection.mockReturnValue(oldProviderTest.promise);
    apiMocks.getLlmModels
      .mockResolvedValueOnce({ status: 0, data: [{ id: 'initial-openai-model' }] })
      .mockResolvedValueOnce({ status: 0, data: [{ id: 'late-openai-model' }] });
    const user = userEvent.setup();
    renderPage();

    await waitFor(() => expect(apiMocks.getLlmModels).toHaveBeenCalledTimes(1));
    await user.click(screen.getByRole('button', { name: '连接测试' }));
    await waitFor(() => expect(apiMocks.testLlmConnection).toHaveBeenCalledTimes(1));
    await user.click(screen.getByText('DeepSeek', { selector: 'div' }));
    expect(await screen.findByText('deepseek-chat')).toBeInTheDocument();

    await act(async () => {
      oldProviderTest.resolve({ status: 0 });
    });
    await waitFor(() => expect(apiMocks.saveLlmConfig).toHaveBeenCalledTimes(1));

    await user.click(screen.getByRole('combobox'));
    expect(
      screen.queryByText('late-openai-model', {
        selector: '.ant-select-item-option-content',
      }),
    ).not.toBeInTheDocument();
    expect(
      await screen.findByText('deepseek-reasoner', {
        selector: '.ant-select-item-option-content',
      }),
    ).toBeInTheDocument();
    expect(apiMocks.getLlmModels).toHaveBeenCalledTimes(1);
  });

  it('ignores configuration from the discarded StrictMode lifecycle', async () => {
    const discardedConfig = createDeferred<typeof OPENAI_CONFIG>();
    apiMocks.getLlmConfig
      .mockReturnValueOnce(discardedConfig.promise)
      .mockResolvedValue(OPENAI_CONFIG);
    renderPage(true);

    await waitFor(() => expect(apiMocks.getLlmModels).toHaveBeenCalledTimes(1));

    await act(async () => {
      discardedConfig.resolve({
        ...OPENAI_CONFIG,
        provider: 'deepseek',
        apiBase: 'https://api.deepseek.com/v1',
        model: 'deepseek-chat',
      });
    });

    expect(apiMocks.getLlmModels).toHaveBeenCalledTimes(1);
    expect(screen.getByRole('textbox', { name: 'API Base URL' })).toHaveValue(
      'https://api.openai.com/v1',
    );
    expect(
      screen.getByText('OpenAI', { selector: 'div' }).parentElement?.parentElement,
    ).toHaveStyle('border: 2px solid rgb(16, 163, 127)');
  });
});
