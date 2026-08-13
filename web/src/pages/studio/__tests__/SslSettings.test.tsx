/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */

import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { App } from 'antd';
import {
  getSslSettings,
  saveSslSettings,
  validateSslSettings,
  type SslSettings as SslSettingsModel,
} from '../../../api/settings';
import { LangProvider } from '../../../i18n/LangContext';
import SslSettings from '../SslSettings';

vi.mock('../../../api/settings', () => ({
  getSslSettings: vi.fn(),
  saveSslSettings: vi.fn(),
  validateSslSettings: vi.fn(),
}));

const sslSettings: SslSettingsModel = {
  enabled: true,
  protocol: 'TLSv1.3',
  clientAuth: 'need',
  keyStoreType: 'PKCS12',
  keyStorePath: '/etc/rocketmq/server.p12',
  keyStorePasswordConfigured: true,
  trustStoreType: 'PKCS12',
  trustStorePath: '/etc/rocketmq/trust.p12',
  trustStorePasswordConfigured: false,
  restartRequired: true,
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

const renderWithProviders = () =>
  render(
    <App>
      <LangProvider>
        <SslSettings />
      </LangProvider>
    </App>,
  );

describe('SslSettings Page', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(getSslSettings).mockResolvedValue(sslSettings);
    vi.mocked(saveSslSettings).mockResolvedValue(sslSettings);
    vi.mocked(validateSslSettings).mockResolvedValue({
      success: true,
      message: 'SSL/TLS keystore settings are valid',
      warnings: [],
    });
  });

  it('loads persisted SSL settings instead of showing the old unavailable placeholder', async () => {
    renderWithProviders();

    expect(await screen.findByTestId('ssl-settings-form')).toBeInTheDocument();
    expect(screen.getByText('SSL/TLS 配置用于重启前校验')).toBeInTheDocument();
    expect(screen.getByDisplayValue('/etc/rocketmq/server.p12')).toBeInTheDocument();
    expect(screen.getByText('已保存密码；留空将继续保留现有密码')).toBeInTheDocument();
    expect(screen.getByText('清除已保存的 KeyStore 密码')).toBeInTheDocument();
    expect(screen.queryByTestId('ssl-settings-unavailable')).not.toBeInTheDocument();
  });

  it('validates the current SSL form through the backend endpoint', async () => {
    renderWithProviders();

    fireEvent.click(await screen.findByRole('button', { name: '验证配置' }));

    await waitFor(() =>
      expect(validateSslSettings).toHaveBeenCalledWith(
        expect.objectContaining({
          enabled: true,
          protocol: 'TLSv1.3',
          clientAuth: 'need',
          keyStorePath: '/etc/rocketmq/server.p12',
          trustStorePath: '/etc/rocketmq/trust.p12',
        }),
      ),
    );
    expect(await screen.findByTestId('ssl-validation-result')).toHaveTextContent(
      'SSL/TLS keystore settings are valid',
    );
  });

  it('saves settings and clears password fields after the backend accepts them', async () => {
    renderWithProviders();

    fireEvent.click(await screen.findByRole('button', { name: '保存' }));

    await waitFor(() =>
      expect(saveSslSettings).toHaveBeenCalledWith(
        expect.objectContaining({
          enabled: true,
          keyStorePath: '/etc/rocketmq/server.p12',
        }),
      ),
    );
    expect(screen.getAllByPlaceholderText('留空保留现有密码')[0]).toHaveValue('');
  });

  it('sends clear flags when an operator chooses to remove stored passwords', async () => {
    renderWithProviders();

    fireEvent.click(await screen.findByLabelText('清除已保存的 KeyStore 密码'));
    fireEvent.click(screen.getByRole('button', { name: '保存' }));

    await waitFor(() =>
      expect(saveSslSettings).toHaveBeenCalledWith(
        expect.objectContaining({
          clearKeyStorePassword: true,
        }),
      ),
    );
  });
});
