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

import { StrictMode } from 'react';
import { App } from 'antd';
import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { LangProvider } from '../../../i18n/LangContext';
import type { LlmTestResult } from '../../../api/llm';
import LlmSettingsPage from '../LlmSettings';

const llmApiMocks = vi.hoisted(() => ({
  getLlmConfig: vi.fn(),
  saveLlmConfig: vi.fn(),
  testLlmConnection: vi.fn(),
  getLlmModels: vi.fn(),
}));

vi.mock('../../../api/llm', () => llmApiMocks);

interface Deferred<T> {
  promise: Promise<T>;
  resolve: (value: T) => void;
  reject: (reason?: unknown) => void;
}

const deferred = <T,>(): Deferred<T> => {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((promiseResolve, promiseReject) => {
    resolve = promiseResolve;
    reject = promiseReject;
  });
  return { promise, resolve, reject };
};

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

const OPENAI_CONFIG = {
  provider: 'openai',
  engine: 'http',
  apiBase: 'https://api.openai.com/v1',
  model: 'gpt-4o',
  maxTokens: 4096,
  temperature: 0.7,
  enabled: true,
  apiKeyConfigured: false,
};

const renderPage = async () => {
  render(
    <StrictMode>
      <App>
        <LangProvider>
          <LlmSettingsPage />
        </LangProvider>
      </App>
    </StrictMode>,
  );
  await screen.findByDisplayValue(OPENAI_CONFIG.apiBase);
};

const testButton = () => screen.getByRole('button', { name: /测\s*试\s*连\s*接/ });
const saveButton = () => screen.getByRole('button', { name: /保\s*存/ });

