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

import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { StrictMode } from 'react';
import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App, message } from 'antd';
import type { Instance } from '../../api/instance';
import type { Topic } from '../../api/metadata';
import { LangProvider } from '../../i18n/LangContext';
import TopicConfigComparisonModal from '../TopicConfigComparisonModal';
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

const renderModal = (
  overrides: Partial<React.ComponentProps<typeof TopicConfigComparisonModal>> = {},
) =>
  render(
    <App>
      <LangProvider>
        <TopicConfigComparisonModal
          open
          instances={instances}
          currentInstanceId="production"
          onClose={vi.fn()}
          {...overrides}
        />
      </LangProvider>
    </App>,
  );

describe('TopicConfigComparisonModal', () => {
  afterEach(() => vi.restoreAllMocks());
  beforeEach(() => {
    vi.clearAllMocks();
    topicServiceMocks.listAllTopics.mockImplementation(({ instanceId }: { instanceId: string }) =>
      Promise.resolve(instanceId === 'production' ? productionTopics : stagingTopics),
    );
  });

  it('renders as a modal instead of a side drawer', () => {
    renderModal();

    expect(document.querySelector('.ant-modal')).not.toBeNull();
    expect(document.querySelector('.ant-drawer')).toBeNull();
  });

  it('loads both complete inventories and summarizes all comparison states', async () => {
    const user = userEvent.setup();
    renderModal();

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
    renderModal();
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
    renderModal();
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
    renderModal();

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
    renderModal({ instances: [instances[0]] });

    expect(screen.getByText('至少需要两个实例才能进行配置对比')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '开始对比' })).toBeDisabled();
  });

  it('keeps the previous result empty when inventory loading fails', async () => {
    topicServiceMocks.listAllTopics.mockRejectedValue(new Error('offline'));
    const user = userEvent.setup();
    renderModal();

    await user.click(screen.getByRole('button', { name: '开始对比' }));

    await waitFor(() => expect(topicServiceMocks.listAllTopics).toHaveBeenCalledTimes(2));
    expect(screen.queryByText('配置一致')).not.toBeInTheDocument();
  });

  it('clears a previous comparison when refreshing the same pair fails', async () => {
    const user = userEvent.setup();
    renderModal();
    const compareButton = screen.getByRole('button', { name: '开始对比' });

    await user.click(compareButton);
    expect(await screen.findByText('matching-topic')).toBeInTheDocument();

    topicServiceMocks.listAllTopics.mockRejectedValue(new Error('offline'));
    await user.click(compareButton);
    await waitFor(() => expect(topicServiceMocks.listAllTopics).toHaveBeenCalledTimes(4));
    await waitFor(() => expect(compareButton).not.toHaveClass('ant-btn-loading'));

    expect(screen.queryByText('matching-topic')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /导出结果/ })).not.toBeInTheDocument();
  });

  it('discards a comparison that resolves after the instance pair changed', async () => {
    const archiveInstance: Instance = { ...instances[1], id: 3, name: 'archive' };
    const pending = new Map<string, (value: Topic[]) => void>();
    topicServiceMocks.listAllTopics.mockImplementation(
      ({ instanceId }: { instanceId: string }) =>
        new Promise<Topic[]>((resolve) => {
          pending.set(instanceId, resolve);
        }),
    );
    const user = userEvent.setup();
    renderModal({ instances: [...instances, archiveInstance] });
    // Hold the node: while it is loading, the spinner contributes to its accessible name.
    const compareButton = screen.getByRole('button', { name: '开始对比' });

    await user.click(compareButton);
    await waitFor(() => expect(pending.size).toBe(2));
    expect(compareButton).toHaveClass('ant-btn-loading');

    // Change the source instance while the production/staging comparison is still in flight.
    await user.click(screen.getByRole('combobox', { name: '源实例' }));
    await user.click(await screen.findByTitle('archive'));

    await act(async () => {
      pending.get('production')?.(productionTopics);
      pending.get('staging')?.(stagingTopics);
    });

    expect(screen.queryByText('配置一致')).not.toBeInTheDocument();
    expect(screen.queryByText('source-only-topic')).not.toBeInTheDocument();
    // Changing the pair must also end the in-flight indicator, so the control cannot stay spinning.
    await waitFor(() => expect(compareButton).not.toHaveClass('ant-btn-loading'));
  });

  it('filters the result when a status card is clicked', async () => {
    const user = userEvent.setup();
    renderModal();

    // 未开始对比时给可操作的空态，而不是一片空白
    expect(
      screen.getByText('选择源实例与目标实例，点「开始对比」后按 Topic 逐条列出配置差异。'),
    ).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '开始对比' }));
    await screen.findByText('matching-topic');

    await user.click(screen.getByRole('button', { name: '按配置漂移筛选' }));
    expect(screen.getByText('drifted-topic')).toBeInTheDocument();
    expect(screen.queryByText('matching-topic')).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '清除状态筛选' }));
    expect(await screen.findByText('matching-topic')).toBeInTheDocument();
  });

  it('calls onClose from the modal close control', async () => {
    const onClose = vi.fn();
    const user = userEvent.setup();
    renderModal({ onClose });

    await user.click(screen.getByRole('button', { name: 'Close' }));

    expect(onClose).toHaveBeenCalledTimes(1);
  });
  it.each(['production', 'staging'])(
    'doesNotNotifyAfterComparisonUnmountFrom%sTest',
    async (failedInstance) => {
      let rejectSource!: (error: Error) => void;
      topicServiceMocks.listAllTopics.mockImplementation(
        ({ instanceId }: { instanceId: string }) =>
          instanceId === failedInstance
            ? new Promise<Topic[]>((_resolve, reject) => {
                rejectSource = reject;
              })
            : Promise.resolve(stagingTopics),
      );
      const errorMessage = vi
        .spyOn(message, 'error')
        .mockImplementation(() => (() => {}) as ReturnType<typeof message.error>);
      const user = userEvent.setup();
      const view = renderModal();
      await user.click(screen.getByRole('button', { name: '开始对比' }));
      await waitFor(() => expect(topicServiceMocks.listAllTopics).toHaveBeenCalledTimes(2));
      view.unmount();
      await act(async () => {
        rejectSource(new Error('offline'));
      });
      expect(errorMessage).not.toHaveBeenCalled();
    },
  );

  it('keepsCurrentComparisonFailureNotificationsInStrictModeTest', async () => {
    const errorMessage = vi
      .spyOn(message, 'error')
      .mockImplementation(() => (() => {}) as ReturnType<typeof message.error>);
    topicServiceMocks.listAllTopics.mockRejectedValue(new Error('offline'));
    const user = userEvent.setup();
    render(
      <StrictMode>
        <App>
          <LangProvider>
            <TopicConfigComparisonModal
              open
              instances={instances}
              currentInstanceId="production"
              onClose={vi.fn()}
            />
          </LangProvider>
        </App>
      </StrictMode>,
    );
    await user.click(screen.getByRole('button', { name: '开始对比' }));
    await waitFor(() => expect(errorMessage).toHaveBeenCalledTimes(1));
    expect(errorMessage).toHaveBeenCalledWith('Topic 配置加载失败，请稍后重试');
  });

  it('doesNotNotifyFromOldComparisonAfterReopeningTest', async () => {
    let rejectSource!: (error: Error) => void;
    topicServiceMocks.listAllTopics
      .mockImplementationOnce(
        () =>
          new Promise<Topic[]>((_resolve, reject) => {
            rejectSource = reject;
          }),
      )
      .mockResolvedValueOnce(stagingTopics);
    const errorMessage = vi
      .spyOn(message, 'error')
      .mockImplementation(() => (() => {}) as ReturnType<typeof message.error>);
    const user = userEvent.setup();
    const oldView = renderModal();
    await user.click(screen.getByRole('button', { name: '开始对比' }));
    await waitFor(() => expect(topicServiceMocks.listAllTopics).toHaveBeenCalledTimes(2));
    oldView.unmount();
    renderModal();
    await user.click(screen.getByRole('button', { name: '开始对比' }));
    expect(await screen.findByText('matching-topic')).toBeInTheDocument();
    await act(async () => {
      rejectSource(new Error('old comparison failed'));
    });
    expect(errorMessage).not.toHaveBeenCalled();
    expect(screen.getByText('matching-topic')).toBeInTheDocument();
  });
});
