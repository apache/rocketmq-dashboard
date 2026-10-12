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
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from 'antd';
import type { AlertRule, AlertRuleDomain, AlertRuleTestResult, PageResult } from '../../../api/ops';
import { LangProvider } from '../../../i18n/LangContext';
import { listInstances } from '../../../services/instanceService';
import {
  listAlertRulesPage,
  listAlertRuleRuntime,
  listNativeAlertMetrics,
  testAlertRule,
  updateAlertRule,
} from '../../../services/opsService';
import AlertsPage from '../alerts';

vi.mock('../../../services/instanceService', () => ({
  listInstances: vi.fn(),
}));

vi.mock('../../../services/opsService', () => ({
  createAlertRule: vi.fn(),
  deleteAlertRule: vi.fn(),
  listAlertRulesPage: vi.fn(),
  listAlertRuleRuntime: vi.fn(),
  listNativeAlertMetrics: vi.fn(),
  toggleAlertRule: vi.fn(),
  bulkToggleAlertRules: vi.fn(),
  bulkDeleteAlertRules: vi.fn(),
  exportAlertRulesTransfer: vi.fn(),
  importAlertRulesTransfer: vi.fn(),
  updateAlertRule: vi.fn(),
  testAlertRule: vi.fn(),
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

const buildRule = (id: number, name: string): AlertRule => ({
  id,
  name,
  instanceId: 'local',
  metric: 'broker.disk.usage_ratio',
  operator: '>',
  threshold: 85,
  thresholdUnit: '%',
  duration: '1m',
  channels: ['dingtalk'],
  enabled: true,
  lastTriggered: null,
  description: '',
});

const pageResult = (rules: AlertRule[]): PageResult<AlertRule> => ({
  items: rules.map((rule) => ({ ...rule, channels: [...rule.channels] })),
  total: rules.length,
  page: 1,
  size: 20,
});

const renderPage = (domain?: AlertRuleDomain) =>
  render(
    <App>
      <LangProvider>
        <AlertsPage domain={domain} />
      </LangProvider>
    </App>,
  );

function getRuleRow(ruleName: string) {
  const row = screen.getByText(ruleName).closest('tr');
  if (!row) throw new Error(`Row not found: ${ruleName}`);
  return row;
}

function mockBusinessLagRule() {
  const lagRule: AlertRule = {
    ...buildRule(3, 'Lag guard'),
    metric: 'consumer.lag.total',
    threshold: 1000,
    thresholdUnit: 'messages',
    channels: ['email'],
    consumerGroup: 'orders',
    aggregation: 'LAST',
    windowSeconds: 0,
    consecutiveSamples: 1,
  };
  vi.mocked(listAlertRulesPage).mockResolvedValue(pageResult([lagRule]));
  vi.mocked(listNativeAlertMetrics).mockResolvedValue([
    {
      key: 'consumer.lag.total',
      label: 'Consumer lag total',
      thresholdUnit: 'messages',
      supportsConsumerGroup: true,
    },
  ]);
}

function lagTestResult(conditionMet = true): AlertRuleTestResult {
  return {
    samples: [
      {
        labels: { consumerGroup: 'orders' },
        availability: 'AVAILABLE',
        currentValue: 1200,
        conditionMet,
      },
    ],
  };
}

async function openLagEditor(user: ReturnType<typeof userEvent.setup>) {
  renderPage('BUSINESS');
  await screen.findByText('Lag guard');
  await user.click(within(getRuleRow('Lag guard')).getByRole('button', { name: '编辑' }));
  return screen.findByRole('dialog');
}

describe('AlertsPage rule test results', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(listInstances).mockResolvedValue([
      {
        id: 1,
        name: 'local',
        remark: '',
        type: 'DIRECT',
        endpoint: '127.0.0.1:9876',
        topicCount: 0,
        consumerGroupCount: 0,
        gmtCreate: '',
        gmtModified: '',
      },
    ]);
    vi.mocked(listAlertRulesPage).mockResolvedValue(
      pageResult([buildRule(1, 'Rule A'), buildRule(2, 'Rule B')]),
    );
    vi.mocked(listAlertRuleRuntime).mockResolvedValue([]);
    vi.mocked(listNativeAlertMetrics).mockResolvedValue([
      {
        key: 'broker.disk.usage_ratio',
        label: 'Broker disk usage ratio',
        thresholdUnit: 'ratio',
        supportsConsumerGroup: false,
      },
    ]);
    vi.mocked(updateAlertRule).mockImplementation(async (rule: Partial<AlertRule>) =>
      buildRule(Number(rule.id), String(rule.name)),
    );
    vi.mocked(testAlertRule).mockResolvedValue({
      samples: [
        {
          labels: { broker: 'stale-broker-sample' },
          availability: 'AVAILABLE',
          currentValue: 987654,
          conditionMet: true,
        },
      ],
    });
  });

  it('shows no test samples for a rule the user never tested', async () => {
    const user = userEvent.setup();
    renderPage();

    await screen.findByText('Rule A');
    await user.click(within(getRuleRow('Rule A')).getByRole('button', { name: '编辑' }));
    await screen.findByRole('dialog');

    // Run the test on Rule A and confirm the sample table is shown for it.
    await user.click(screen.getByRole('button', { name: '试运行' }));
    await screen.findByText('broker=stale-broker-sample');

    // Save Rule A. The saved dialog keeps no test result, so the next editor must not
    // present Rule A's samples as its own.
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: /编\s*辑/ }));
    await waitFor(() => expect(updateAlertRule).toHaveBeenCalledTimes(1));

    // antd keeps the closed dialog mounted, so drive the second open without waiting for
    // its leave transition, then assert on the editor the user actually sees.
    fireEvent.click(within(getRuleRow('Rule B')).getByRole('button', { name: '编辑' }));
    await screen.findByDisplayValue('Rule B');

    const reopened = screen.getByRole('dialog');
    expect(within(reopened).queryByText('规则试运行结果')).toBeNull();
    expect(within(reopened).queryByText('broker=stale-broker-sample')).toBeNull();
  }, 30_000);

  it.each(['completed', 'pending', 'unchanged', 'metadata'])(
    'keepsTestResultsBoundToTheCurrentThresholdTest: %s',
    async (completion) => {
      const user = userEvent.setup({ pointerEventsCheck: 0 });
      mockBusinessLagRule();
      const samples = lagTestResult();
      let finishTest!: (value: AlertRuleTestResult) => void;
      const pendingTest = new Promise<AlertRuleTestResult>((resolve) => {
        finishTest = resolve;
      });
      vi.mocked(testAlertRule).mockReset();
      vi.mocked(testAlertRule).mockReturnValue(pendingTest);
      const editor = await openLagEditor(user);
      await user.click(within(editor).getByRole('button', { name: '试运行' }));
      await waitFor(() =>
        expect(testAlertRule).toHaveBeenCalledWith(
          expect.objectContaining({ metric: 'consumer.lag.total', threshold: 1000 }),
          'BUSINESS',
        ),
      );
      if (completion !== 'pending') {
        await act(async () => finishTest(samples));
        expect(await within(editor).findByText('规则试运行结果')).toBeInTheDocument();
        expect(within(editor).getByText('命中')).toBeInTheDocument();
      }
      if (completion === 'completed' || completion === 'pending') {
        const threshold = within(editor).getByPlaceholderText('阈值');
        await user.clear(threshold);
        await user.type(threshold, '2000');
        expect(threshold).toHaveValue('2000');
      }
      if (completion === 'metadata') {
        await user.type(within(editor).getByRole('textbox', { name: '规则名称' }), ' updated');
      }
      if (completion === 'pending') {
        await act(async () => finishTest(samples));
      }
      expect(testAlertRule).toHaveBeenCalledTimes(1);
      if (completion === 'unchanged' || completion === 'metadata') {
        expect(within(editor).getByText('规则试运行结果')).toBeInTheDocument();
        expect(within(editor).getByText('命中')).toBeInTheDocument();
      } else {
        expect(within(editor).queryByText('规则试运行结果')).not.toBeInTheDocument();
        expect(within(editor).queryByText('命中')).not.toBeInTheDocument();
      }
    },
  );

  it.each(['success', 'failure'])(
    'keepsTheNewCriteriaTestActiveWhenAnOldRequestSettlesTest: %s',
    async (outcome) => {
      const user = userEvent.setup({ pointerEventsCheck: 0 });
      mockBusinessLagRule();
      let finishEarlier!: (value: AlertRuleTestResult) => void;
      let failEarlier!: (error: Error) => void;
      const earlier = new Promise<AlertRuleTestResult>((resolve, reject) => {
        finishEarlier = resolve;
        failEarlier = reject;
      });
      let finishCurrent!: (value: AlertRuleTestResult) => void;
      const current = new Promise<AlertRuleTestResult>((resolve) => {
        finishCurrent = resolve;
      });
      vi.mocked(testAlertRule).mockReset();
      vi.mocked(testAlertRule).mockReturnValueOnce(earlier).mockReturnValueOnce(current);
      const editor = await openLagEditor(user);
      const testButton = within(editor).getByRole('button', { name: '试运行' });
      await user.click(testButton);
      await waitFor(() => expect(testAlertRule).toHaveBeenCalledTimes(1));
      const threshold = within(editor).getByPlaceholderText('阈值');
      await user.clear(threshold);
      await user.type(threshold, '2000');
      await user.click(testButton);
      await waitFor(() =>
        expect(testAlertRule).toHaveBeenNthCalledWith(
          2,
          expect.objectContaining({ threshold: 2000 }),
          'BUSINESS',
        ),
      );
      await act(async () => {
        if (outcome === 'success') finishEarlier(lagTestResult());
        else failEarlier(new Error('old criteria test failed'));
      });
      expect(testButton).toHaveClass('ant-btn-loading');
      expect(within(editor).queryByText('规则试运行结果')).not.toBeInTheDocument();
      expect(screen.queryByText(/规则试运行失败/)).not.toBeInTheDocument();
      expect(screen.queryByText(/1 个命中：1200/)).not.toBeInTheDocument();
      await act(async () => finishCurrent(lagTestResult(false)));
      expect(await within(editor).findByText('规则试运行结果')).toBeInTheDocument();
      expect(within(editor).getByText('未命中')).toBeInTheDocument();
      expect(testButton).not.toHaveClass('ant-btn-loading');
    },
    // Opening the editor, running two tests and retyping the threshold is a long chain of
    // userEvent interactions; the case above needs the same budget for the same reason.
    30_000,
  );
});
