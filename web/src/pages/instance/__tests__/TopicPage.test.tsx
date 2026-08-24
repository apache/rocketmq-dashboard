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

import { describe, it, expect, vi, beforeAll, beforeEach, afterEach } from 'vitest';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { App } from 'antd';
import { LangProvider } from '../../../i18n/LangContext';
import type { Topic } from '../../../api/metadata';
import { parseMessageProperties } from '../../../utils/messageProperties';
import TopicPage from '../topic';

const topicServiceMocks = vi.hoisted(() => ({
  batchDeleteTopics: vi.fn(),
  createTopic: vi.fn(),
  deleteTopic: vi.fn(),
  getTopicConsumers: vi.fn(),
  getTopicConsumerPage: vi.fn(),
  getTopicRoutes: vi.fn(),
  listTopics: vi.fn(),
  listTopicsPage: vi.fn(),
  sendTopicMessage: vi.fn(),
}));

const instanceServiceMocks = vi.hoisted(() => ({
  listInstances: vi.fn(),
}));

vi.mock('../../../services/topicService', () => topicServiceMocks);

const mockTopicsList = (items: Topic[]) =>
  topicServiceMocks.listTopicsPage.mockResolvedValue({
    items,
    total: items.length,
    page: 1,
    size: 20,
  });
vi.mock('../../../services/instanceService', () => instanceServiceMocks);

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
  Object.defineProperty(URL, 'createObjectURL', {
    writable: true,
    value: vi.fn(() => 'blob:topic-export'),
  });
  Object.defineProperty(URL, 'revokeObjectURL', {
    writable: true,
    value: vi.fn(),
  });
});

const buildTopics = (count: number): Topic[] =>
  Array.from({ length: count }, (_, index) => {
    const suffix = String(index + 1).padStart(2, '0');
    return {
      name: `topic-${suffix}`,
      namespace: 'default',
      type: 'NORMAL',
      clusterId: 'rmq-cn-v5-prod-01',
      instanceId: 'instance-proxy-1',
      writeQueues: 8,
      readQueues: 8,
      perm: 'RW',
      messageCount: index,
      tps: index,
      consumerGroupCount: 0,
      remark: `Topic ${suffix}`,
      gmtCreate: '2026-01-01T00:00:00Z',
      gmtModified: '2026-01-01T00:00:00Z',
    };
  });

const selectedInstance = {
  id: 5,
  name: 'instance-proxy-1',
  remark: '',
  type: 'PROXY_CLUSTER' as const,
  endpoint: '10.0.2.21:8080',
  topicCount: 0,
  consumerGroupCount: 0,
  gmtCreate: '2026-01-01T00:00:00Z',
  gmtModified: '2026-01-01T00:00:00Z',
};

const renderWithProviders = (initialEntry = '/instance/topic') =>
  render(
    <App>
      <LangProvider>
        <MemoryRouter initialEntries={[initialEntry]}>
          <Routes>
            <Route path="/instance/topic" element={<TopicPage />} />
            <Route path="/instance/:instanceId/topic" element={<TopicPage />} />
          </Routes>
        </MemoryRouter>
      </LangProvider>
    </App>,
  );

const getTableBody = () => {
  const tableBody = document.querySelector('.ant-table-tbody');
  expect(tableBody).not.toBeNull();
  return tableBody as HTMLElement;
};

