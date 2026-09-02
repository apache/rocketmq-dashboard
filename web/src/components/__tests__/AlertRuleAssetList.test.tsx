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

import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest';
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App as AntdApp } from 'antd';
import AlertRuleAssetList from '../AlertRuleAssetList';
import { LangProvider } from '../../i18n/LangContext';
import * as alertRuleAssetService from '../../services/alertRuleAssetService';

vi.mock('../../services/alertRuleAssetService', () => ({
  listAlertRuleAssets: vi.fn(),
  getAlertRuleAsset: vi.fn(),
  exportAlertRuleAsset: vi.fn(),
}));

const sampleAssets = [
  {
    name: 'rocketmq-broker-down',
    group: 'rocketmq-broker.rules',
    ruleCount: 1,
    severities: ['critical'],
  },
  {
    name: 'rocketmq-consumer-lag-high',
    group: 'rocketmq-consumer.rules',
    ruleCount: 1,
    severities: ['warning'],
  },
];

const renderWithProviders = (ui: React.ReactElement) =>
  render(
    <LangProvider>
      <AntdApp>{ui}</AntdApp>
    </LangProvider>,
  );

describe('AlertRuleAssetList', () => {
  beforeAll(() => {
    window.matchMedia =
      window.matchMedia ||
      ((query: string) =>
        ({
          matches: false,
          media: query,
          onchange: null,
          addListener: () => {},
          removeListener: () => {},
          addEventListener: () => {},
          removeEventListener: () => {},
          dispatchEvent: () => false,
        }) as unknown as MediaQueryList);
  });

  afterEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
  });

  it('renders asset rows from the service', async () => {
    vi.mocked(alertRuleAssetService.listAlertRuleAssets).mockResolvedValue(sampleAssets);

    renderWithProviders(<AlertRuleAssetList />);

    expect(await screen.findByText('rocketmq-broker-down')).toBeInTheDocument();
    expect(screen.getByText('rocketmq-consumer-lag-high')).toBeInTheDocument();
  });

  it('filters assets by search text and severity', async () => {
    vi.mocked(alertRuleAssetService.listAlertRuleAssets).mockResolvedValue(sampleAssets);
    const user = userEvent.setup({ pointerEventsCheck: 0 });
    const { container } = renderWithProviders(<AlertRuleAssetList />);

    expect(await screen.findByText('rocketmq-broker-down')).toBeInTheDocument();

    await user.type(screen.getByPlaceholderText(/Search alert|搜索告警/), 'consumer');

    expect(screen.getByText('rocketmq-consumer-lag-high')).toBeInTheDocument();
    expect(screen.queryByText('rocketmq-broker-down')).not.toBeInTheDocument();

    await user.clear(screen.getByPlaceholderText(/Search alert|搜索告警/));
    await user.click(container.querySelector('.ant-select-selector') as Element);
    await user.click(
      await screen.findByText('CRITICAL', { selector: '.ant-select-item-option-content' }),
    );

    expect(screen.getByText('rocketmq-broker-down')).toBeInTheDocument();
    await waitFor(() =>
      expect(screen.queryByText('rocketmq-consumer-lag-high')).not.toBeInTheDocument(),
    );
  }, 10_000);
  it('keeps a failed list request visible and recovers when retried', async () => {
    vi.mocked(alertRuleAssetService.listAlertRuleAssets)
      .mockRejectedValueOnce(new Error('temporary failure'))
      .mockResolvedValueOnce(sampleAssets);

    renderWithProviders(<AlertRuleAssetList />);

    const retryButton = await screen.findByRole('button', { name: /Retry|重试/ });
    expect(alertRuleAssetService.listAlertRuleAssets).toHaveBeenCalledTimes(1);

    fireEvent.click(retryButton);

    expect(await screen.findByText('rocketmq-broker-down')).toBeInTheDocument();
    expect(alertRuleAssetService.listAlertRuleAssets).toHaveBeenCalledTimes(2);
    expect(screen.queryByRole('button', { name: /Retry|重试/ })).not.toBeInTheDocument();
  });

  it('opens a modal with yaml content when View is clicked', async () => {
    vi.mocked(alertRuleAssetService.listAlertRuleAssets).mockResolvedValue(sampleAssets);
    vi.mocked(alertRuleAssetService.getAlertRuleAsset).mockResolvedValue(
      'groups:\n  - name: rocketmq-broker.rules\n    rules:\n      - alert: RocketMQBrokerDown\n',
    );

    renderWithProviders(<AlertRuleAssetList />);

    const viewButtons = await screen.findAllByRole('button', { name: /查看|View/ });
    fireEvent.click(viewButtons[0]);

    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByText(/rocketmq-broker.rules/)).toBeInTheDocument();
    expect(alertRuleAssetService.getAlertRuleAsset).toHaveBeenCalledWith('rocketmq-broker-down');
  });

  it('keeps the latest preview when an earlier request resolves last', async () => {
    vi.mocked(alertRuleAssetService.listAlertRuleAssets).mockResolvedValue(sampleAssets);
    let resolveBroker!: (value: string) => void;
    let resolveConsumer!: (value: string) => void;
    vi.mocked(alertRuleAssetService.getAlertRuleAsset).mockImplementation(
      (name) =>
        new Promise((resolve) => {
          if (name === 'rocketmq-broker-down') resolveBroker = resolve;
          else resolveConsumer = resolve;
        }),
    );
    renderWithProviders(<AlertRuleAssetList />);

    const viewButtons = await screen.findAllByRole('button', { name: /查看|View/ });
    fireEvent.click(viewButtons[0]);
    fireEvent.click(viewButtons[1]);

    await act(async () => {
      resolveConsumer('alert: LATEST_CONSUMER_ALERT');
    });
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByText(/LATEST_CONSUMER_ALERT/)).toBeInTheDocument();

    await act(async () => {
      resolveBroker('alert: STALE_BROKER_ALERT');
    });
    expect(within(dialog).getByText(/LATEST_CONSUMER_ALERT/)).toBeInTheDocument();
    expect(within(dialog).queryByText(/STALE_BROKER_ALERT/)).not.toBeInTheDocument();
  });

  it('tracks simultaneous asset exports independently', async () => {
    vi.mocked(alertRuleAssetService.listAlertRuleAssets).mockResolvedValue(sampleAssets);
    vi.mocked(alertRuleAssetService.exportAlertRuleAsset).mockImplementation(
      () => new Promise(() => {}),
    );
    renderWithProviders(<AlertRuleAssetList />);

    const exportButtons = await screen.findAllByRole('button', { name: /导出|Export/ });
    fireEvent.click(exportButtons[0]);
    fireEvent.click(exportButtons[1]);

    await waitFor(() => {
      expect(alertRuleAssetService.exportAlertRuleAsset).toHaveBeenCalledTimes(2);
      expect(exportButtons[0]).toHaveClass('ant-btn-loading');
      expect(exportButtons[1]).toHaveClass('ant-btn-loading');
    });
  });

  it('deduplicates an asset export before loading state renders', async () => {
    vi.mocked(alertRuleAssetService.listAlertRuleAssets).mockResolvedValue(sampleAssets);
    let resolveExport!: (value: Blob) => void;
    vi.mocked(alertRuleAssetService.exportAlertRuleAsset).mockImplementation(
      () => new Promise((resolve) => (resolveExport = resolve)),
    );
    renderWithProviders(<AlertRuleAssetList />);

    const exportButtons = await screen.findAllByRole('button', { name: /导出|Export/ });
    act(() => {
      exportButtons[0].click();
      exportButtons[0].click();
    });

    expect(alertRuleAssetService.exportAlertRuleAsset).toHaveBeenCalledTimes(1);
    await act(async () => resolveExport(new Blob(['rules'])));
  });

  it('downloads the yaml when Export is clicked', async () => {
    const createObjectURLSpy = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:url');
    const revokeSpy = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {});
    const clickSpy = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});

    vi.mocked(alertRuleAssetService.listAlertRuleAssets).mockResolvedValue(sampleAssets);
    vi.mocked(alertRuleAssetService.exportAlertRuleAsset).mockResolvedValue(
      new Blob(['groups:\n  - name: rocketmq-broker.rules\n'], { type: 'text/yaml' }),
    );

    renderWithProviders(<AlertRuleAssetList />);

    const exportButtons = await screen.findAllByRole('button', { name: /导出|Export/ });
    fireEvent.click(exportButtons[0]);

    await waitFor(() =>
      expect(alertRuleAssetService.exportAlertRuleAsset).toHaveBeenCalledWith(
        'rocketmq-broker-down',
      ),
    );
    expect(createObjectURLSpy).toHaveBeenCalled();
    expect(clickSpy).toHaveBeenCalled();

    createObjectURLSpy.mockRestore();
    revokeSpy.mockRestore();
    clickSpy.mockRestore();
  });
});
