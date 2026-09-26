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
import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from 'antd';
import type { Instance } from '../../api/instance';
import type { ConsumerGroup, Topic } from '../../api/metadata';
import { LangProvider } from '../../i18n/LangContext';
import FleetResourceInventoryDrawer from '../FleetResourceInventoryDrawer';
import { downloadCsv } from '../../utils/download';

const topicMocks = vi.hoisted(() => ({ listAllTopics: vi.fn() }));
const groupMocks = vi.hoisted(() => ({ listAllConsumerGroups: vi.fn() }));
vi.mock('../../services/topicService', () => topicMocks);
vi.mock('../../services/consumerService', () => groupMocks);
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

const instances: Instance[] = ['production', 'staging'].map((name, index) => ({
  id: index + 1,
  name,
  remark: null,
  type: 'DIRECT',
  endpoint: `${name}:9876`,
  vendor: index === 0 ? 'APACHE' : 'ALIYUN',
  topicCount: 1,
  consumerGroupCount: 1,
  gmtCreate: '',
  gmtModified: '',
}));

const topic = (instanceId: string): Topic => ({
  name: 'orders',
  instanceId,
  namespace: 'default',
  type: 'NORMAL',
  clusterId: 'DefaultCluster',
  writeQueues: 8,
  readQueues: 8,
  perm: 'RW',
  messageCount: 0,
  tps: 0,
  consumerGroupCount: 1,
  remark: '',
  gmtCreate: '',
  gmtModified: '',
});

const group = (instanceId: string): ConsumerGroup => ({
  name: 'order-workers',
  instanceId,
  namespace: 'default',
  clusterId: 'DefaultCluster',
  subscriptionMode: 'Push',
  consumeType: 'CLUSTERING',
  onlineInstances: 1,
  totalLag: 0,
  subscribedTopics: ['orders'],
  subscriptionDataType: 'NORMAL',
  retryMaxTimes: 16,
  gmtCreate: '',
  gmtModified: '',
  delaySeconds: 0,
  instances: [],
});

const renderDrawer = (
  overrides: Partial<React.ComponentProps<typeof FleetResourceInventoryDrawer>> = {},
) =>
  render(
    <App>
      <LangProvider>
        <FleetResourceInventoryDrawer open instances={instances} onClose={vi.fn()} {...overrides} />
      </LangProvider>
    </App>,
  );

describe('FleetResourceInventoryDrawer', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    topicMocks.listAllTopics.mockImplementation(({ instanceId }: { instanceId: string }) =>
      Promise.resolve([topic(instanceId)]),
    );
    groupMocks.listAllConsumerGroups.mockImplementation(({ instanceId }: { instanceId: string }) =>
      Promise.resolve([group(instanceId)]),
    );
  });

  it('loads both resource kinds for every selected instance', async () => {
    const user = userEvent.setup();
    renderDrawer();
    await user.click(screen.getByRole('button', { name: '加载资源' }));

    await waitFor(() => {
      expect(topicMocks.listAllTopics).toHaveBeenCalledTimes(2);
      expect(groupMocks.listAllConsumerGroups).toHaveBeenCalledTimes(2);
    });
    expect(await screen.findAllByText('orders')).toHaveLength(2);
    expect(screen.getAllByText('order-workers')).toHaveLength(2);
  });

  it('surfaces partial failures while preserving successful resources', async () => {
    topicMocks.listAllTopics.mockRejectedValueOnce(new Error('topic unavailable'));
    const user = userEvent.setup();
    renderDrawer();
    await user.click(screen.getByRole('button', { name: '加载资源' }));

    expect(
      await screen.findByText('有 1 个资源清单加载失败，成功结果仍可检索。'),
    ).toBeInTheDocument();
    expect(screen.getAllByText('order-workers')).toHaveLength(2);
  });

  it('exports the filtered inventory', async () => {
    const user = userEvent.setup();
    renderDrawer();
    await user.click(screen.getByRole('button', { name: '加载资源' }));
    await screen.findAllByText('orders');
    await user.type(screen.getByLabelText('搜索名称、实例、集群或配置'), 'order-workers');
    await user.click(screen.getByRole('button', { name: /导出结果/ }));

    const [filename, csv] = vi.mocked(downloadCsv).mock.calls[0];
    expect(filename).toBe('rocketmq-fleet-resource-inventory.csv');
    expect(csv).toContain('order-workers');
    expect(csv).not.toContain(',orders,');
  });

  it('ignores an in-flight load after the instance fleet changes', async () => {
    let resolveProduction: (topics: Topic[]) => void = () => {};
    const productionTopics = new Promise<Topic[]>((resolve) => {
      resolveProduction = resolve;
    });
    topicMocks.listAllTopics.mockImplementation(({ instanceId }: { instanceId: string }) =>
      instanceId === 'production'
        ? productionTopics
        : Promise.resolve([{ ...topic(instanceId), name: 'staging-only' }]),
    );
    const user = userEvent.setup();
    const { rerender } = renderDrawer();

    await user.click(screen.getByRole('button', { name: '加载资源' }));
    await waitFor(() =>
      expect(topicMocks.listAllTopics).toHaveBeenCalledWith({ instanceId: 'production' }),
    );

    rerender(
      <App>
        <LangProvider>
          <FleetResourceInventoryDrawer open instances={[instances[1]]} onClose={vi.fn()} />
        </LangProvider>
      </App>,
    );
    await user.click(screen.getByRole('button', { name: '加载资源' }));
    expect(await screen.findByText('staging-only')).toBeInTheDocument();

    await act(async () => {
      resolveProduction([{ ...topic('production'), name: 'production-only' }]);
      await productionTopics;
    });

    expect(screen.queryByText('production-only')).not.toBeInTheDocument();
  });

  it('bounds concurrent instance scans', async () => {
    const manyInstances: Instance[] = Array.from({ length: 6 }, (_, index) => ({
      ...instances[0],
      id: index + 1,
      name: `instance-${index + 1}`,
    }));
    let activeTopicLoads = 0;
    let maxActiveTopicLoads = 0;
    topicMocks.listAllTopics.mockImplementation(async ({ instanceId }: { instanceId: string }) => {
      activeTopicLoads += 1;
      maxActiveTopicLoads = Math.max(maxActiveTopicLoads, activeTopicLoads);
      await new Promise((resolve) => window.setTimeout(resolve, 0));
      activeTopicLoads -= 1;
      return [{ ...topic(instanceId), name: `topic-${instanceId}` }];
    });
    const user = userEvent.setup();
    renderDrawer({ instances: manyInstances });

    await user.click(screen.getByRole('button', { name: '加载资源' }));

    expect(await screen.findByText('topic-instance-6')).toBeInTheDocument();
    expect(maxActiveTopicLoads).toBeLessThanOrEqual(4);
    expect(maxActiveTopicLoads).toBeGreaterThan(1);
  });

  it('shows an empty state with no instances', () => {
    renderDrawer({ instances: [] });
    expect(screen.getByText('暂无可检索的实例')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '加载资源' })).toBeDisabled();
  });
});
