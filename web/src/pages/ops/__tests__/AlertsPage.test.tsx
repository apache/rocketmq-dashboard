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

import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from 'antd';
import type { AlertRule } from '../../../api/ops';
import { LangProvider } from '../../../i18n/LangContext';
import AlertsPage from '../alerts';
import { createAlertRule, listAlertRules, toggleAlertRule } from '../../../services/opsService';

vi.mock('../../../services/opsService', () => ({
  createAlertRule: vi.fn(),
  deleteAlertRule: vi.fn(),
  listAlertRules: vi.fn(),
  toggleAlertRule: vi.fn(),
  updateAlertRule: vi.fn(),
}));

const alertRules: AlertRule[] = [
  {
    id: 'alert-a',
    name: 'Broker disk usage',
    metric: 'rocketmq_disk_use_ratio',
    operator: '>',
    threshold: 85,
    thresholdUnit: '%',
    duration: '5m',
    channels: ['email'],
    enabled: false,
    lastTriggered: null,
    description: 'disk usage',
  },
  {
    id: 'alert-b',
    name: 'Consumer lag',
    metric: 'rocketmq_consumer_lag_messages',
    operator: '>',
    threshold: 1000,
    thresholdUnit: '条',
    duration: '15m',
    channels: ['dingtalk'],
    enabled: false,
    lastTriggered: null,
    description: 'consumer lag',
  },
];

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

function cloneRule(rule: AlertRule): AlertRule {
  return {
    ...rule,
    channels: [...rule.channels],
  };
}

function renderPage() {
  return render(
    <App>
      <LangProvider>
        <AlertsPage />
      </LangProvider>
    </App>,
  );
}

function getRuleRow(ruleName: string) {
  const row = screen.getByText(ruleName).closest('tr');
  if (!row) throw new Error(`Row not found: ${ruleName}`);
  return row;
}

