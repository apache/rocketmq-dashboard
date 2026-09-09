/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */

import { App } from 'antd';
import { render, screen, waitFor, within } from '@testing-library/react';
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

const unavailableTopologyDashboard = (): DashboardData => ({
  ...dashboard('proxy-cluster'),
  stats: {
    ...dashboard('proxy-cluster').stats,
    totalProxies: null,
    totalNameServers: null,
  },
  clusters: dashboard('proxy-cluster').clusters.map((cluster) => ({
    ...cluster,
    proxies: null,
  })),
});

const trafficDashboard = (): DashboardData => ({
  ...dashboard('traffic-a'),
  stats: {
    ...dashboard('traffic-a').stats,
    totalClusters: 2,
    totalBrokers: 3,
    totalProxies: 1,
    tpsIn: 125,
    tpsOut: 75,
    messagesPerSecond: 200,
  },
  clusters: [
    {
      ...dashboard('traffic-a').clusters[0],
      brokers: 2,
      tpsIn: 100,
      tpsOut: 50,
      throughput: [10, 20, 30, 80],
    },
    {
      ...dashboard('traffic-b').clusters[0],
      brokers: 1,
      tpsIn: 25,
      tpsOut: 25,
      throughput: [25, 25, 25, 25],
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
  it('renders unavailable Proxy topology counts as N/A instead of zero', async () => {
    vi.mocked(dashboardService.getDashboard).mockResolvedValue(unavailableTopologyDashboard());
    renderWithProviders(<DashboardPage />);

    await screen.findAllByText('proxy-cluster');
    expect(await screen.findByText(/1 Brokers · N\/A Proxy/u)).toBeInTheDocument();
    expect(screen.queryByText('0 Proxy')).not.toBeInTheDocument();
    const clusterHealthCard = screen.getByText('集群健康概览').closest('.ant-card');
    expect(clusterHealthCard).not.toBeNull();
    const row = within(clusterHealthCard as HTMLElement)
      .getByText('proxy-cluster')
      .closest('tr');
    expect(row).not.toBeNull();
    expect(within(row as HTMLElement).getAllByText('N/A').length).toBeGreaterThanOrEqual(1);
  });

  it('merges traffic insight metrics into the existing cluster health table', async () => {
    vi.mocked(dashboardService.getDashboard).mockResolvedValue(trafficDashboard());
    renderWithProviders(<DashboardPage />);

    await screen.findAllByText('traffic-a');
    const clusterHealthCard = screen.getByText('集群健康概览').closest('.ant-card');
    expect(clusterHealthCard).not.toBeNull();
    const clusterHealth = within(clusterHealthCard as HTMLElement);
    expect(clusterHealth.getAllByText('总 TPS')).not.toHaveLength(0);
    expect(clusterHealth.getAllByText('占比')).not.toHaveLength(0);
    expect(clusterHealth.getAllByText('单 Broker TPS')).not.toHaveLength(0);
    expect(clusterHealth.getAllByText('出入比')).not.toHaveLength(0);

    const row = clusterHealth.getByText('traffic-a').closest('tr');
    expect(row).not.toBeNull();
    const clusterRow = within(row as HTMLElement);
    expect(clusterRow.getByText('150/s')).toBeInTheDocument();
    expect(clusterRow.getByText('75%')).toBeInTheDocument();
    expect(clusterRow.getByText('75/s')).toBeInTheDocument();
    expect(clusterRow.getByText('0.5:1')).toBeInTheDocument();
    expect(clusterRow.getByText(/上升/u)).toBeInTheDocument();
  });

  it('does not show dashboard data from the previous instance while loading a new selection', async () => {
    const instanceA = deferred<DashboardData>();
    vi.mocked(dashboardService.getDashboard)
      .mockResolvedValueOnce(dashboard('initial-cluster'))
      .mockReturnValueOnce(instanceA.promise);
    const user = userEvent.setup();
    renderWithProviders(<DashboardPage />);

    await screen.findAllByText('initial-cluster');
    const selector = screen.getByRole('combobox', { name: 'Dashboard instance' });
    await user.click(selector);
    await user.click(
      await screen.findByText('instance-a', { selector: '.ant-select-item-option-content' }),
    );
    await waitFor(() => expect(dashboardService.getDashboard).toHaveBeenCalledWith('instance-a'));

    expect(screen.queryByText('initial-cluster')).not.toBeInTheDocument();
    instanceA.resolve(dashboard('instance-a-cluster'));
    await screen.findAllByText('instance-a-cluster');
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

    await screen.findAllByText('initial-cluster');
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
    expect(await screen.findAllByText('instance-b-cluster')).not.toHaveLength(0);

    instanceA.resolve(dashboard('instance-a-cluster'));
    await waitFor(() => {
      expect(screen.queryByText('instance-a-cluster')).not.toBeInTheDocument();
    });
    expect(screen.getAllByText('instance-b-cluster')).not.toHaveLength(0);
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

    await screen.findAllByText('instance-a-cluster');
    const selector = screen.getByRole('combobox', { name: 'Dashboard instance' });
    await user.click(selector);
    await user.click(
      await screen.findByText('instance-b', { selector: '.ant-select-item-option-content' }),
    );
    await user.click(screen.getByText('查看全部'));

    expect(screen.getByTestId('location')).toHaveTextContent('/cluster?instanceId=instance-b');
  });

  it('does not offer cloud instances for MQAdmin runtime diagnostics', async () => {
    vi.mocked(instanceService.listInstances).mockResolvedValue([
      {
        id: 1,
        name: 'apache-instance',
        endpoint: 'apache:9876',
        type: 'DIRECT',
        vendor: 'APACHE',
        remark: '',
        topicCount: 0,
        consumerGroupCount: 0,
        gmtCreate: '',
        gmtModified: '',
      },
      {
        id: 2,
        name: 'cloud-instance',
        endpoint: 'cloud:9876',
        type: 'DIRECT',
        vendor: 'ALIYUN',
        remark: '',
        topicCount: 0,
        consumerGroupCount: 0,
        gmtCreate: '',
        gmtModified: '',
      },
    ]);
    vi.mocked(dashboardService.getDashboard).mockResolvedValue(dashboard('apache-cluster'));
    const user = userEvent.setup();
    renderWithProviders(<DashboardPage />);

    await screen.findAllByText('apache-cluster');
    await user.click(screen.getByRole('combobox', { name: 'Dashboard instance' }));

    expect(
      await screen.findByText('apache-instance', {
        selector: '.ant-select-item-option-content',
      }),
    ).toBeInTheDocument();
    expect(
      screen.queryByText('cloud-instance', { selector: '.ant-select-item-option-content' }),
    ).not.toBeInTheDocument();
  });
});
