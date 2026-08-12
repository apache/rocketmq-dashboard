/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */

import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { App } from 'antd';
import { getGeneralSettings, saveGeneralSettings } from '../../../api/settings';
import { GeneralSettingsTab } from '../index';

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

describe('GeneralSettingsTab', () => {
  beforeEach(() => {
    vi.clearAllMocks();
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
    render(
      <App>
        <GeneralSettingsTab />
      </App>,
    );

    const saveButton = await screen.findByRole('button', { name: '保存设置' });
    await waitFor(() => expect(saveButton).toBeEnabled());
    const form = saveButton.closest('form');
    expect(form).not.toBeNull();

    fireEvent.submit(form!);
    fireEvent.submit(form!);

    await waitFor(() => expect(saveGeneralSettings).toHaveBeenCalledTimes(1));
  });

  it('hides unsupported appearance and notification controls while preserving their values', async () => {
    vi.mocked(saveGeneralSettings).mockResolvedValue();
    render(
      <App>
        <GeneralSettingsTab />
      </App>,
    );

    const saveButton = await screen.findByRole('button', { name: '保存设置' });
    expect(screen.queryByText('主题模式')).not.toBeInTheDocument();
    expect(screen.queryByText('紧凑模式')).not.toBeInTheDocument();
    expect(screen.queryByText('桌面通知')).not.toBeInTheDocument();
    expect(screen.queryByText('通知声音')).not.toBeInTheDocument();

    fireEvent.click(saveButton);

    await waitFor(() =>
      expect(saveGeneralSettings).toHaveBeenCalledWith(
        expect.objectContaining({
          theme: 'system',
          compact: false,
          desktopNotify: false,
          notifySound: false,
        }),
      ),
    );
  });
});
