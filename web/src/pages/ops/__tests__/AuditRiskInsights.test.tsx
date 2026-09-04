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

import { App } from 'antd';
import { render, screen, within } from '@testing-library/react';
import { beforeAll, describe, expect, it, vi } from 'vitest';
import type { AuditSummary } from '../../../api/audit';
import type { AuditRecord } from '../../../api/ops';
import { LangProvider } from '../../../i18n/LangContext';
import AuditRiskInsights from '../AuditRiskInsights';

const summary: AuditSummary = {
  total: 10,
  successful: 5,
  failed: 4,
  partial: 1,
  uniqueOperators: 3,
  latestAt: '2026-08-01 10:00:00',
  byOperation: [],
  byResourceType: [],
};

const records: AuditRecord[] = [
  {
    id: 1,
    timestamp: '2026-08-01 10:05:00',
    operator: 'admin',
    operationType: 'DELETE_TOPIC',
    resourceType: 'TOPIC',
    target: 'orders',
    clusterId: 'prod-cn',
    detail: '',
    result: 'FAILED',
    errorMessage: 'topic busy',
  },
  {
    id: 2,
    timestamp: '2026-08-01 10:06:00',
    operator: 'admin',
    operationType: 'DELETE_TOPIC',
    resourceType: 'TOPIC',
    target: 'orders',
    clusterId: 'prod-cn',
    detail: '',
    result: 'FAILED',
    errorMessage: 'topic busy',
  },
  {
    id: 3,
    timestamp: '2026-08-01 10:07:00',
    operator: 'ops',
    operationType: 'RELOAD_PROXY_CONFIG',
    resourceType: 'PROXY',
    target: '10.0.0.1:8081',
    clusterId: 'prod-cn',
    detail: '',
    result: 'PARTIAL',
    errorMessage: '',
  },
];

const renderPanel = (props = {}) =>
  render(
    <App>
      <LangProvider>
        <AuditRiskInsights summary={summary} records={records} loading={false} {...props} />
      </LangProvider>
    </App>,
  );

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

describe('AuditRiskInsights', () => {
  it('renders filtered risk rates and current-page issue signals', () => {
    renderPanel();

    expect(screen.getByText('审计风险洞察')).toBeInTheDocument();
    expect(screen.getByText('严重')).toBeInTheDocument();
    expect(screen.getByText('失败率')).toBeInTheDocument();
    const failureRateCard = screen.getByText('失败率').closest('.ant-card');
    expect(failureRateCard).not.toBeNull();
    expect(failureRateCard).toHaveTextContent('40.0%');
    expect(screen.getByText('当前筛选共 10 条')).toBeInTheDocument();
    expect(screen.getByText('需要关注的审计信号')).toBeInTheDocument();
    expect(screen.getByText(/当前筛选失败率 40%/u)).toBeInTheDocument();
    expect(screen.getByText(/当前页有 2 条高风险操作失败/u)).toBeInTheDocument();
    expect(screen.getByText(/orders 在当前页出现 2 次失败或部分成功/u)).toBeInTheDocument();
  });

  it('renders repeated target and risky record tables', () => {
    renderPanel();

    const repeatedTargetCard = screen.getByText('重复异常对象').closest('.ant-card');
    expect(repeatedTargetCard).not.toBeNull();
    expect(within(repeatedTargetCard as HTMLElement).getByText('orders')).toBeInTheDocument();
    expect(within(repeatedTargetCard as HTMLElement).getByText('2 / 0')).toBeInTheDocument();
    expect(within(repeatedTargetCard as HTMLElement).getByText('删除 Topic')).toBeInTheDocument();

    const riskyRecordCard = screen.getByText('高风险记录').closest('.ant-card');
    expect(riskyRecordCard).not.toBeNull();
    expect(within(riskyRecordCard as HTMLElement).getAllByText('admin')).toHaveLength(2);
    expect(within(riskyRecordCard as HTMLElement).getAllByText('失败')).not.toHaveLength(0);
  });

  it('shows an empty state when the filtered result has no records', () => {
    renderPanel({
      summary: {
        ...summary,
        total: 0,
        successful: 0,
        failed: 0,
        partial: 0,
      },
      records: [],
    });

    expect(screen.getByText('提示')).toBeInTheDocument();
    expect(screen.getByText('当前筛选没有匹配的审计记录')).toBeInTheDocument();
    expect(screen.getByText('当前页没有重复失败或部分成功的对象')).toBeInTheDocument();
    expect(screen.getByText('当前页没有异常或高风险审计记录')).toBeInTheDocument();
  });

  it('renders a loading skeleton while audit data is refreshing', () => {
    renderPanel({ loading: true });

    expect(screen.getByText('审计风险洞察')).toBeInTheDocument();
    expect(screen.queryByText('失败率')).not.toBeInTheDocument();
  });
});