describe('TopicPage', () => {
  beforeEach(() => {
    mockTopicsList(buildTopics(25));
    topicServiceMocks.batchDeleteTopics.mockResolvedValue({ deleted: [], failed: [] });
    topicServiceMocks.createTopic.mockImplementation(async (data: Partial<Topic>) => ({
      ...buildTopics(1)[0],
      ...data,
      namespace: 'default',
      clusterId: 'server-cluster',
      messageCount: 0,
      tps: 0,
      consumerGroupCount: 0,
      gmtCreate: '2026-01-02T00:00:00Z',
      gmtModified: '2026-01-02T00:00:00Z',
    }));
    topicServiceMocks.getTopicRoutes.mockResolvedValue([]);
    topicServiceMocks.getTopicConsumers.mockResolvedValue([]);
    topicServiceMocks.getTopicConsumerPage.mockResolvedValue({
      items: [],
      total: 0,
      page: 1,
      pageSize: 20,
    });
    instanceServiceMocks.listInstances.mockResolvedValue([
      {
        id: 5,
        name: 'instance-proxy-1',
        remark: '',
        type: 'PROXY_CLUSTER',
        endpoint: '10.0.2.21:8080',
        topicCount: 1,
        consumerGroupCount: 0,
        gmtCreate: '2026-01-01T00:00:00Z',
        gmtModified: '2026-01-01T00:00:00Z',
      },
    ]);
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it('shows the selected Proxy deployment type explicitly', async () => {
    instanceServiceMocks.listInstances.mockResolvedValue([
      { ...selectedInstance, type: 'PROXY_LOCAL' },
    ]);

    renderWithProviders('/instance/instance-proxy-1/topic');

    expect(await screen.findByText(/Proxy Local/)).toBeInTheDocument();
    expect(screen.getByText(/与 Broker 同进程部署的 Proxy 地址/)).toBeInTheDocument();
  });

  it('ignores duplicate Topic creates while the first request is pending', async () => {
    topicServiceMocks.createTopic.mockImplementation(() => new Promise(() => {}));
    const user = userEvent.setup();
    renderWithProviders();

    expect(await screen.findByText('topic-01')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /创建 Topic/ }));
    const dialog = await screen.findByRole('dialog');
    await user.type(within(dialog).getByLabelText('Topic 名称'), 'new-topic');
    const create = within(dialog).getByRole('button', { name: /创\s*建/ });

    fireEvent.click(create);
    fireEvent.click(create);

    await waitFor(() => expect(topicServiceMocks.createTopic).toHaveBeenCalledTimes(1));
    expect(create).toHaveClass('ant-btn-loading');
  });

  it('downloads the currently filtered topics when exporting', async () => {
    const user = userEvent.setup();
    const clickSpy = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(vi.fn());
    let exportedBlob: Blob | undefined;
    vi.mocked(URL.createObjectURL).mockImplementation((blob) => {
      exportedBlob = blob as Blob;
      return 'blob:topic-export';
    });
    mockTopicsList([
      {
        ...buildTopics(1)[0],
        name: 'orders-topic',
        namespace: 'trade',
        remark: '\t=orders, "critical"',
      },
      {
        ...buildTopics(1)[0],
        name: 'users-topic',
        namespace: 'user',
        remark: '=formula-risk',
      },
    ]);
    renderWithProviders();

    expect(await screen.findByText('orders-topic')).toBeInTheDocument();
    await user.type(screen.getByPlaceholderText('搜索 Topic 名称'), 'orders');
    await user.keyboard('{Enter}');
    await waitFor(() => expect(screen.queryByText('users-topic')).not.toBeInTheDocument());
    await user.click(screen.getByRole('button', { name: /导出/ }));

    expect(URL.createObjectURL).toHaveBeenCalledTimes(1);
    expect(clickSpy).toHaveBeenCalledTimes(1);
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:topic-export');
    expect(document.querySelector('a[download^="rocketmq-topics-"]')).not.toBeInTheDocument();

    expect(exportedBlob).toBeDefined();
    const csv = await exportedBlob!.text();
    expect(csv).toContain('"orders-topic"');
    expect(csv).toContain('"\'\t=orders, ""critical"""');
    expect(csv).not.toContain('users-topic');
    clickSpy.mockRestore();
  });

  it('keeps the current table page after opening and closing topic details', async () => {
    const user = userEvent.setup();
    instanceServiceMocks.listInstances.mockResolvedValue([
      {
        id: 6,
        name: 'instance-a',
        type: 'DIRECT',
        endpoint: '127.0.0.1:9876',
        remark: '',
        topicCount: 25,
        consumerGroupCount: 0,
        gmtCreate: '2026-01-01T00:00:00Z',
        gmtModified: '2026-01-01T00:00:00Z',
      },
    ]);
    mockTopicsList(buildTopics(25).map((topic) => ({ ...topic, instanceId: 'instance-a' })));
    renderWithProviders('/instance/instance-a/topic');

    expect(await screen.findByText('topic-01')).toBeInTheDocument();

    const secondPage = document.querySelector('.ant-pagination-item-2');
    expect(secondPage).not.toBeNull();
    await user.click(secondPage as HTMLElement);

    await waitFor(() => expect(within(getTableBody()).getByText('topic-21')).toBeInTheDocument());
    expect(within(getTableBody()).queryByText('topic-01')).not.toBeInTheDocument();

    await user.click(screen.getAllByRole('button', { name: /详情/ })[0]);
    await waitFor(() =>
      expect(topicServiceMocks.getTopicRoutes).toHaveBeenCalledWith('topic-21', 'instance-a'),
    );
    expect(topicServiceMocks.getTopicConsumerPage).toHaveBeenCalledWith(
      'topic-21',
      'instance-a',
      1,
      20,
    );

    const closeButton = document.querySelector('.ant-modal-close');
    expect(closeButton).not.toBeNull();
    await user.click(closeButton as HTMLElement);

    expect(within(getTableBody()).getByText('topic-21')).toBeInTheDocument();
    expect(within(getTableBody()).queryByText('topic-01')).not.toBeInTheDocument();
  });

  it('keeps the selected instance when rebuilding a topic without a broker route', async () => {
    const user = userEvent.setup();
    const topic = { ...buildTopics(1)[0], instanceId: 'instance-a' };
    instanceServiceMocks.listInstances.mockResolvedValue([
      {
        ...selectedInstance,
        id: 6,
        name: 'instance-a',
        type: 'DIRECT',
      },
    ]);
    mockTopicsList([topic]);
    renderWithProviders('/instance/instance-a/topic');

    expect(await screen.findByText('topic-01')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /详情/ }));
    await user.click(await screen.findByRole('button', { name: '在 Broker 上重建' }));

    await waitFor(() =>
      expect(topicServiceMocks.createTopic).toHaveBeenCalledWith({
        name: 'topic-01',
        type: 'NORMAL',
        writeQueues: 8,
        readQueues: 8,
        instanceId: 'instance-a',
      }),
    );
    expect(topicServiceMocks.getTopicRoutes).toHaveBeenLastCalledWith('topic-01', 'instance-a');
  });

  it('keeps failed topics selected after a partially successful batch deletion', async () => {
    const user = userEvent.setup();
    mockTopicsList(buildTopics(3));
    topicServiceMocks.batchDeleteTopics.mockResolvedValue({
      deleted: ['topic-01', 'topic-03'],
      failed: ['topic-02'],
    });
    renderWithProviders();

    expect(await screen.findByText('topic-01')).toBeInTheDocument();
    await user.click(screen.getAllByRole('checkbox')[0]);
    await user.click(screen.getByRole('button', { name: /删除 \(3\)$/ }));

    const dialog = await screen.findByRole('dialog');
    await user.click(within(dialog).getByRole('button', { name: /删\s*除/ }));

    await waitFor(() => expect(screen.queryByText('topic-01')).not.toBeInTheDocument());
    expect(screen.getByText('topic-02')).toBeInTheDocument();
    expect(screen.queryByText('topic-03')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: /删除 \(1\)$/ })).toBeInTheDocument();
    expect(screen.getByText('已删除 2 个 Topic，1 个删除失败')).toBeInTheDocument();
  });

  it('filters topics by the instance from the route and shows its endpoint', async () => {
    const base = buildTopics(1)[0];
    mockTopicsList([
      { ...base, name: 'topic-a', instanceId: 'instance-proxy-1' },
      { ...base, name: 'topic-b', instanceId: 'instance-proxy-2' },
    ]);
    instanceServiceMocks.listInstances.mockResolvedValue([
      {
        id: 5,
        name: 'instance-proxy-1',
        remark: '',
        type: 'PROXY_CLUSTER',
        endpoint: '10.0.2.21:8080',
        topicCount: 1,
        consumerGroupCount: 0,
        gmtCreate: '2026-01-01T00:00:00Z',
        gmtModified: '2026-01-01T00:00:00Z',
      },
      {
        id: 7,
        name: 'instance-proxy-2',
        remark: '',
        type: 'PROXY_CLUSTER',
        endpoint: '10.0.2.22:8080',
        topicCount: 1,
        consumerGroupCount: 0,
        gmtCreate: '2026-01-01T00:00:00Z',
        gmtModified: '2026-01-01T00:00:00Z',
      },
    ]);

    renderWithProviders('/instance/instance-proxy-1/topic');

    expect(await screen.findByText('topic-a')).toBeInTheDocument();
    expect(screen.queryByText('topic-b')).not.toBeInTheDocument();
    expect(screen.getByText('10.0.2.21:8080')).toBeInTheDocument();
  });

  it('imports valid topic CSV rows through the create service with the selected instance', async () => {
    const user = userEvent.setup();
    mockTopicsList([]);
    instanceServiceMocks.listInstances.mockResolvedValue([selectedInstance]);
    renderWithProviders('/instance/instance-proxy-1/topic');

    await screen.findByText(/共 0 个 Topic/);
    const csv = [
      '"Name","Namespace","Type","Cluster ID","Write Queues","Read Queues","Permission","Remark"',
      '"imported-topic","ignored","NORMAL","ignored-cluster","4","6","RW","orders"',
    ].join('\n');
    await user.upload(screen.getByTestId('topic-import-file'), new File([csv], 'topics.csv'));
    expect(await screen.findByText('检测到 1 个 Topic，将按顺序调用创建接口')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '开始导入' }));

    await waitFor(() =>
      expect(topicServiceMocks.createTopic).toHaveBeenCalledWith({
        name: 'imported-topic',
        type: 'NORMAL',
        writeQueues: 4,
        readQueues: 6,
        perm: 'RW',
        remark: 'orders',
        instanceId: 'instance-proxy-1',
      }),
    );
    expect(await screen.findByText('已导入 1 个 Topic')).toBeInTheDocument();
    expect(screen.getAllByText('imported-topic').length).toBeGreaterThan(0);
  });

  it('does not call createTopic when imported topic CSV is invalid or duplicated', async () => {
    const user = userEvent.setup();
    instanceServiceMocks.listInstances.mockResolvedValue([selectedInstance]);
    mockTopicsList([{ ...buildTopics(1)[0], instanceId: 'instance-proxy-1' }]);
    renderWithProviders('/instance/instance-proxy-1/topic');

    expect(await screen.findByText('topic-01')).toBeInTheDocument();
    expect(await screen.findByText('10.0.2.21:8080')).toBeInTheDocument();
    const csv = [
      '"Name","Type","Write Queues","Read Queues","Permission"',
      '"bad topic","NORMAL","8","8","RW"',
      '"bad topic","NORMAL","8","8","RW"',
    ].join('\n');
    await user.upload(screen.getByTestId('topic-import-file'), new File([csv], 'bad.csv'));

    expect(await screen.findByText('检测到 2 行无效，将跳过这些行')).toBeInTheDocument();
    expect(screen.getAllByText(/Name 仅支持/).length).toBeGreaterThan(0);
    expect(screen.getByText(/重复/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '开始导入' })).toBeDisabled();
    expect(topicServiceMocks.createTopic).not.toHaveBeenCalled();
  });

  it('imports valid topic rows while skipping duplicate rows', async () => {
    const user = userEvent.setup();
    mockTopicsList([]);
    instanceServiceMocks.listInstances.mockResolvedValue([selectedInstance]);
    renderWithProviders('/instance/instance-proxy-1/topic');

    await screen.findByText(/共 0 个 Topic/);
    await screen.findByText('10.0.2.21:8080');
    const csv = [
      '"Name","Type","Write Queues","Read Queues","Permission"',
      '"topic-a","NORMAL","8","8","RW"',
      '"topic-a","NORMAL","8","8","RW"',
      '"topic-b","FIFO","4","4","RW"',
    ].join('\n');
    await user.upload(screen.getByTestId('topic-import-file'), new File([csv], 'dedup.csv'));

    expect(await screen.findByText('检测到 1 行无效，将跳过这些行')).toBeInTheDocument();
    expect(screen.getByText(/Name 与第 2 行重复/)).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '开始导入' }));

    await waitFor(() => expect(topicServiceMocks.createTopic).toHaveBeenCalledTimes(2));
    expect(topicServiceMocks.createTopic).toHaveBeenNthCalledWith(
      1,
      expect.objectContaining({ name: 'topic-a', instanceId: 'instance-proxy-1' }),
    );
    expect(topicServiceMocks.createTopic).toHaveBeenNthCalledWith(
      2,
      expect.objectContaining({ name: 'topic-b', instanceId: 'instance-proxy-1' }),
    );
    expect(await screen.findByText('已导入 2 个 Topic，1 行无效已跳过')).toBeInTheDocument();
  });

  it('disables Topic writes until an instance is available', async () => {
    instanceServiceMocks.listInstances.mockResolvedValue([]);
    renderWithProviders();

    expect(await screen.findByText('共 0 个 Topic')).toBeInTheDocument();
    expect(topicServiceMocks.listTopicsPage).not.toHaveBeenCalled();
    await waitFor(() => expect(document.querySelector('.ant-spin-spinning')).toBeNull());
    expect(screen.getByRole('button', { name: /导入/ })).toBeDisabled();
    expect(screen.getByRole('button', { name: /创建 Topic/ })).toBeDisabled();
  });

  it('rejects malformed or duplicate batch message properties without sending', () => {
    expect(parseMessageProperties('traceId=abc\ntenant\ntraceId=duplicate')).toEqual({
      properties: { traceId: 'abc' },
      errors: ['“tenant”应使用 key=value 格式', '属性名“traceId”重复'],
    });
  });

  it('preserves equals signs in valid batch message property values', () => {
    expect(parseMessageProperties('signature=part-a=part-b')).toEqual({
      properties: { signature: 'part-a=part-b' },
      errors: [],
    });
  });

  it('renders unavailable Topic consumer metrics distinctly from zero', async () => {
    const user = userEvent.setup();
    mockTopicsList([buildTopics(1)[0]]);
    topicServiceMocks.getTopicConsumerPage.mockResolvedValue({
      items: [
        {
          group: 'cg-orders',
          consumeType: 'CLUSTERING',
          messageModel: 'CLUSTERING',
          consumeTps: 0,
          diffTotal: 0,
          metricsAvailable: false,
        },
      ],
      total: 1,
      page: 1,
      pageSize: 20,
    });
    renderWithProviders();

    await user.click(await screen.findByRole('button', { name: /详情/ }));

    expect(topicServiceMocks.getTopicConsumerPage).toHaveBeenCalledWith(
      'topic-01',
      'instance-proxy-1',
      1,
      20,
    );
    expect(await screen.findAllByText('不可用')).not.toHaveLength(0);
  });

  it('renders subscription group names as links in the topic detail modal', async () => {
    const user = userEvent.setup();
    mockTopicsList([buildTopics(1)[0]]);
    topicServiceMocks.getTopicConsumerPage.mockResolvedValue({
      items: [
        {
          group: 'cg-orders',
          consumeType: 'CLUSTERING',
          messageModel: 'CLUSTERING',
          consumeTps: 5,
          diffTotal: 0,
        },
      ],
      total: 1,
      page: 1,
      pageSize: 20,
    });
    renderWithProviders();

    await user.click(await screen.findByRole('button', { name: /详情/ }));

    const groupLink = await screen.findByText('cg-orders');
    expect(groupLink.closest('a')).not.toBeNull();
  });
});
