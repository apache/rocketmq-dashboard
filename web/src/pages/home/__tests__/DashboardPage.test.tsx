/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */

import { App } from 'antd';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type React from 'react';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import type { DashboardData } from '../../../api/metrics';
import { LangProvider } from '../../../i18n/LangContext';
import * as dashboardService from '../../../services/dashboardService';
import * as instanceService from '../../../services/instanceService';
import DashboardPage from '../dashboard';

vi.mock('../../../services/dashboardService', () => ({ getDashboard: vi.fn() }));
vi.mock('../../../services/instanceService', () => ({ listInstances: vi.fn() }));

const dashboard = (name: string): DashboardData => ({
  stats: {
    totalClusters: 1,
    healthyClusters: 1,
    totalBrokers: 1,
    totalProxies: 0,
    totalNameServers: 1,
    totalTopics: 1,
    totalConsumerGroups: 1,
    totalMessagesToday: 1,
    messagesPerSecond: 1,
    tpsIn: 1,
    tpsOut: 1,
  },
  clusters: [
    {
      id: name,
      name,
      type: 'V4_NAMESRV',
      status: 'healthy',
      brokers: 1,
      proxies: 0,
      topics: 1,
      groups: 1,
      tpsIn: 1,
      tpsOut: 1,
      version: '5.0.0',
      throughput: [1],
    },
  ],
});

const deferred = <T,>() => {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((promiseResolve) => {
    resolve = promiseResolve;
  });
  return { promise, resolve };
};

const renderWithProviders = (ui: React.ReactElement) =>
  render(
    <App>
      <LangProvider>
        <MemoryRouter>{ui}</MemoryRouter>
      </LangProvider>
    </App>,
  );

const LocationProbe = () => {
  const location = useLocation();
  return (
    <output data-testid="location">
      {location.pathname}
      {location.search}
    </output>
  );
};

beforeAll(() => {
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: vi.fn().mockImplementation(() => ({
      matches: false,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    })),
  });
});

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(instanceService.listInstances).mockResolvedValue([
    {
      id: 1,
      name: 'instance-a',
      endpoint: 'a:9876',
      type: 'DIRECT',
      remark: '',
      topicCount: 0,
      consumerGroupCount: 0,
      gmtCreate: '',
      gmtModified: '',
    },
    {
      id: 2,
      name: 'instance-b',
      endpoint: 'b:9876',
      type: 'DIRECT',
      remark: '',
      topicCount: 0,
      consumerGroupCount: 0,
      gmtCreate: '',
      gmtModified: '',
    },
  ]);
});

describe('DashboardPage', () => {
  it('does not show dashboard data from the previous instance while loading a new selection', async () => {
    const instanceA = deferred<DashboardData>();
    vi.mocked(dashboardService.getDashboard)
      .mockResolvedValueOnce(dashboard('initial-cluster'))
      .mockReturnValueOnce(instanceA.promise);
    const user = userEvent.setup();
    renderWithProviders(<DashboardPage />);

    await screen.findByText('initial-cluster');
    const selector = screen.getByRole('combobox', { name: 'Dashboard instance' });
    await user.click(selector);
    await user.click(
      await screen.findByText('instance-a', { selector: '.ant-select-item-option-content' }),
    );
    await waitFor(() => expect(dashboardService.getDashboard).toHaveBeenCalledWith('instance-a'));

    expect(screen.queryByText('initial-cluster')).not.toBeInTheDocument();
    instanceA.resolve(dashboard('instance-a-cluster'));
    await screen.findByText('instance-a-cluster');
  });

  it('does not let a stale instance response overwrite the latest selection', async () => {
    const instanceA = deferred<DashboardData>();
    const instanceB = deferred<DashboardData>();
    vi.mocked(dashboardService.getDashboard)
      .mockResolvedValueOnce(dashboard('initial-cluster'))
      .mockReturnValueOnce(instanceA.promise)
      .mockReturnValueOnce(instanceB.promise);
    const user = userEvent.setup();
    renderWithProviders(<DashboardPage />);

    await screen.findByText('initial-cluster');
    const selector = screen.getByRole('combobox', { name: 'Dashboard instance' });
    await user.click(selector);
    await user.click(
      await screen.findByText('instance-a', { selector: '.ant-select-item-option-content' }),
    );
    await user.click(selector);
    await user.click(
      await screen.findByText('instance-b', { selector: '.ant-select-item-option-content' }),
    );

    instanceB.resolve(dashboard('instance-b-cluster'));
    expect(await screen.findByText('instance-b-cluster')).toBeInTheDocument();

    instanceA.resolve(dashboard('instance-a-cluster'));
    await waitFor(() => {
      expect(screen.queryByText('instance-a-cluster')).not.toBeInTheDocument();
    });
    expect(screen.getByText('instance-b-cluster')).toBeInTheDocument();
  });

  it('preserves the selected instance when navigating to the cluster page', async () => {
    vi.mocked(dashboardService.getDashboard).mockResolvedValue(dashboard('instance-a-cluster'));
    const user = userEvent.setup();
    renderWithProviders(
      <>
        <DashboardPage />
        <LocationProbe />
      </>,
    );

    await screen.findByText('instance-a-cluster');
    const selector = screen.getByRole('combobox', { name: 'Dashboard instance' });
    await user.click(selector);
    await user.click(
      await screen.findByText('instance-b', { selector: '.ant-select-item-option-content' }),
    );
    await user.click(screen.getByText('查看全部'));

    expect(screen.getByTestId('location')).toHaveTextContent('/cluster?instanceId=instance-b');
  });
});
