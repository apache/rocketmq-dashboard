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
import { render, screen } from '@testing-library/react';
import { App } from 'antd';
import { getLlmConfig, getLlmModels } from '../../../api/llm';
import { ThemeProvider } from '../../../theme/ThemeProvider';
import { LangProvider } from '../../../i18n/LangContext';
import { LANGUAGE_STORAGE_KEY } from '../../../i18n/languagePreference';
import { AiAssistantTab } from '../AiAssistantTab';

type MockAuthState = { admin: boolean | null; userId: number | null; logout: () => void };
const authState = vi.hoisted(() => ({
  admin: false as boolean | null,
  userId: 42 as number | null,
  logout: vi.fn(),
}));
vi.mock('../../../stores/authStore', () => ({
  default: (selector: (state: MockAuthState) => unknown) => selector(authState),
}));

vi.mock('../../../api/llm', () => ({
  getLlmConfig: vi.fn(),
  getLlmModels: vi.fn(),
  saveLlmConfig: vi.fn(),
  testLlmConnection: vi.fn(),
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
});

const renderTab = () =>
  render(
    <App>
      <LangProvider>
        <ThemeProvider>
          <AiAssistantTab />
        </ThemeProvider>
      </LangProvider>
    </App>,
  );

describe('AiAssistantTab reader access', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    localStorage.setItem(LANGUAGE_STORAGE_KEY, 'en');
    vi.mocked(getLlmModels).mockResolvedValue({ status: 0, data: [] });
    authState.admin = false;
    authState.userId = 42;
  });

  it('disables the admin-only save and test controls for readers', async () => {
    // The whole LLM configuration surface is admin-only: GET /api/llm/config is an admin-only
    // read, so a reader lands on the load error, and POST /api/llm/config rejects the write with
    // 403 (AuthInterceptor). Neither button can ever complete for this role.
    vi.mocked(getLlmConfig).mockRejectedValue({
      response: { status: 403, data: { message: 'Forbidden' } },
    });

    renderTab();

    // The load error is what a reader always sees here (the GET is admin-only too), and it is also
    // the point at which the form is out of its loading state.
    await screen.findByText('Failed to load AI config');
    expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Test connection' })).toBeDisabled();
  });

  it('keeps the save and test controls usable for an admin', async () => {
    authState.admin = true;
    vi.mocked(getLlmConfig).mockResolvedValue({
      provider: 'openai',
      apiBase: 'https://example.com/v1',
      model: 'gpt-4o-mini',
      apiKeyConfigured: true,
      maxTokens: 4096,
      temperature: 0.7,
      enabled: true,
    });

    renderTab();

    // The clear-key control renders only once the loaded config reports a stored key, so awaiting it
    // is also the wait for the load to settle (loading alone disables both buttons).
    expect(await screen.findByRole('button', { name: 'Clear key' })).toBeEnabled();
    expect(screen.getByRole('button', { name: 'Save' })).toBeEnabled();
    expect(screen.getByRole('button', { name: 'Test connection' })).toBeEnabled();
  });
});
