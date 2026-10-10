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
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { LangProvider } from '../../../i18n/LangContext';
import { LANGUAGE_STORAGE_KEY } from '../../../i18n/languagePreference';
import * as dashboardService from '../../../services/dashboardService';
import * as instanceService from '../../../services/instanceService';
import DashboardPage from '../dashboard';
import HomePage from '../index';

vi.mock('../../../services/dashboardService', () => ({ getDashboard: vi.fn() }));
vi.mock('../../../services/instanceService', () => ({ listInstances: vi.fn() }));

const navigateMock = vi.hoisted(() => vi.fn());

vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router-dom')>();
  return { ...actual, useNavigate: () => navigateMock };
});

const llmApiMocks = vi.hoisted(() => ({ getLlmConfig: vi.fn() }));

vi.mock('../../../api/llm', () => llmApiMocks);

const renderInEnglish = (ui: React.ReactElement) => {
  localStorage.setItem(LANGUAGE_STORAGE_KEY, 'en');
  render(
    <App>
      <LangProvider>
        <MemoryRouter>{ui}</MemoryRouter>
      </LangProvider>
    </App>,
  );
};

const chinese = /[\u4e00-\u9fff]/;

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
  localStorage.clear();
  vi.mocked(instanceService.listInstances).mockResolvedValue([]);
  llmApiMocks.getLlmConfig.mockResolvedValue({
    provider: 'tongyi',
    apiBase: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
    model: 'qwen3.8-max',
    maxTokens: 4096,
    temperature: 0.7,
    enabled: true,
    ready: true,
  });
});

describe('Home dashboard localization', () => {
  it('renders the dashboard load failure alert in English', async () => {
    vi.mocked(dashboardService.getDashboard).mockRejectedValue(new Error('unavailable'));

    renderInEnglish(<DashboardPage />);

    const alert = await screen.findByRole('alert');

    expect(alert).toHaveTextContent('Failed to load the dashboard');
    expect(alert).toHaveTextContent(
      'Could not load the cluster overview. Check your network connection and try again.',
    );
    expect(screen.getByRole('button', { name: 'Retry' })).toBeInTheDocument();
    expect(alert.textContent ?? '').not.toMatch(chinese);
  });

  it('renders the home footer version line in English', async () => {
    renderInEnglish(<HomePage />);
    await screen.findByText('qwen3.8-max');

    const versionLine = screen.getByText(/Version \d{4}-\d{2}-\d{2} \d{2}:\d{2} build\(/u);

    expect(versionLine).toBeInTheDocument();
    expect(versionLine.textContent ?? '').not.toMatch(chinese);
  });
});
