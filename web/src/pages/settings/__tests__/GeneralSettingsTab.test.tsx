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

describe('GeneralSettingsTab', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
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
    });
  });

  it('ignores a duplicate submit while a save is in flight', async () => {
    vi.mocked(saveGeneralSettings).mockImplementation(() => new Promise(() => {}));
    renderTab();

    const saveButtons = await screen.findAllByRole('button', { name: '保存设置' });
    await waitFor(() => expect(saveButtons[0]).toBeEnabled());
    const form = saveButtons[0].closest('form');
    expect(form).not.toBeNull();

    fireEvent.submit(form!);
    fireEvent.submit(form!);

    await waitFor(() => expect(saveGeneralSettings).toHaveBeenCalledTimes(1));
  });

  it('keeps the session timeout fields bound when displaying their unit', async () => {
    renderTab();

    const timeoutInputs = await screen.findAllByDisplayValue('30');
    expect(timeoutInputs).toHaveLength(2);
    expect(screen.getByLabelText('会话超时单位')).toHaveValue('分钟');
    expect(screen.getByLabelText('会话空闲超时单位')).toHaveValue('分钟');
  });

  it('saves the session idle timeout from the security form', async () => {
    vi.mocked(saveGeneralSettings).mockResolvedValue();
    vi.mocked(getGeneralSettings).mockResolvedValue({
      theme: 'system',
      compact: false,
      desktopNotify: false,
      notifySound: false,
      sessionTimeout: 30,
      sessionIdleTimeout: 15,
      requireLogin: true,
      llmProvider: 'openai',
      apiKeyConfigured: false,
      model: 'test-model',
      baseUrl: 'https://example.test/v1',
    });
    renderTab();

    const idleInput = await waitFor(() => {
      const input = document.getElementById('sessionIdleTimeout') as HTMLInputElement | null;
      expect(input).not.toBeNull();
      return input!;
    });
    expect(idleInput).toHaveValue('15');
    fireEvent.change(idleInput, { target: { value: '45' } });

    const saveButtons = await screen.findAllByRole('button', { name: '保存设置' });
    fireEvent.submit(saveButtons[0].closest('form')!);

    await waitFor(() =>
      expect(saveGeneralSettings).toHaveBeenCalledWith(
        expect.objectContaining({ sessionTimeout: 30, sessionIdleTimeout: 45 }),
      ),
    );
  });

  it('defaults the idle timeout to the server default when the field is absent', async () => {
    renderTab();

    const idleInput = await waitFor(() => {
      const input = document.getElementById('sessionIdleTimeout') as HTMLInputElement | null;
      expect(input).not.toBeNull();
      return input!;
    });
    expect(idleInput).toHaveValue('30');
  });

  it('shows appearance preferences but keeps desktop notification options hidden', async () => {
    vi.mocked(saveGeneralSettings).mockResolvedValue();
    renderTab();

    const saveButtons = await screen.findAllByRole('button', { name: '保存设置' });
    expect(screen.getByText('主题模式')).toBeInTheDocument();
    expect(screen.getByText('紧凑模式')).toBeInTheDocument();
    expect(screen.queryByText('桌面通知')).not.toBeInTheDocument();
    expect(screen.queryByText('通知声音')).not.toBeInTheDocument();

    fireEvent.submit(saveButtons[0].closest('form')!);

    await waitFor(() =>
      expect(saveGeneralSettings).toHaveBeenCalledWith(
        expect.objectContaining({
          theme: 'system',
          compact: false,
          desktopNotify: false,
          notifySound: false,
          sessionTimeout: 30,
        }),
      ),
    );
  });

  it('renders section labels in English when English is selected', async () => {
    localStorage.setItem(LANGUAGE_STORAGE_KEY, 'en');
    renderTab();

    expect(await screen.findByText('Appearance preferences')).toBeInTheDocument();
    expect(screen.getByText('Session timeout')).toBeInTheDocument();
  });

  it('saves immediately when the theme mode preference changes', async () => {
    vi.mocked(saveGeneralSettings).mockResolvedValue();
    const user = userEvent.setup();
    renderTab();

    await screen.findByText('主题模式');
    await user.click(screen.getByText('深色'));

    await waitFor(() =>
      expect(saveGeneralSettings).toHaveBeenCalledWith(
        expect.objectContaining({ theme: 'dark', sessionTimeout: 30 }),
      ),
    );
    expect(localStorage.getItem('rocketmq-studio-theme')).toBe('dark');
    // The fresh read that guards unmanaged fields must not revert the visible preference.
    await waitFor(() =>
      expect(screen.getByText('深色').closest('.ant-segmented-item')).toHaveClass(
        'ant-segmented-item-selected',
      ),
    );
  });

  it('saves llm fields from a fresh read instead of the mount-time snapshot', async () => {
    const baseSettings = {
      theme: 'system',
      compact: false,
      desktopNotify: false,
      notifySound: false,
      sessionTimeout: 30,
      requireLogin: true,
      apiKeyConfigured: false,
    };
    vi.mocked(getGeneralSettings)
      .mockResolvedValueOnce({
        ...baseSettings,
        llmProvider: 'openai',
        model: 'gpt-test',
        baseUrl: 'https://openai.example/v1',
      })
      .mockResolvedValue({
        ...baseSettings,
        llmProvider: 'deepseek',
        model: 'deepseek-chat',
        baseUrl: 'https://deepseek.example/v1',
      });
    vi.mocked(saveGeneralSettings).mockResolvedValue();
    renderTab();

    const saveButtons = await screen.findAllByRole('button', { name: '保存设置' });
    await waitFor(() => expect(saveButtons[0]).toBeEnabled());
    // The AI assistant tab saved a different provider while this tab stayed mounted;
    // submitting the security form must not resurrect the stale provider.
    fireEvent.submit(saveButtons[0].closest('form')!);

    await waitFor(() => expect(saveGeneralSettings).toHaveBeenCalledTimes(1));
    expect(saveGeneralSettings).toHaveBeenCalledWith(
      expect.objectContaining({
        llmProvider: 'deepseek',
        model: 'deepseek-chat',
        baseUrl: 'https://deepseek.example/v1',
        sessionTimeout: 30,
      }),
    );
  });

  it('aborts the save when the fresh settings read fails', async () => {
    renderTab();

    const saveButtons = await screen.findAllByRole('button', { name: '保存设置' });
    await waitFor(() => expect(saveButtons[0]).toBeEnabled());
    // The fresh read that every save is built from now fails: writing the patch on top of the
    // mount-time snapshot would clobber fields another tab saved in the meantime.
    vi.mocked(getGeneralSettings).mockRejectedValueOnce(new Error('boom'));
    fireEvent.submit(saveButtons[0].closest('form')!);

    await waitFor(() => expect(screen.getByText('设置保存失败，请稍后重试')).toBeInTheDocument());
    expect(saveGeneralSettings).not.toHaveBeenCalled();
  });

  it('can explicitly clear a configured DingTalk signing secret', async () => {
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
      dingtalkSigningSecretConfigured: true,
    });
    vi.mocked(saveGeneralSettings).mockResolvedValue();
    const user = userEvent.setup();
    renderTab();

    await user.click(await screen.findByRole('button', { name: '清除钉钉签名密钥' }));

    await waitFor(() =>
      expect(saveGeneralSettings).toHaveBeenCalledWith(
        expect.objectContaining({ clearDingtalkSigningSecret: true }),
      ),
    );
  });
});