describe('AlertsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(listAlertRules).mockResolvedValue(alertRules.map(cloneRule));
    vi.mocked(toggleAlertRule).mockImplementation(async (id, enabled) => {
      const rule = alertRules.find((item) => item.id === id);
      if (!rule) throw new Error(`Rule not found: ${id}`);
      return { ...cloneRule(rule), enabled };
    });
  });

  it('submits backend-compatible metric and duration values when creating a rule', async () => {
    vi.mocked(createAlertRule).mockImplementation(async (rule) => ({
      id: 'alert-created',
      name: rule.name ?? '',
      metric: rule.metric ?? '',
      operator: rule.operator ?? '>',
      threshold: rule.threshold ?? 0,
      thresholdUnit: rule.thresholdUnit ?? '',
      duration: rule.duration ?? '',
      channels: rule.channels ?? [],
      enabled: rule.enabled ?? false,
      lastTriggered: null,
      description: rule.description ?? '',
    }));

    const user = userEvent.setup();
    renderPage();

    await screen.findByText('Broker disk usage');
    await user.click(screen.getByRole('button', { name: '新建规则' }));

    const dialog = screen.getByRole('dialog');
    await user.type(within(dialog).getByRole('textbox', { name: '规则名称' }), 'Disk high');

    const selects = within(dialog).getAllByRole('combobox');
    await user.click(selects[0]);
    const metricLabels = await screen.findAllByText('磁盘使用率');
    await user.click(metricLabels[metricLabels.length - 1]);
    await user.click(selects[1]);
    const operatorLabels = await screen.findAllByText('>');
    await user.click(operatorLabels[operatorLabels.length - 1]);
    await user.type(within(dialog).getByRole('spinbutton'), '85');
    await user.click(selects[2]);
    const durationLabels = await screen.findAllByText('5分钟');
    await user.click(durationLabels[durationLabels.length - 1]);
    await user.click(within(dialog).getByRole('checkbox', { name: 'Email' }));
    await user.click(within(dialog).getByRole('button', { name: '新 建' }));

    await waitFor(() => {
      expect(createAlertRule).toHaveBeenCalledWith(
        expect.objectContaining({
          name: 'Disk high',
          metric: 'rocketmq_disk_use_ratio',
          duration: '5m',
          thresholdUnit: '%',
        }),
      );
    });
  });

  it('bulk enables selected alert rules and clears the selection after success', async () => {
    const user = userEvent.setup();
    renderPage();

    await screen.findByText('Broker disk usage');
    await user.click(within(getRuleRow('Broker disk usage')).getByRole('checkbox'));
    await user.click(within(getRuleRow('Consumer lag')).getByRole('checkbox'));

    await user.click(screen.getByRole('button', { name: '批量启用' }));

    await waitFor(() => {
      expect(toggleAlertRule).toHaveBeenCalledTimes(2);
    });
    expect(toggleAlertRule).toHaveBeenCalledWith('alert-a', true);
    expect(toggleAlertRule).toHaveBeenCalledWith('alert-b', true);
    expect(await screen.findByText('已启用 2 条告警规则')).toBeInTheDocument();
    expect(within(getRuleRow('Broker disk usage')).getByRole('switch')).toHaveAttribute(
      'aria-checked',
      'true',
    );
    expect(within(getRuleRow('Consumer lag')).getByRole('switch')).toHaveAttribute(
      'aria-checked',
      'true',
    );
    expect(within(getRuleRow('Broker disk usage')).getByRole('checkbox')).not.toBeChecked();
    expect(within(getRuleRow('Consumer lag')).getByRole('checkbox')).not.toBeChecked();
  });

  it('keeps only failed alert rules selected after a partial bulk failure', async () => {
    vi.mocked(listAlertRules).mockResolvedValue(
      alertRules.map((rule) => ({ ...cloneRule(rule), enabled: true })),
    );
    vi.mocked(toggleAlertRule).mockImplementation(async (id, enabled) => {
      if (id === 'alert-b') throw new Error('network error');
      const rule = alertRules.find((item) => item.id === id);
      if (!rule) throw new Error(`Rule not found: ${id}`);
      return { ...cloneRule(rule), enabled };
    });

    const user = userEvent.setup();
    renderPage();

    await screen.findByText('Broker disk usage');
    await user.click(within(getRuleRow('Broker disk usage')).getByRole('checkbox'));
    await user.click(within(getRuleRow('Consumer lag')).getByRole('checkbox'));

    await user.click(screen.getByRole('button', { name: '批量禁用' }));

    await waitFor(() => {
      expect(toggleAlertRule).toHaveBeenCalledTimes(2);
    });
    expect(await screen.findByText('已禁用 1 条告警规则，1 条失败')).toBeInTheDocument();
    expect(within(getRuleRow('Broker disk usage')).getByRole('switch')).toHaveAttribute(
      'aria-checked',
      'false',
    );
    expect(within(getRuleRow('Consumer lag')).getByRole('switch')).toHaveAttribute(
      'aria-checked',
      'true',
    );
    expect(within(getRuleRow('Broker disk usage')).getByRole('checkbox')).not.toBeChecked();
    expect(within(getRuleRow('Consumer lag')).getByRole('checkbox')).toBeChecked();
  });

  it('keeps all selected alert rules selected when the bulk action fails', async () => {
    vi.mocked(toggleAlertRule).mockRejectedValue(new Error('network error'));

    const user = userEvent.setup();
    renderPage();

    await screen.findByText('Broker disk usage');
    await user.click(within(getRuleRow('Broker disk usage')).getByRole('checkbox'));
    await user.click(within(getRuleRow('Consumer lag')).getByRole('checkbox'));

    await user.click(screen.getByRole('button', { name: '批量启用' }));

    expect(await screen.findByText('2 条告警规则启用失败')).toBeInTheDocument();
    expect(within(getRuleRow('Broker disk usage')).getByRole('checkbox')).toBeChecked();
    expect(within(getRuleRow('Consumer lag')).getByRole('checkbox')).toBeChecked();
    expect(within(getRuleRow('Broker disk usage')).getByRole('switch')).toHaveAttribute(
      'aria-checked',
      'false',
    );
    expect(within(getRuleRow('Consumer lag')).getByRole('switch')).toHaveAttribute(
      'aria-checked',
      'false',
    );
  });

  it('disables other alert rule mutations while a bulk action is running', async () => {
    let resolveToggle: ((rule: AlertRule) => void) | undefined;
    vi.mocked(toggleAlertRule).mockReturnValue(
      new Promise<AlertRule>((resolve) => {
        resolveToggle = resolve;
      }),
    );

    const user = userEvent.setup();
    renderPage();

    await screen.findByText('Broker disk usage');
    await user.click(within(getRuleRow('Broker disk usage')).getByRole('checkbox'));
    await user.click(screen.getByRole('button', { name: '批量启用' }));

    await waitFor(() => {
      expect(toggleAlertRule).toHaveBeenCalledWith('alert-a', true);
    });
    expect(screen.getByRole('button', { name: '新建规则' })).toBeDisabled();
    expect(within(getRuleRow('Broker disk usage')).getByRole('switch')).toBeDisabled();
    expect(
      within(getRuleRow('Broker disk usage')).getByRole('button', { name: '编辑' }),
    ).toBeDisabled();
    expect(
      within(getRuleRow('Broker disk usage')).getByRole('button', { name: '删除' }),
    ).toBeDisabled();

    resolveToggle?.({ ...cloneRule(alertRules[0]), enabled: true });
    expect(await screen.findByText('已启用 1 条告警规则')).toBeInTheDocument();
  });
});
