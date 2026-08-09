/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */

import { App } from 'antd';
import { render, screen } from '@testing-library/react';
import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { LangProvider } from '../../../i18n/LangContext';
import { listSystemAlerts } from '../../../services/opsService';
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
  });

  it('renders an alert with an unknown backend level', async () => {
    vi.mocked(listSystemAlerts).mockResolvedValue([
      {
        id: 'alert-critical',
        level: 'critical',
        title: 'Critical broker condition',
        description: 'A newer backend emitted this level',
        time: '2026-08-10 01:00',
        acknowledged: false,
      },
    ]);

    renderPage();

    expect(await screen.findByText('Critical broker condition')).toBeInTheDocument();
    expect(screen.getByText('critical')).toBeInTheDocument();
    expect(screen.getByText('A newer backend emitted this level')).toBeInTheDocument();
  });
});
