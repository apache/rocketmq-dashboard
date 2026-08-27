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
import { acknowledgeAlert, listSystemAlertsPage } from '../../../services/opsService';
import SystemAlertsPage from '../systemAlerts';

vi.mock('../../../services/opsService', () => ({
  acknowledgeAlert: vi.fn(),
  clearAcknowledgedAlerts: vi.fn(),
  listSystemAlertsPage: vi.fn(),
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

const mixedCaseAlerts = {
  total: 2,
  page: 1,
  size: 20,
  items: [
    {
      id: 4,
      level: 'Error',
      title: 'Mixed-case error',
      description: 'error',
      time: '2026-08-10 01:00',
      acknowledged: false,
    },
    {
      id: 5,
      level: 'WARNING',
      title: 'Mixed-case warning',
      description: 'warning',
      time: '2026-08-10 01:01',
      acknowledged: false,
    },
  ],
};

describe('SystemAlertsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(listSystemAlertsPage).mockReset();
    vi.mocked(listSystemAlertsPage).mockResolvedValue(mixedCaseAlerts);
  });

  it('renders an alert with an unknown backend level', async () => {
    vi.mocked(listSystemAlertsPage).mockResolvedValue({
      total: 1,
      page: 1,
      size: 20,
      items: [
        {
          id: 3,
          level: 'critical',
          title: 'Critical broker condition',
          description: 'A newer backend emitted this level',
          time: '2026-08-10 01:00',
          acknowledged: false,
        },
      ],
    });
    renderPage();

    expect(await screen.findByText('Critical broker condition')).toBeInTheDocument();
    expect(screen.getByText('critical')).toBeInTheDocument();
    expect(screen.getByText('A newer backend emitted this level')).toBeInTheDocument();
  });

  it('filters backend alert levels case-insensitively', async () => {
    vi.mocked(listSystemAlertsPage)
      .mockResolvedValueOnce(mixedCaseAlerts)
      .mockResolvedValueOnce({
        total: 1,
        page: 1,
        size: 20,
        items: [
          {
            id: 4,
            level: 'Error',
            title: 'Mixed-case error',
            description: 'error',
            time: '2026-08-10 01:00',
            acknowledged: false,
          },
        ],
      });
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('Mixed-case error');

    await user.click(screen.getByRole('button', { name: /严\s*重/ }));
    expect(screen.getByText('Mixed-case error')).toBeInTheDocument();
    await waitFor(() =>
      expect(listSystemAlertsPage).toHaveBeenLastCalledWith({
        level: 'error',
        page: 1,
        pageSize: 20,
      }),
    );
    await waitFor(() => expect(screen.queryByText('Mixed-case warning')).not.toBeInTheDocument());
  });

  it('tracks simultaneous acknowledgements independently', async () => {
    vi.mocked(acknowledgeAlert).mockImplementation(() => new Promise(() => {}));
    vi.mocked(listSystemAlertsPage).mockResolvedValue({
      total: 2,
      page: 1,
      size: 20,
      items: [
        {
          id: 1,
          level: 'error',
          title: 'Broker unavailable',
          description: 'broker a',
          time: '2026-08-10 01:00',
          acknowledged: false,
        },
        {
          id: 2,
          level: 'warning',
          title: 'Consumer lag',
          description: 'consumer b',
          time: '2026-08-10 01:01',
          acknowledged: false,
        },
      ],
    });
    const user = userEvent.setup();
    renderPage();

    await screen.findByText('Broker unavailable');
    const acknowledgeButtons = screen.getAllByRole('button', { name: /^确认$/ });
    await user.click(acknowledgeButtons[0]);
    await user.click(acknowledgeButtons[1]);

    await waitFor(() => {
      expect(acknowledgeAlert).toHaveBeenCalledWith(1);
      expect(acknowledgeAlert).toHaveBeenCalledWith(2);
      expect(acknowledgeButtons[0]).toHaveClass('ant-btn-loading');
      expect(acknowledgeButtons[1]).toHaveClass('ant-btn-loading');
    });
  });
});
