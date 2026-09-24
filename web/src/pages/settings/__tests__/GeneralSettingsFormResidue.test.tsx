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
import userEvent from '@testing-library/user-event';
import { App } from 'antd';
import { getGeneralSettings, saveGeneralSettings } from '../../../api/settings';
import { LangProvider, useLang } from '../../../i18n/LangContext';
import { ThemeProvider } from '../../../theme/ThemeProvider';
import { GeneralSettingsTab } from '../GeneralSettingsTab';

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

vi.mock('../../../api/settings', () => ({
  createDataSource: vi.fn(),
  deleteDataSource: vi.fn(),
  getGeneralSettings: vi.fn(),
  listDataSources: vi.fn(),
  saveGeneralSettings: vi.fn(),
  testDataSource: vi.fn(),
  updateDataSource: vi.fn(),
}));

const LanguageSwitch = () => {
  const { setLang } = useLang();
  return (
    <button type="button" onClick={() => setLang('en')}>
      switch-language
    </button>
  );
};

const renderTab = () =>
  render(
    <App>
      <LangProvider>
        <LanguageSwitch />
        <ThemeProvider>
          <GeneralSettingsTab />
        </ThemeProvider>
      </LangProvider>
    </App>,
  );

describe('GeneralSettingsTab unsaved input', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    vi.mocked(saveGeneralSettings).mockResolvedValue(undefined);
    vi.mocked(getGeneralSettings).mockResolvedValue({
      theme: 'system',
      compact: false,
      desktopNotify: false,
      notifySound: false,
      sessionTimeout: 30,
      requireLogin: true,
      llmProvider: 'openai',
      apiKeyConfigured: false,
      model: 'test-model',
      baseUrl: 'https://example.test/v1',
      dingtalkWebhook: 'https://oapi.dingtalk.com/robot/send?access_token=server',
      emailRecipients: 'oncall@example.com',
    });
  });

  it('keeps unsaved notification edits when the display language changes', async () => {
    const user = userEvent.setup();
    renderTab();

    const webhook = await screen.findByDisplayValue(
      'https://oapi.dingtalk.com/robot/send?access_token=server',
    );
    await user.clear(webhook);
    await user.type(webhook, 'https://oapi.dingtalk.com/robot/send?access_token=edited');
    expect(webhook).toHaveValue('https://oapi.dingtalk.com/robot/send?access_token=edited');

    await user.click(screen.getByRole('button', { name: 'switch-language' }));

    expect(webhook).toHaveValue('https://oapi.dingtalk.com/robot/send?access_token=edited');
  }, 20_000);
});
