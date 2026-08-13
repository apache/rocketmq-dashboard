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
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from 'antd';
import { LangProvider } from '../../../i18n/LangContext';
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
        },
      });
    });
  });

  it('does not submit while an input method composition is being confirmed', async () => {
    renderHome();
    await screen.findByText('qwen3.8-max');
    const input = screen.getByPlaceholderText('向 RocketMQ Bot 提问，全程加密、安全、可信');

    fireEvent.change(input, { target: { value: '查看集群状态' } });
    fireEvent.keyDown(input, { key: 'Enter', isComposing: true });

    expect(navigateMock).not.toHaveBeenCalled();
  });
});
