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
import type { Instance } from '../../api/instance';
import type { ConsumerGroup } from '../../api/metadata';
import { LangProvider } from '../../i18n/LangContext';
import ConsumerGroupConfigComparisonDrawer from '../ConsumerGroupConfigComparisonDrawer';
import { downloadCsv } from '../../utils/download';

const consumerServiceMocks = vi.hoisted(() => ({ listAllConsumerGroups: vi.fn() }));
vi.mock('../../services/consumerService', () => consumerServiceMocks);
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

const instances: Instance[] = [
  {
    id: 1,
    name: 'production',
    remark: null,
    type: 'DIRECT',
    endpoint: 'nameserver-a:9876',
    vendor: 'APACHE',
    topicCount: 0,
    consumerGroupCount: 3,
    gmtCreate: '2026-09-01 00:00:00',
    gmtModified: '2026-09-01 00:00:00',
  },
  {
    id: 2,
    name: 'staging',
    remark: null,
    type: 'DIRECT',
    endpoint: 'nameserver-b:9876',
    vendor: 'APACHE',
    topicCount: 0,
    consumerGroupCount: 3,
    gmtCreate: '2026-09-01 00:00:00',
    gmtModified: '2026-09-01 00:00:00',
  },
];

const group = (name: string, instanceId: string, overrides: Partial<ConsumerGroup> = {}) => ({
  name,
  namespace: 'default',
  clusterId: `${instanceId}-cluster`,
  instanceId,
  subscriptionMode: 'Push',
  consumeType: 'CLUSTERING',
  onlineInstances: 0,
  totalLag: 0,
  subscribedTopics: [],
  subscriptionDataType: 'NORMAL',
  deliveryOrderType: 'CONCURRENTLY',
  retryMaxTimes: 16,
  gmtCreate: '2026-09-01 00:00:00',
  gmtModified: '2026-09-01 00:00:00',
  delaySeconds: 0,
  instances: [],
  ...overrides,
});

const productionGroups = [
  group('matching-group', 'production'),
  group('drifted-group', 'production'),
  group('source-only-group', 'production'),
];
const stagingGroups = [
  group('matching-group', 'staging'),
  group('drifted-group', 'staging', { retryMaxTimes: 32 }),
  group('target-only-group', 'staging'),
];

const renderDrawer = (
  overrides: Partial<React.ComponentProps<typeof ConsumerGroupConfigComparisonDrawer>> = {},
) =>
  render(
    <App>
      <LangProvider>
        <ConsumerGroupConfigComparisonDrawer
          open
          instances={instances}
          currentInstanceId="production"
          onClose={vi.fn()}
          {...overrides}
        />
      </LangProvider>
    </App>,
  );

describe('ConsumerGroupConfigComparisonDrawer', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    consumerServiceMocks.listAllConsumerGroups.mockImplementation(
      ({ instanceId }: { instanceId: string }) =>
        Promise.resolve(instanceId === 'production' ? productionGroups : stagingGroups),
    );
  });

  it('loads complete inventories and summarizes comparison states', async () => {
    const user = userEvent.setup();
    renderDrawer();

    await user.click(screen.getByRole('button', { name: '开始对比' }));

    await waitFor(() =>
      expect(consumerServiceMocks.listAllConsumerGroups).toHaveBeenCalledTimes(2),
    );
    for (const name of [
      'matching-group',
      'drifted-group',
      'source-only-group',
      'target-only-group',
    ]) {
      expect(await screen.findByText(name)).toBeInTheDocument();
    }
    for (const label of ['配置一致', '配置漂移', '仅源实例', '仅目标实例']) {
      expect(
        screen.getByText(label, { selector: '.ant-statistic-title' }).parentElement,
      ).toHaveTextContent('1');
    }
  });

  it('shows field-level values for drift', async () => {
    const user = userEvent.setup();
    renderDrawer();
    await user.click(screen.getByRole('button', { name: '开始对比' }));
    await screen.findByText('drifted-group');

    await user.click(screen.getAllByRole('button', { name: 'Expand row' })[0]);

    expect(await screen.findByText('最大重试次数')).toBeInTheDocument();
    expect(screen.getByText('16')).toBeInTheDocument();
    expect(screen.getByText('32')).toBeInTheDocument();
  });

  it('exports only filtered rows', async () => {
    const user = userEvent.setup();
    renderDrawer();
    await user.click(screen.getByRole('button', { name: '开始对比' }));
    await screen.findByText('source-only-group');
    await user.type(screen.getByLabelText('搜索 Group 名称'), 'source-only');
    await user.click(screen.getByRole('button', { name: /导出结果/ }));

    const [filename, csv] = vi.mocked(downloadCsv).mock.calls[0];
    expect(filename).toBe('rocketmq-consumer-group-config-production-vs-staging.csv');
    expect(csv).toContain('source-only-group');
    expect(csv).not.toContain('target-only-group');
  });

  it('swaps source and target before loading', async () => {
    const user = userEvent.setup();
    renderDrawer();
    await user.click(screen.getByRole('button', { name: '交换源实例和目标实例' }));
    await user.click(screen.getByRole('button', { name: '开始对比' }));

    await waitFor(() => {
      expect(consumerServiceMocks.listAllConsumerGroups.mock.calls[0][0]).toEqual({
        instanceId: 'staging',
      });
      expect(consumerServiceMocks.listAllConsumerGroups.mock.calls[1][0]).toEqual({
        instanceId: 'production',
      });
    });
  });

  it('shows the two-instance requirement and disables comparison', () => {
    renderDrawer({ instances: [instances[0]] });
    expect(screen.getByText('至少需要两个实例才能进行配置对比')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '开始对比' })).toBeDisabled();
  });

  it('does not display stale summaries after loading fails', async () => {
    consumerServiceMocks.listAllConsumerGroups.mockRejectedValue(new Error('offline'));
    const user = userEvent.setup();
    renderDrawer();
    await user.click(screen.getByRole('button', { name: '开始对比' }));

    await waitFor(() =>
      expect(consumerServiceMocks.listAllConsumerGroups).toHaveBeenCalledTimes(2),
    );
    expect(screen.queryByText('配置一致')).not.toBeInTheDocument();
  });
});
