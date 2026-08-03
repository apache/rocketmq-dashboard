// Licensed to the Apache Software Foundation (ASF) under one or more
// contributor license agreements.

import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App as AntdApp } from 'antd';
import { LangProvider } from '../../i18n/LangContext';
import { MetricsDataSourceManager } from '../MetricsDataSourceManager';
import type { MetricsDataSource } from '../../api/metrics';

vi.mock('../../services/metricsService', () => ({
  listMetricDataSources: vi.fn(),
  createMetricDataSource: vi.fn(),
  updateMetricDataSource: vi.fn(),
  deleteMetricDataSource: vi.fn(),
  queryMetricDataSource: vi.fn(),
}));

import * as metricsService from '../../services/metricsService';

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

const seeded: MetricsDataSource[] = [
  {
    name: 'prometheus-prod',
    providerType: 'PROMETHEUS',
    url: 'http://prometheus:9090',
    authType: 'none',
    enabled: true,
  },
  {
    name: 'victoriametrics-prod',
    providerType: 'VICTORIAMETRICS',
    url: 'http://vm:8428',
    authType: 'none',
    enabled: true,
  },
];

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(metricsService.listMetricDataSources).mockResolvedValue(seeded);
});

describe('MetricsDataSourceManager', () => {
  it('lists the configured data sources', async () => {
    render(
      <AntdApp>
        <LangProvider>
          <MetricsDataSourceManager />
        </LangProvider>
      </AntdApp>,
    );

    expect(await screen.findByText('VictoriaMetrics')).toBeInTheDocument();
    expect(screen.getByText('prometheus-prod')).toBeInTheDocument();
    expect(screen.getByText('victoriametrics-prod')).toBeInTheDocument();
  });

  it('opens the add modal and creates a data source', async () => {
    const user = userEvent.setup();
    vi.mocked(metricsService.createMetricDataSource).mockResolvedValue({
      name: 'cortex-prod',
      providerType: 'CORTEX',
      url: 'http://cortex:9009',
      authType: 'none',
      enabled: true,
    });

    render(
      <AntdApp>
        <LangProvider>
          <MetricsDataSourceManager />
        </LangProvider>
      </AntdApp>,
    );
    await screen.findByText('VictoriaMetrics');

    await user.click(screen.getByRole('button', { name: /Add Data Source|新增数据源/ }));
    const dialog = await screen.findByRole('dialog');

    const inputs = within(dialog).getAllByRole('textbox');
    await user.type(inputs[0], 'cortex-prod');
    await user.type(inputs[1], 'http://cortex:9009');

    // pick the CORTEX provider type from the antd Select (options are portaled to body)
    await user.click(within(dialog).getByText('Prometheus'));
    await user.click(await screen.findByText('Cortex'));

    await user.click(within(dialog).getByText('OK'));

    await waitFor(() =>
      expect(metricsService.createMetricDataSource).toHaveBeenCalledWith(
        expect.objectContaining({ name: 'cortex-prod', providerType: 'CORTEX' }),
      ),
    );
  });
});
