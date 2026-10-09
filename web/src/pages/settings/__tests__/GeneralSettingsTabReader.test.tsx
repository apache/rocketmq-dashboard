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
import { getGeneralSettings, saveGeneralSettings } from '../../../api/settings';
import { ThemeProvider } from '../../../theme/ThemeProvider';
import { LangProvider } from '../../../i18n/LangContext';
import { LANGUAGE_STORAGE_KEY } from '../../../i18n/languagePreference';
import { GeneralSettingsTab } from '../GeneralSettingsTab';

type MockAuthState = { admin: boolean | null; userId: number | null; logout: () => void };
vi.mock('../../../stores/authStore', () => ({
  default: (selector: (state: MockAuthState) => unknown) =>
    selector({ admin: false, userId: 42, logout: vi.fn() }),
}));

vi.mock('../../../api/settings', () => ({
  createDataSource: vi.fn(),
  deleteDataSource: vi.fn(),
  getGeneralSettings: vi.fn(),
  listDataSources: vi.fn(),
  saveGeneralSettings: vi.fn(),
  testDataSource: vi.fn(),
  updateDataSource: vi.fn(),
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
          <GeneralSettingsTab />
        </ThemeProvider>
      </LangProvider>
    </App>,
  );

describe('GeneralSettingsTab reader access', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    localStorage.setItem(LANGUAGE_STORAGE_KEY, 'en');
    // The server hands redacted webhook values to readers and rejects their
    // writes with 403 (POST /api/settings/general/save is admin-only).
    vi.mocked(getGeneralSettings).mockResolvedValue({
      theme: 'system',
      compact: false,
      desktopNotify: false,
      notifySound: false,
      sessionTimeout: 30,
      requireLogin: true,
      llmProvider: 'tongyi',
      apiKeyConfigured: false,
      model: 'qwen',
      baseUrl: 'https://example.com/v1',
      dingtalkWebhook: '******',
      dingtalkSigningSecretConfigured: true,
      emailRecipients: 'ops@example.com',
      smsWebhook: '******',
      smsWebhookConfigured: true,
    });
    vi.mocked(saveGeneralSettings).mockRejectedValue({
      response: { status: 403, data: { message: 'Forbidden' } },
    });
  });

  it('applies a reader theme switch locally without a doomed settings write', async () => {
    renderTab();

    fireEvent.click(await screen.findByText('Dark'));
    // Give the doomed save round-trip (fresh GET, then POST) time to fire.
    await new Promise((resolve) => {
      setTimeout(resolve, 200);
    });

    expect(saveGeneralSettings).not.toHaveBeenCalled();
    expect(
      screen.queryByText('Failed to save settings. Please try again later.'),
    ).not.toBeInTheDocument();
  });

  it('disables the admin-only save and test buttons for readers', async () => {
    renderTab();

    await screen.findByText('Test DingTalk');

    const saveButtons = screen.getAllByRole('button', { name: 'Save settings' });
    expect(saveButtons).toHaveLength(2);
    for (const button of saveButtons) {
      expect(button).toBeDisabled();
    }
    expect(screen.getByRole('button', { name: 'Test DingTalk' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Test email' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Test SMS/Webhook' })).toBeDisabled();
  });
});
