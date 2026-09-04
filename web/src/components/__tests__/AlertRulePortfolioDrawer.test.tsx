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
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from 'antd';
import type { AlertRule } from '../../api/ops';
import type { Instance } from '../../api/instance';
import { LangProvider } from '../../i18n/LangContext';
import AlertRulePortfolioDrawer from '../AlertRulePortfolioDrawer';
import { downloadCsv } from '../../utils/download';

const opsServiceMocks = vi.hoisted(() => ({ listAlertRules: vi.fn() }));
vi.mock('../../services/opsService', () => opsServiceMocks);
vi.mock('../../utils/download', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../utils/download')>();
  return { ...actual, downloadCsv: vi.fn() };
});

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

const instance: Instance = {
  id: 1,
  name: 'production',
  type: 'DIRECT',
  endpoint: 'nameserver:9876',
  vendor: 'APACHE',
  remark: null,
  topicCount: 1,
  consumerGroupCount: 1,
  gmtCreate: '',
  gmtModified: '',
};

const rule = (overrides: Partial<AlertRule>): AlertRule => ({
  id: 1,
  name: 'Broker unavailable',
  metric: 'broker.availability',
  operator: 'UNAVAILABLE',
  threshold: 0,
  duration: '1m',
  channels: ['dingtalk'],
  enabled: true,
  lastTriggered: null,
  description: '',
  instanceId: 'production',
  ...overrides,
});

const rules = [
  rule({ id: 1, name: 'Broker unavailable' }),
  rule({ id: 2, name: 'Broker down', duration: '60s' }),
  rule({ id: 3, name: 'Lag without notification', metric: 'consumer.lag.total', channels: [] }),
];

const renderDrawer = () =>
  render(
    <App>
      <LangProvider>
        <AlertRulePortfolioDrawer open domain="CLUSTER" instances={[instance]} onClose={vi.fn()} />
      </LangProvider>
    </App>,
  );

describe('AlertRulePortfolioDrawer', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    opsServiceMocks.listAlertRules.mockResolvedValue(rules);
  });

  it('reviews the complete domain inventory and renders issue totals', async () => {
    const user = userEvent.setup();
    renderDrawer();
    await user.click(screen.getByRole('button', { name: '开始审查' }));

    await waitFor(() => expect(opsServiceMocks.listAlertRules).toHaveBeenCalledWith('CLUSTER'));
    expect(await screen.findByText('完全重复评估')).toBeInTheDocument();
    expect(screen.getByText('启用但无通知渠道')).toBeInTheDocument();
    expect(screen.getByText('规则总数')).toBeInTheDocument();
  });

  it('shows a retryable error without stale results', async () => {
    opsServiceMocks.listAlertRules.mockRejectedValue(new Error('unavailable'));
    const user = userEvent.setup();
    renderDrawer();
    await user.click(screen.getByRole('button', { name: '开始审查' }));

    expect(await screen.findByText('全量规则加载失败，请稍后重试')).toBeInTheDocument();
    expect(screen.queryByText('规则总数')).not.toBeInTheDocument();
  });

  it('exports the filtered rule rows rather than the current issue tab', async () => {
    const user = userEvent.setup();
    renderDrawer();
    await user.click(screen.getByRole('button', { name: '开始审查' }));
    await screen.findByText('完全重复评估');
    await user.type(screen.getByLabelText('搜索规则、指标或范围'), 'consumer.lag');
    await user.click(screen.getByRole('button', { name: '导出审查结果' }));

    const [filename, csv] = vi.mocked(downloadCsv).mock.calls[0];
    expect(filename).toBe('rocketmq-cluster-alert-rule-review.csv');
    expect(csv).toContain('Lag without notification');
    expect(csv).not.toContain('Broker unavailable');
  });

  it('can rerun the review after the server-side inventory changes', async () => {
    const user = userEvent.setup();
    renderDrawer();
    await user.click(screen.getByRole('button', { name: '开始审查' }));
    expect(await screen.findByText('完全重复评估')).toBeInTheDocument();

    opsServiceMocks.listAlertRules.mockResolvedValue([rules[0]]);
    await user.click(screen.getByRole('button', { name: '开始审查' }));
    await waitFor(() => expect(opsServiceMocks.listAlertRules).toHaveBeenCalledTimes(2));
    await waitFor(() => expect(screen.queryByText('完全重复评估')).not.toBeInTheDocument());
  });
});
