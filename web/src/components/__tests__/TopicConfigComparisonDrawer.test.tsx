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
import type { Topic } from '../../api/metadata';
import { LangProvider } from '../../i18n/LangContext';
import TopicConfigComparisonDrawer from '../TopicConfigComparisonDrawer';
import { downloadCsv } from '../../utils/download';

const topicServiceMocks = vi.hoisted(() => ({
  listAllTopics: vi.fn(),
}));

vi.mock('../../services/topicService', () => topicServiceMocks);
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
    topicCount: 2,
    consumerGroupCount: 0,
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
    topicCount: 2,
    consumerGroupCount: 0,
    gmtCreate: '2026-09-01 00:00:00',
    gmtModified: '2026-09-01 00:00:00',
  },
];

const topic = (name: string, instanceId: string, overrides: Partial<Topic> = {}): Topic => ({
  name,
  namespace: 'default',
  type: 'NORMAL',
  clusterId: `${instanceId}-cluster`,
  instanceId,
  writeQueues: 8,
  readQueues: 8,
  perm: 'RW',
  messageCount: 0,
  tps: 0,
  consumerGroupCount: 0,
  remark: '',
  gmtCreate: '2026-09-01 00:00:00',
  gmtModified: '2026-09-01 00:00:00',
  ...overrides,
});

const productionTopics = [
  topic('matching-topic', 'production'),
  topic('drifted-topic', 'production'),
  topic('source-only-topic', 'production'),
];
const stagingTopics = [
  topic('matching-topic', 'staging'),
  topic('drifted-topic', 'staging', { writeQueues: 16 }),
  topic('target-only-topic', 'staging'),
];

const renderDrawer = (
  overrides: Partial<React.ComponentProps<typeof TopicConfigComparisonDrawer>> = {},
) =>
  render(
    <App>
      <LangProvider>
        <TopicConfigComparisonDrawer
          open
          instances={instances}
          currentInstanceId="production"
          onClose={vi.fn()}
          {...overrides}
        />
      </LangProvider>
    </App>,
  );

describe('TopicConfigComparisonDrawer', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    topicServiceMocks.listAllTopics.mockImplementation(({ instanceId }: { instanceId: string }) =>
      Promise.resolve(instanceId === 'production' ? productionTopics : stagingTopics),
    );
  });

  it('loads both complete inventories and summarizes all comparison states', async () => {
    const user = userEvent.setup();
    renderDrawer();

    await user.click(screen.getByRole('button', { name: '开始对比' }));

    await waitFor(() => {
      expect(topicServiceMocks.listAllTopics).toHaveBeenCalledWith({ instanceId: 'production' });
      expect(topicServiceMocks.listAllTopics).toHaveBeenCalledWith({ instanceId: 'staging' });
    });
    expect(await screen.findByText('matching-topic')).toBeInTheDocument();
    expect(screen.getByText('drifted-topic')).toBeInTheDocument();
    expect(screen.getByText('source-only-topic')).toBeInTheDocument();
    expect(screen.getByText('target-only-topic')).toBeInTheDocument();
    for (const label of ['配置一致', '配置漂移', '仅源实例', '仅目标实例']) {
      expect(
        screen.getByText(label, { selector: '.ant-statistic-title' }).parentElement,
      ).toHaveTextContent('1');
    }
  });

  it('shows field-level values when a drifted row is expanded', async () => {
    const user = userEvent.setup();
    renderDrawer();
    await user.click(screen.getByRole('button', { name: '开始对比' }));
    await screen.findByText('drifted-topic');

    const expandButtons = screen.getAllByRole('button', { name: 'Expand row' });
    await user.click(expandButtons[0]);

    expect(await screen.findByText('写队列数')).toBeInTheDocument();
    expect(screen.getByText('8')).toBeInTheDocument();
    expect(screen.getByText('16')).toBeInTheDocument();
  });

  it('filters exported rows by topic search', async () => {
    const user = userEvent.setup();
    renderDrawer();
    await user.click(screen.getByRole('button', { name: '开始对比' }));
    await screen.findByText('source-only-topic');

    await user.type(screen.getByLabelText('搜索 Topic 名称'), 'source-only');
    await user.click(screen.getByRole('button', { name: /导出结果/ }));

    expect(downloadCsv).toHaveBeenCalledTimes(1);
    const [filename, csv] = vi.mocked(downloadCsv).mock.calls[0];
    expect(filename).toBe('rocketmq-topic-config-production-vs-staging.csv');
    expect(csv).toContain('source-only-topic');
    expect(csv).not.toContain('target-only-topic');
  });

  it('swaps source and target before comparison', async () => {
    const user = userEvent.setup();
    renderDrawer();

    await user.click(screen.getByRole('button', { name: '交换源实例和目标实例' }));
    await user.click(screen.getByRole('button', { name: '开始对比' }));

    await waitFor(() => {
      expect(topicServiceMocks.listAllTopics.mock.calls[0][0]).toEqual({ instanceId: 'staging' });
      expect(topicServiceMocks.listAllTopics.mock.calls[1][0]).toEqual({
        instanceId: 'production',
      });
    });
  });

  it('shows an actionable empty state when only one instance exists', () => {
    renderDrawer({ instances: [instances[0]] });

    expect(screen.getByText('至少需要两个实例才能进行配置对比')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '开始对比' })).toBeDisabled();
  });

  it('keeps the previous result empty when inventory loading fails', async () => {
    topicServiceMocks.listAllTopics.mockRejectedValue(new Error('offline'));
    const user = userEvent.setup();
    renderDrawer();

    await user.click(screen.getByRole('button', { name: '开始对比' }));

    await waitFor(() => expect(topicServiceMocks.listAllTopics).toHaveBeenCalledTimes(2));
    expect(screen.queryByText('配置一致')).not.toBeInTheDocument();
  });

  it('calls onClose from the drawer close control', async () => {
    const onClose = vi.fn();
    const user = userEvent.setup();
    renderDrawer({ onClose });

    await user.click(screen.getByRole('button', { name: 'Close' }));

    expect(onClose).toHaveBeenCalledTimes(1);
  });
});
