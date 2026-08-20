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
import { useTheme } from '../../../theme/useTheme';
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

const ThemeProbe = () => {
  const { themeMode, compact } = useTheme();
  return (
    <>
      <span data-testid="theme-mode">{themeMode}</span>
      <span data-testid="compact-mode">{String(compact)}</span>
    </>
  );
};

const renderTab = () =>
  render(
    <App>
      <ThemeProvider>
        <GeneralSettingsTab />
        <ThemeProbe />
      </ThemeProvider>
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

  it('keeps the session timeout field bound when displaying its unit', async () => {
    renderTab();

    expect(await screen.findByDisplayValue('30')).toBeInTheDocument();
    expect(screen.getByLabelText('会话超时单位')).toHaveValue('分钟');
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
  });

  it('rolls back the appearance preference when saving fails', async () => {
    vi.mocked(saveGeneralSettings).mockRejectedValue(new Error('save failed'));
    const user = userEvent.setup();
    renderTab();

    await screen.findByText('主题模式');
    await user.click(screen.getByText('深色'));

    await waitFor(() => expect(saveGeneralSettings).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(screen.getByTestId('theme-mode')).toHaveTextContent('system'));
    expect(screen.getByRole('radio', { name: '跟随系统' })).toBeChecked();
    expect(localStorage.getItem('rocketmq-studio-theme')).toBe('system');
  });

  it('rolls back compact mode when saving fails', async () => {
    vi.mocked(saveGeneralSettings).mockRejectedValue(new Error('save failed'));
    const user = userEvent.setup();
    renderTab();

    await screen.findByText('紧凑模式');
    const compactSwitch = screen.getByRole('switch');
    await user.click(compactSwitch);

    await waitFor(() => expect(saveGeneralSettings).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(screen.getByTestId('compact-mode')).toHaveTextContent('false'));
    expect(compactSwitch).not.toBeChecked();
    expect(localStorage.getItem('rocketmq-studio-compact')).toBe('false');
  });

  it('serializes form saves and merges each patch into the latest settings', async () => {
    let resolveFirstSave!: () => void;
    vi.mocked(saveGeneralSettings)
      .mockImplementationOnce(
        () =>
          new Promise<void>((resolve) => {
            resolveFirstSave = resolve;
          }),
      )
      .mockResolvedValueOnce();
    const user = userEvent.setup();
    renderTab();

    const timeout = await screen.findByDisplayValue('30');
    const saveButtons = screen.getAllByRole('button', { name: '保存设置' });
    await user.clear(timeout);
    await user.type(timeout, '45');
    fireEvent.submit(saveButtons[0].closest('form')!);
    await waitFor(() => expect(saveGeneralSettings).toHaveBeenCalledTimes(1));

    await user.type(screen.getByLabelText('钉钉机器人 Webhook'), 'https://example.test/notify');
    fireEvent.submit(saveButtons[1].closest('form')!);
    await waitFor(() => expect(saveButtons[1]).toHaveClass('ant-btn-loading'));
    expect(saveGeneralSettings).toHaveBeenCalledTimes(1);

    resolveFirstSave();
    await waitFor(() => expect(saveGeneralSettings).toHaveBeenCalledTimes(2));
    expect(saveGeneralSettings).toHaveBeenLastCalledWith(
      expect.objectContaining({
        sessionTimeout: 45,
        dingtalkWebhook: 'https://example.test/notify',
      }),
    );
  });
});
