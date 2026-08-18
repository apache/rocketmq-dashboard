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

import type { ReactElement } from 'react';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from 'antd';
import { LangProvider } from '../../../i18n/LangContext';
import AlertManagementPage from '../AlertManagement';
import {
  createAlertRule,
  deleteAlertRule,
  exportAlertRulesYaml,
  listAlertRules,
  toggleAlertRule,
  updateAlertRule,
} from '../../../api/alertManagement';

vi.mock('../../../api/alertManagement', () => ({
  createAlertRule: vi.fn(),
  deleteAlertRule: vi.fn(),
  exportAlertRulesYaml: vi.fn(),
  listAlertRules: vi.fn(),
  toggleAlertRule: vi.fn(),
  updateAlertRule: vi.fn(),
}));

const alertRules = [
  {
    id: 1,
    name: 'BrokerDown',
    metric: 'up{job="rocketmq-broker"}',
    operator: '==',
    threshold: 0,
    duration: '5m',
    severity: 'critical',
    enabled: true,
    description: 'Broker unavailable - Broker has been unavailable for five minutes',
  },
  {
    id: 2,
    name: 'ConsumerLagHigh',
    metric: 'rocketmq_consumer_lag_messages',
    operator: '>',
    threshold: 100000,
    duration: '10m',
    severity: 'warning',
    enabled: true,
    description: 'Consumer lag is high - Consumer lag has exceeded the threshold',
    brokerName: 'broker-a',
    clusterName: 'DefaultCluster',
  },
];

const rulesYaml = `
groups:
- name: rocketmq-broker.rules
  rules:
    # Rule 1:
    - alert: BrokerDown
      expr: up{job="rocketmq-broker"} == 0
      for: 5m
      labels:
        severity: critical
        team: broker
      annotations:
        summary: "Broker unavailable"
        description: "Broker has been unavailable for five minutes"
    # Rule 2:
    - alert: ConsumerLagHigh
      expr: rocketmq_consumer_lag_messages > 100000
      for: 10m
      labels:
        severity: warning
        team: consumer
      annotations:
        summary: "Consumer lag is high"
        description: "Consumer lag has exceeded the threshold"
`;

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

const renderWithProviders = (ui: ReactElement) => {
  return render(
    <App>
      <LangProvider>{ui}</LangProvider>
    </App>,
  );
};

