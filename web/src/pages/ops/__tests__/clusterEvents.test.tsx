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

import { render, screen, waitFor } from '@testing-library/react';
import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import type { AuditRecord } from '../../../api/ops';
import { listAuditRecords } from '../../../api/ops';
import { LangProvider } from '../../../i18n/LangContext';
import ClusterEventsTab, { CLUSTER_EVENT_OPERATIONS } from '../clusterEvents';

vi.mock('../../../api/ops', () => ({
  listAuditRecords: vi.fn(),
}));

const record = (id: number): AuditRecord => ({
  id,
  timestamp: '2026-10-28T06:00:00',
  operator: 'rocketmq',
  operationType: 'UPDATE_BROKER_CONFIG',
  resourceType: 'BROKER',
  target: 'broker-a',
  clusterId: 'cluster-1',
  detail: 'brokerRole=SYNC',
  result: 'SUCCESS',
  errorMessage: '',
});

const renderTab = () =>
  render(
    <MemoryRouter>
      <LangProvider>
        <ClusterEventsTab />
      </LangProvider>
    </MemoryRouter>,
  );

beforeAll(() => {
  // antd's responsive Table/Pagination subscribes to matchMedia, which jsdom lacks.
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

describe('ClusterEventsTab', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(listAuditRecords).mockResolvedValue({
      items: [record(1)],
      total: 1,
      page: 1,
      size: 20,
    });
  });

  it('queries the audit log restricted to the cluster control-plane operations', async () => {
    renderTab();

    await waitFor(() =>
      expect(listAuditRecords).toHaveBeenCalledWith({
        operationTypes: CLUSTER_EVENT_OPERATIONS,
        page: 1,
        pageSize: 20,
      }),
    );
    expect(CLUSTER_EVENT_OPERATIONS).toContain('UPDATE_BROKER_CONFIG');
  });

  it('renders the localized event name and the record facts', async () => {
    renderTab();

    // 事件列用本地化操作名（更新 Broker 配置），不裸显内部码。
    expect(await screen.findByText('更新 Broker 配置')).toBeInTheDocument();
    expect(screen.queryByText('UPDATE_BROKER_CONFIG')).not.toBeInTheDocument();
    expect(screen.getByText('broker-a')).toBeInTheDocument();
    expect(screen.getByText('cluster-1')).toBeInTheDocument();
    expect(screen.getByText('rocketmq')).toBeInTheDocument();
    expect(screen.getByText('成功')).toBeInTheDocument();
    expect(screen.getByText('共 1 条事件')).toBeInTheDocument();
  });
});
