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
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from 'antd';
import { LangProvider } from '../../../i18n/LangContext';
import ProducerPage from '../Producer';
import {
  type ProducerConnection,
  type ProducerConnectionResult,
  fetchProducerGroups,
  fetchTopicList,
  queryProducerConnection,
} from '../../../api/producer';
import { listInstances } from '../../../services/instanceService';

vi.mock('../../../api/producer', () => ({
  fetchProducerGroups: vi.fn(),
  fetchTopicList: vi.fn(),
  queryProducerConnection: vi.fn(),
}));

vi.mock('../../../services/instanceService', () => ({
  listInstances: vi.fn(),
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

const renderWithProviders = (ui: React.ReactElement) => {
  return render(
    <App>
      <LangProvider>{ui}</LangProvider>
    </App>,
  );
};

const producerResult = (connectionSet: ProducerConnection[]): ProducerConnectionResult => ({
  connectionSet,
  summary: {
    totalConnections: connectionSet.length,
    uniqueClientCount: new Set(connectionSet.map((connection) => connection.clientId)).size,
    uniqueAddressCount: new Set(connectionSet.map((connection) => connection.clientAddr)).size,
    uniqueLanguageCount: new Set(connectionSet.map((connection) => connection.language)).size,
    uniqueVersionCount: new Set(connectionSet.map((connection) => connection.versionDesc)).size,
    languages: connectionSet.map((connection) => ({ value: connection.language, count: 1 })),
    versions: connectionSet.map((connection) => ({ value: connection.versionDesc, count: 1 })),
    duplicateClientIds: [],
    warnings: connectionSet.length === 0 ? ['NO_CONNECTIONS'] : [],
    readiness: connectionSet.length === 0 ? 'UNAVAILABLE' : 'READY',
  },
});

describe('ProducerPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(listInstances).mockResolvedValue([
      {
        id: 'instance-1',
        name: 'instance-1',
        remark: '',
        type: 'DIRECT',
        endpoint: '127.0.0.1:9876',
        topicCount: 0,
        consumerGroupCount: 0,
        createdAt: '2026-08-01T00:00:00',
        updatedAt: '2026-08-01T00:00:00',
      },
    ]);
    vi.mocked(fetchTopicList).mockResolvedValue(['order-events', 'payment-events']);
    vi.mocked(fetchProducerGroups).mockResolvedValue(['pg-order', 'pg-payment']);
    vi.mocked(queryProducerConnection).mockResolvedValue(producerResult([]));
  });

  it('loads topic options after mount', async () => {
    renderWithProviders(<ProducerPage />);

    await waitFor(() => {
      expect(fetchTopicList).toHaveBeenCalledWith('instance-1');
    });
  });

  it('uses an Apache instance rather than a cloud instance for producer diagnostics', async () => {
    vi.mocked(listInstances).mockResolvedValue([
      {
        id: 'cloud-instance',
        name: 'Cloud instance',
        remark: '',
        type: 'DIRECT',
        endpoint: 'cloud:9876',
        vendor: 'ALIYUN',
        topicCount: 0,
        consumerGroupCount: 0,
        createdAt: '2026-08-01T00:00:00',
        updatedAt: '2026-08-01T00:00:00',
      },
      {
        id: 'apache-instance',
        name: 'Apache instance',
        remark: '',
        type: 'DIRECT',
        endpoint: 'apache:9876',
        vendor: 'APACHE',
        topicCount: 0,
        consumerGroupCount: 0,
        createdAt: '2026-08-01T00:00:00',
        updatedAt: '2026-08-01T00:00:00',
      },
    ]);
    renderWithProviders(<ProducerPage />);

    await waitFor(() => expect(fetchTopicList).toHaveBeenCalledWith('apache-instance'));
    expect(screen.queryByText('Cloud instance')).not.toBeInTheDocument();
  });

  it('renders topic options loaded from the API', async () => {
    const user = userEvent.setup();
    renderWithProviders(<ProducerPage />);

    await waitFor(() => {
      expect(fetchTopicList).toHaveBeenCalledTimes(1);
    });

    await user.click(screen.getAllByRole('combobox')[1]);
    await screen.findByRole('option', { name: 'order-events' });
    expect(await screen.findByRole('option', { name: 'payment-events' })).toBeInTheDocument();
  });

  it('suggests active producer groups while keeping free-form input', async () => {
    const user = userEvent.setup();
    renderWithProviders(<ProducerPage />);

    await waitFor(() => expect(fetchProducerGroups).toHaveBeenCalledTimes(1));
    const groupInput = screen.getAllByRole('combobox')[2];
    await user.type(groupInput, 'payment');

    expect(await screen.findByRole('option', { name: 'pg-payment' })).toBeInTheDocument();
  });

  it('queries producer connections with the required topic and group', async () => {
    const user = userEvent.setup();
    vi.mocked(queryProducerConnection).mockResolvedValue(
      producerResult([
        {
          clientId: 'producer-1',
          clientAddr: '192.168.1.10',
          language: 'JAVA',
          versionDesc: '5.1.0',
        },
      ]),
    );
    renderWithProviders(<ProducerPage />);

    await waitFor(() => expect(fetchTopicList).toHaveBeenCalledTimes(1));
    const [, topicSelect, groupInput] = screen.getAllByRole('combobox');
    fireEvent.mouseDown(topicSelect.parentElement!);
    await user.click(
      await screen.findByText('order-events', { selector: '.ant-select-item-option-content' }),
    );
    await user.type(groupInput, 'order-producer');
    await user.click(screen.getByRole('button', { name: /搜索/ }));

    await waitFor(() => {
      expect(queryProducerConnection).toHaveBeenCalledWith(
        'instance-1',
        'order-events',
        'order-producer',
      );
    });
    expect(await screen.findByText('producer-1')).toBeInTheDocument();
    expect(await screen.findByText('生产者连接健康')).toBeInTheDocument();
    expect(screen.getByText('就绪')).toBeInTheDocument();
  });

  it('does not query without a producer group', async () => {
    const user = userEvent.setup();
    renderWithProviders(<ProducerPage />);

    await waitFor(() => expect(fetchTopicList).toHaveBeenCalledTimes(1));
    const [, topicSelect] = screen.getAllByRole('combobox');
    fireEvent.mouseDown(topicSelect.parentElement!);
    await user.click(
      await screen.findByText('order-events', { selector: '.ant-select-item-option-content' }),
    );
    await user.click(screen.getByRole('button', { name: /搜索/ }));

    expect(
      await screen.findByText('请输入生产者组', { selector: '.ant-form-item-explain-error' }),
    ).toBeInTheDocument();
    expect(queryProducerConnection).not.toHaveBeenCalled();
  });

  it('renders producer connection warnings from the summary', async () => {
    const user = userEvent.setup();
    vi.mocked(queryProducerConnection).mockResolvedValue({
      connectionSet: [
        {
          clientId: 'producer-a',
          clientAddr: '192.168.1.10',
          language: 'JAVA',
          versionDesc: '5.1.0',
        },
        {
          clientId: 'producer-a',
          clientAddr: '192.168.1.11',
          language: 'JAVA',
          versionDesc: '5.2.0',
        },
      ],
      summary: {
        totalConnections: 2,
        uniqueClientCount: 1,
        uniqueAddressCount: 2,
        uniqueLanguageCount: 1,
        uniqueVersionCount: 2,
        languages: [{ value: 'JAVA', count: 2 }],
        versions: [
          { value: '5.1.0', count: 1 },
          { value: '5.2.0', count: 1 },
        ],
        duplicateClientIds: ['producer-a'],
        warnings: ['DUPLICATE_CLIENT_ID', 'MIXED_CLIENT_VERSION'],
        readiness: 'WARNING',
      },
    });
    renderWithProviders(<ProducerPage />);

    await waitFor(() => expect(fetchTopicList).toHaveBeenCalledTimes(1));
    const [, topicSelect, groupInput] = screen.getAllByRole('combobox');
    fireEvent.mouseDown(topicSelect.parentElement!);
    await user.click(
      await screen.findByText('order-events', { selector: '.ant-select-item-option-content' }),
    );
    await user.type(groupInput, 'order-producer');
    await user.click(screen.getByRole('button', { name: /搜索/ }));

    expect(await screen.findByText('存在重复 Client ID')).toBeInTheDocument();
    expect(screen.getByText('存在多个客户端版本')).toBeInTheDocument();
    expect(screen.getByText('JAVA: 2')).toBeInTheDocument();
  });

  it('keeps manual producer group queries available when suggestions fail', async () => {
    vi.mocked(fetchProducerGroups).mockRejectedValue(new Error('broker unavailable'));
    const user = userEvent.setup();
    renderWithProviders(<ProducerPage />);

    await waitFor(() => expect(fetchTopicList).toHaveBeenCalledTimes(1));
    const [, topicSelect, groupInput] = screen.getAllByRole('combobox');
    fireEvent.mouseDown(topicSelect.parentElement!);
    await user.click(
      await screen.findByText('order-events', { selector: '.ant-select-item-option-content' }),
    );
    await user.type(groupInput, 'manual-producer');
    await user.click(screen.getByRole('button', { name: /搜索/ }));

    await waitFor(() => {
      expect(queryProducerConnection).toHaveBeenCalledWith(
        'instance-1',
        'order-events',
        'manual-producer',
      );
    });
  });

  it('clears stale producer query state before loading a new instance', async () => {
    vi.mocked(listInstances).mockResolvedValue([
      {
        id: 'instance-1',
        name: 'instance-1',
        remark: '',
        type: 'DIRECT',
        endpoint: '127.0.0.1:9876',
        topicCount: 0,
        consumerGroupCount: 0,
        createdAt: '2026-08-01T00:00:00',
        updatedAt: '2026-08-01T00:00:00',
      },
      {
        id: 'instance-2',
        name: 'instance-2',
        remark: '',
        type: 'DIRECT',
        endpoint: '127.0.0.2:9876',
        topicCount: 0,
        consumerGroupCount: 0,
        createdAt: '2026-08-01T00:00:00',
        updatedAt: '2026-08-01T00:00:00',
      },
    ]);
    vi.mocked(fetchTopicList).mockResolvedValueOnce(['order-events']).mockResolvedValueOnce([]);
    vi.mocked(queryProducerConnection).mockResolvedValue(
      producerResult([
        {
          clientId: 'producer-1',
          clientAddr: '192.168.1.10',
          language: 'JAVA',
          versionDesc: '5.1.0',
        },
      ]),
    );
    const user = userEvent.setup();
    const { container } = renderWithProviders(<ProducerPage />);

    await waitFor(() => expect(fetchTopicList).toHaveBeenCalledWith('instance-1'));
    const [instanceSelect, topicSelect, groupInput] = screen.getAllByRole('combobox');
    fireEvent.mouseDown(topicSelect.parentElement!);
    await user.click(
      await screen.findByText('order-events', { selector: '.ant-select-item-option-content' }),
    );
    await user.type(groupInput, 'order-producer');
    await user.click(screen.getByRole('button', { name: /搜索/ }));
    expect(await screen.findByText('producer-1')).toBeInTheDocument();

    fireEvent.mouseDown(instanceSelect.parentElement!);
    await user.click(
      await screen.findByText('instance-2', {
        selector: '.ant-select-item-option-content',
      }),
    );

    await waitFor(() => expect(fetchTopicList).toHaveBeenLastCalledWith('instance-2'));
    // scope to the page container: antd keeps closed dropdown portals in document.body
    expect(within(container).queryByText('producer-1')).not.toBeInTheDocument();
    expect(within(container).queryByText('order-events')).not.toBeInTheDocument();
  });
});