describe('AlertManagementPage', () => {
  let createObjectURL: ReturnType<typeof vi.fn>;
  let revokeObjectURL: ReturnType<typeof vi.fn>;
  let clickSpy: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    createObjectURL = vi.fn().mockReturnValue('blob:alert-rules');
    revokeObjectURL = vi.fn();
    Object.defineProperty(URL, 'createObjectURL', {
      configurable: true,
      value: createObjectURL,
    });
    Object.defineProperty(URL, 'revokeObjectURL', {
      configurable: true,
      value: revokeObjectURL,
    });
    clickSpy = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
    vi.mocked(listAlertRules).mockResolvedValue(alertRules);
    vi.mocked(exportAlertRulesYaml).mockResolvedValue({ rules: rulesYaml });
    vi.mocked(createAlertRule).mockImplementation(async (rule) => ({
      ...rule,
      id: 3,
    }));
    vi.mocked(updateAlertRule).mockImplementation(async (rule) => rule);
    vi.mocked(toggleAlertRule).mockImplementation(async (id, enabled) => ({
      ...alertRules[0],
      id,
      enabled,
    }));
    vi.mocked(deleteAlertRule).mockResolvedValue(undefined);
  });

  afterEach(() => {
    clickSpy.mockRestore();
  });

  it('loads alert rules after mount', async () => {
    renderWithProviders(<AlertManagementPage />);

    await waitFor(() => {
      expect(listAlertRules).toHaveBeenCalledTimes(1);
    });

    expect(await screen.findByText('BrokerDown')).toBeInTheDocument();
    expect(
      await screen.findByText(
        'rocketmq_consumer_lag_messages{cluster="DefaultCluster",broker="broker-a"} > 100000',
      ),
    ).toBeInTheDocument();
  });

  it('falls back to default exported YAML when no persisted rules exist', async () => {
    vi.mocked(listAlertRules).mockResolvedValue([]);
    renderWithProviders(<AlertManagementPage />);

    const brokerRule = await screen.findByText('BrokerDown');

    expect(exportAlertRulesYaml).toHaveBeenCalledTimes(1);
    const brokerRow = brokerRule.closest('tr');
    expect(brokerRow).not.toBeNull();
    expect(within(brokerRow!).getByRole('switch')).toBeDisabled();
    expect(within(brokerRow!).getAllByRole('button')[0]).toBeDisabled();
  });

  it('exports only the selected server-side rules when rows are selected', async () => {
    const user = userEvent.setup();
    renderWithProviders(<AlertManagementPage />);

    const brokerRule = await screen.findByText('BrokerDown');
    expect(screen.getByText('ConsumerLagHigh')).toBeInTheDocument();

    const brokerRow = brokerRule.closest('tr');
    expect(brokerRow).not.toBeNull();
    await user.click(within(brokerRow!).getByRole('checkbox'));
    await user.click(screen.getByRole('button', { name: '导出 YAML' }));

    await waitFor(() => {
      expect(exportAlertRulesYaml).toHaveBeenCalledWith(['rule-broker-down']);
    });
    expect(createObjectURL).toHaveBeenCalledTimes(1);
    const blob = createObjectURL.mock.calls[0][0] as Blob;
    const yaml = await blob.text();
    expect(yaml).toBe(rulesYaml);
    expect(clickSpy).toHaveBeenCalledTimes(1);
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:alert-rules');
  });

  it('exports all enabled alert rules when no rows are selected', async () => {
    const user = userEvent.setup();
    renderWithProviders(<AlertManagementPage />);

    await screen.findByText('BrokerDown');
    await user.click(screen.getByRole('button', { name: '导出 YAML' }));

    expect(exportAlertRulesYaml).toHaveBeenCalledWith(undefined);

    const blob = createObjectURL.mock.calls[0][0] as Blob;
    const yaml = await blob.text();
    expect(yaml).toContain('alert: BrokerDown');
    expect(yaml).toContain('alert: ConsumerLagHigh');
  });

  it('classifies persisted Proxy metrics under the Proxy team and group', async () => {
    vi.mocked(listAlertRules).mockResolvedValue([
      {
        id: 4,
        name: 'ProxyDown',
        metric: 'rocketmq_proxy_up',
        operator: '==',
        threshold: 0,
        duration: '5m',
        severity: 'critical',
        enabled: true,
      },
    ]);
    renderWithProviders(<AlertManagementPage />);

    const proxyRule = await screen.findByText('ProxyDown');
    const proxyRow = proxyRule.closest('tr');
    expect(proxyRow).not.toBeNull();
    expect(within(proxyRow!).getAllByText('proxy')).toHaveLength(2);
  });

  it('persists rule status changes through the alert rule API', async () => {
    const user = userEvent.setup();
    renderWithProviders(<AlertManagementPage />);

    const brokerRule = await screen.findByText('BrokerDown');
    const brokerRow = brokerRule.closest('tr');
    expect(brokerRow).not.toBeNull();

    await user.click(within(brokerRow!).getByRole('switch'));

    await waitFor(() => {
      expect(toggleAlertRule).toHaveBeenCalledWith('rule-broker-down', false);
    });
    expect(await screen.findByText('告警规则已更新')).toBeInTheDocument();
  });

  it('creates a persisted alert rule from the editor modal', async () => {
    const user = userEvent.setup();
    renderWithProviders(<AlertManagementPage />);

    await screen.findByText('BrokerDown');
    await user.click(screen.getByRole('button', { name: '添加规则' }));

    const dialog = await screen.findByRole('dialog', { name: '添加规则' });
    await user.type(
      within(dialog).getByPlaceholderText('e.g. RocketMQ_Broker_Down'),
      'TopicBacklogHigh',
    );
    fireEvent.change(
      within(dialog).getByPlaceholderText(
        'e.g. rocketmq_consumer_lag_messages{cluster="DefaultCluster"} > 1000',
      ),
      {
        target: {
          value: 'rocketmq_topic_messages{cluster="DefaultCluster",broker="broker-a"} > 500',
        },
      },
    );
    await user.type(
      within(dialog).getByPlaceholderText('Brief description of the alert'),
      'Topic backlog high',
    );
    await user.click(within(dialog).getByRole('button', { name: /OK|确/ }));

    await waitFor(() => {
      expect(createAlertRule).toHaveBeenCalledWith(
        expect.objectContaining({
          name: 'TopicBacklogHigh',
          metric: 'rocketmq_topic_messages',
          clusterName: 'DefaultCluster',
          brokerName: 'broker-a',
          operator: '>',
          threshold: 500,
          duration: '5m',
          enabled: true,
          description: 'Topic backlog high',
          severity: 'warning',
        }),
      );
    });
    expect(await screen.findByText('告警规则已创建')).toBeInTheDocument();
  });

  it('updates a persisted alert rule from the editor modal', async () => {
    const user = userEvent.setup();
    renderWithProviders(<AlertManagementPage />);

    const consumerRule = await screen.findByText('ConsumerLagHigh');
    const consumerRow = consumerRule.closest('tr');
    expect(consumerRow).not.toBeNull();
    await user.click(within(consumerRow!).getAllByRole('button')[0]);

    const dialog = await screen.findByRole('dialog', { name: '编辑规则' });
    const summaryInput = within(dialog).getByPlaceholderText('Brief description of the alert');
    await user.clear(summaryInput);
    await user.type(summaryInput, 'Consumer lag updated');
    await user.click(within(dialog).getByRole('button', { name: /OK|确/ }));

    await waitFor(() => {
      expect(updateAlertRule).toHaveBeenCalledWith(
        expect.objectContaining({
          id: 'rule-consumer-lag',
          name: 'ConsumerLagHigh',
          metric: 'rocketmq_consumer_lag_messages',
          clusterName: 'DefaultCluster',
          brokerName: 'broker-a',
          operator: '>',
          threshold: 100000,
          description: 'Consumer lag updated - Consumer lag has exceeded the threshold',
        }),
      );
    });
    expect(await screen.findByText('告警规则已更新')).toBeInTheDocument();
  });

  it('rejects selectors that the persisted alert contract cannot represent', async () => {
    const user = userEvent.setup();
    renderWithProviders(<AlertManagementPage />);

    await screen.findByText('BrokerDown');
    await user.click(screen.getByRole('button', { name: '添加规则' }));

    const dialog = await screen.findByRole('dialog', { name: '添加规则' });
    await user.type(
      within(dialog).getByPlaceholderText('e.g. RocketMQ_Broker_Down'),
      'UnsupportedSelector',
    );
    fireEvent.change(
      within(dialog).getByPlaceholderText(
        'e.g. rocketmq_consumer_lag_messages{cluster="DefaultCluster"} > 1000',
      ),
      { target: { value: 'up{job=~"rocketmq.*broker.*"} == 0' } },
    );
    await user.type(
      within(dialog).getByPlaceholderText('Brief description of the alert'),
      'Unsupported selector',
    );
    await user.click(within(dialog).getByRole('button', { name: /OK|确/ }));

    await waitFor(() => expect(createAlertRule).not.toHaveBeenCalled());
    expect(dialog).toBeInTheDocument();
    expect(await screen.findByText(/Expression supports only/)).toBeInTheDocument();
  });

  it('deletes a persisted alert rule', async () => {
    const user = userEvent.setup();
    renderWithProviders(<AlertManagementPage />);

    const brokerRule = await screen.findByText('BrokerDown');
    const brokerRow = brokerRule.closest('tr');
    expect(brokerRow).not.toBeNull();
    await user.click(within(brokerRow!).getAllByRole('button')[1]);
    await user.click(await screen.findByRole('button', { name: /OK|确/ }));

    await waitFor(() => {
      expect(deleteAlertRule).toHaveBeenCalledWith('rule-broker-down');
    });
    expect(screen.queryByText('BrokerDown')).not.toBeInTheDocument();
  });

  it('preserves selected rules while filtering the table', async () => {
    const user = userEvent.setup();
    renderWithProviders(<AlertManagementPage />);

    const brokerRule = await screen.findByText('BrokerDown');
    const brokerRow = brokerRule.closest('tr');
    expect(brokerRow).not.toBeNull();
    await user.click(within(brokerRow!).getByRole('checkbox'));

    await user.type(screen.getByRole('textbox'), 'ConsumerLagHigh');

    expect(screen.queryByText('BrokerDown')).not.toBeInTheDocument();
    expect(screen.getByText('ConsumerLagHigh')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '导出 YAML' }));

    const blob = createObjectURL.mock.calls[0][0] as Blob;
    const yaml = await blob.text();
    expect(yaml).toContain('alert: BrokerDown');
    expect(yaml).toContain('alert: ConsumerLagHigh');
  });
});