describe('LlmSettingsPage operation ownership', () => {
  beforeEach(() => {
    Object.values(llmApiMocks).forEach((mock) => mock.mockReset());
    llmApiMocks.getLlmConfig.mockResolvedValue(OPENAI_CONFIG);
    llmApiMocks.getLlmModels.mockResolvedValue({
      status: 0,
      data: [{ id: 'gpt-4o' }, { id: 'gpt-4.1' }],
    });
    llmApiMocks.saveLlmConfig.mockResolvedValue({ status: 0 });
    llmApiMocks.testLlmConnection.mockResolvedValue({ status: 0, msg: 'current result' });
  });

  it('ignores a test result after the provider changes', async () => {
    const user = userEvent.setup();
    const request = deferred<LlmTestResult>();
    llmApiMocks.testLlmConnection.mockReturnValueOnce(request.promise);
    await renderPage();

    await user.click(testButton());
    await waitFor(() => expect(llmApiMocks.testLlmConnection).toHaveBeenCalledTimes(1));
    expect(testButton()).toHaveClass('ant-btn-loading');

    await user.click(screen.getByRole('combobox', { name: '模型服务商' }));
    await user.click(
      await screen.findByText('DeepSeek', { selector: '.ant-select-item-option-content' }),
    );
    expect(screen.getByRole('textbox', { name: 'API Base URL' })).toHaveValue(
      'https://api.deepseek.com/v1',
    );

    await act(async () => {
      request.resolve({ status: 0, msg: 'stale provider result' });
      await request.promise;
    });

    expect(screen.queryByText('stale provider result')).not.toBeInTheDocument();
    expect(llmApiMocks.saveLlmConfig).not.toHaveBeenCalled();
  });

  it('ignores a test result after the execution engine changes', async () => {
    const user = userEvent.setup();
    const request = deferred<LlmTestResult>();
    llmApiMocks.testLlmConnection.mockReturnValueOnce(request.promise);
    await renderPage();

    await user.click(testButton());
    await waitFor(() => expect(llmApiMocks.testLlmConnection).toHaveBeenCalledTimes(1));
    await user.click(screen.getByRole('combobox', { name: '执行引擎' }));
    await user.click(
      await screen.findByText('Qoder CLI', { selector: '.ant-select-item-option-content' }),
    );

    await act(async () => {
      request.resolve({ status: 0, msg: 'stale engine result' });
      await request.promise;
    });

    expect(screen.queryByText('stale engine result')).not.toBeInTheDocument();
  });

  it('does not let an older test clear the loading state of a newer test', async () => {
    const user = userEvent.setup();
    const firstRequest = deferred<LlmTestResult>();
    const secondRequest = deferred<LlmTestResult>();
    llmApiMocks.testLlmConnection
      .mockReturnValueOnce(firstRequest.promise)
      .mockReturnValueOnce(secondRequest.promise);
    await renderPage();

    await user.click(testButton());
    await waitFor(() => expect(llmApiMocks.testLlmConnection).toHaveBeenCalledTimes(1));
    const apiKeyInput = screen.getByLabelText(/^API Key$/);
    await user.type(apiKeyInput, 'replacement-key');
    expect(testButton()).not.toHaveClass('ant-btn-loading');

    await user.click(testButton());
    await waitFor(() => expect(llmApiMocks.testLlmConnection).toHaveBeenCalledTimes(2));
    expect(testButton()).toHaveClass('ant-btn-loading');

    await act(async () => {
      firstRequest.resolve({ status: 0, msg: 'older result' });
      await firstRequest.promise;
    });
    expect(testButton()).toHaveClass('ant-btn-loading');
    expect(screen.queryByText('older result')).not.toBeInTheDocument();

    await act(async () => {
      secondRequest.resolve({ status: 0, msg: 'newer result' });
      await secondRequest.promise;
    });
    expect(await screen.findByText('newer result')).toBeInTheDocument();
    expect(testButton()).not.toHaveClass('ant-btn-loading');
  });

  it('lets an explicit save supersede a pending connection test', async () => {
    const user = userEvent.setup();
    const request = deferred<LlmTestResult>();
    llmApiMocks.testLlmConnection.mockReturnValueOnce(request.promise);
    await renderPage();

    await user.click(testButton());
    await waitFor(() => expect(llmApiMocks.testLlmConnection).toHaveBeenCalledTimes(1));
    await user.click(saveButton());
    await waitFor(() => expect(llmApiMocks.saveLlmConfig).toHaveBeenCalledTimes(1));

    await act(async () => {
      request.resolve({ status: 0, msg: 'stale test after save' });
      await request.promise;
    });

    expect(screen.queryByText('stale test after save')).not.toBeInTheDocument();
    expect(llmApiMocks.saveLlmConfig).toHaveBeenCalledTimes(1);
  });

  it('does not let a stale save clear a newly edited API key', async () => {
    const user = userEvent.setup();
    const request = deferred<LlmTestResult>();
    llmApiMocks.saveLlmConfig.mockReturnValueOnce(request.promise);
    await renderPage();

    const apiKeyInput = screen.getByLabelText(/^API Key$/);
    await user.type(apiKeyInput, 'submitted-key');
    await user.click(saveButton());
    await waitFor(() => expect(llmApiMocks.saveLlmConfig).toHaveBeenCalledTimes(1));
    expect(testButton()).toBeDisabled();

    await user.clear(apiKeyInput);
    await user.type(apiKeyInput, 'replacement-key');
    await act(async () => {
      request.resolve({ status: 0 });
      await request.promise;
    });

    expect(apiKeyInput).toHaveValue('replacement-key');
    expect(screen.queryByText('密钥已配置')).not.toBeInTheDocument();
    expect(screen.queryByText('保存成功')).not.toBeInTheDocument();
    expect(saveButton()).not.toHaveClass('ant-btn-loading');
    expect(testButton()).not.toBeDisabled();
  });

  it('ignores a test error after the form changes', async () => {
    const user = userEvent.setup();
    const request = deferred<LlmTestResult>();
    llmApiMocks.testLlmConnection.mockReturnValueOnce(request.promise);
    await renderPage();

    await user.click(testButton());
    await waitFor(() => expect(llmApiMocks.testLlmConnection).toHaveBeenCalledTimes(1));
    const apiBaseInput = screen.getByRole('textbox', { name: 'API Base URL' });
    await user.clear(apiBaseInput);
    await user.type(apiBaseInput, 'https://replacement.example/v1');

    await act(async () => {
      request.reject(new Error('stale request failure'));
      await expect(request.promise).rejects.toThrow('stale request failure');
    });

    expect(apiBaseInput).toHaveValue('https://replacement.example/v1');
    expect(screen.queryByText('连接测试请求失败，请稍后重试')).not.toBeInTheDocument();
  });

  it('shows a current test result without saving or clearing the API key', async () => {
    const user = userEvent.setup();
    await renderPage();

    const apiKeyInput = screen.getByLabelText(/^API Key$/);
    await user.type(apiKeyInput, 'test-only-key');
    await user.click(testButton());

    expect(await screen.findByText('current result')).toBeInTheDocument();
    expect(apiKeyInput).toHaveValue('test-only-key');
    expect(llmApiMocks.saveLlmConfig).not.toHaveBeenCalled();
  });

  it('keeps the current explicit-save behavior', async () => {
    const user = userEvent.setup();
    await renderPage();

    const apiKeyInput = screen.getByLabelText(/^API Key$/);
    await user.type(apiKeyInput, 'saved-key');
    await user.click(saveButton());

    expect(await screen.findByText('密钥已配置')).toBeInTheDocument();
    expect(await screen.findByText('保存成功')).toBeInTheDocument();
    expect(apiKeyInput).toHaveValue('');
    expect(llmApiMocks.saveLlmConfig).toHaveBeenCalledWith(
      expect.objectContaining({
        provider: 'openai',
        engine: 'http',
        apiKey: 'saved-key',
        enabled: true,
      }),
    );
  });
});
