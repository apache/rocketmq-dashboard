/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */

import { App } from 'antd';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { LangProvider } from '../../../i18n/LangContext';
import { acknowledgeAlert, listSystemAlerts } from '../../../services/opsService';
import SystemAlertsPage from '../systemAlerts';

vi.mock('../../../services/opsService', () => ({
  acknowledgeAlert: vi.fn(),
  clearAcknowledgedAlerts: vi.fn(),
  listSystemAlerts: vi.fn(),
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

const renderPage = () =>
  render(
    <App>
      <LangProvider>
        <SystemAlertsPage />
      </LangProvider>
    </App>,
  );

describe('SystemAlertsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(listSystemAlerts).mockResolvedValue([
      {
        id: 'alert-a',
        level: 'error',
        title: 'Broker unavailable',
        description: 'broker a',
        time: '2026-08-10 01:00',
        acknowledged: false,
      },
      {
        id: 'alert-b',
        level: 'warning',
        title: 'Consumer lag',
        description: 'consumer b',
        time: '2026-08-10 01:01',
        acknowledged: false,
      },
    ]);
  });

  it('tracks simultaneous acknowledgements independently', async () => {
    vi.mocked(acknowledgeAlert).mockImplementation(() => new Promise(() => {}));
    const user = userEvent.setup();
    renderPage();

    await screen.findByText('Broker unavailable');
    const acknowledgeButtons = screen.getAllByRole('button', { name: /^确认$/ });
    await user.click(acknowledgeButtons[0]);
    await user.click(acknowledgeButtons[1]);

    await waitFor(() => {
      expect(acknowledgeAlert).toHaveBeenCalledWith('alert-a');
      expect(acknowledgeAlert).toHaveBeenCalledWith('alert-b');
      expect(acknowledgeButtons[0]).toHaveClass('ant-btn-loading');
      expect(acknowledgeButtons[1]).toHaveClass('ant-btn-loading');
    });
  });
});
