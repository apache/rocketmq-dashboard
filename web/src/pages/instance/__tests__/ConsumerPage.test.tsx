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
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type React from 'react';
import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import type { ConsumerGroup } from '../../../api/metadata';
import { LangProvider } from '../../../i18n/LangContext';
import * as consumerService from '../../../services/consumerService';
import ConsumerPage from '../consumer';

vi.mock('../../../services/consumerService', () => ({
  batchDeleteConsumerGroups: vi.fn(),
  createConsumerGroup: vi.fn(),
  deleteConsumerGroup: vi.fn(),
  getConsumerGroup: vi.fn(),
  getConsumerProgress: vi.fn(),
  getConsumerSubscriptions: vi.fn(),
  listConsumerGroups: vi.fn(),
  resetConsumerOffset: vi.fn(),
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

const group: ConsumerGroup = {
  name: 'remote-cg',
  namespace: 'remote-ns',
  clusterId: 'cluster-a',
  subscriptionMode: 'Push',
  consumeType: 'CLUSTERING',
  onlineInstances: 1,
  totalLag: 10,
  subscribedTopics: ['remote-topic'],
  subscriptionDataType: 'NORMAL',
  retryMaxTimes: 16,
  createdAt: '2026-07-23T00:00:00Z',
  updatedAt: '2026-07-23T00:00:00Z',
  delaySeconds: 3,
  instances: [],
};

const renderWithProviders = (ui: React.ReactElement) =>
  render(
    <App>
      <LangProvider>{ui}</LangProvider>
    </App>,
  );

describe('Consumer page', () => {
  beforeEach(() => {
    vi.mocked(consumerService.listConsumerGroups).mockResolvedValue([group]);
    vi.mocked(consumerService.getConsumerGroup).mockResolvedValue({
      ...group,
      instances: [
        {
          clientId: 'client-1',
          protocol: 'GRPC',
          address: '127.0.0.1:8081',
          subscribedTopics: ['remote-topic'],
          lastHeartbeat: '2026-07-23T00:01:00Z',
          topicLag: { 'remote-topic': 10 },
        },
      ],
    });
    vi.mocked(consumerService.getConsumerProgress).mockResolvedValue([
      {
        broker: 'broker-a',
        queueId: 0,
        brokerOffset: 100,
        consumerOffset: 90,
        diffTotal: 10,
      },
    ]);
    vi.mocked(consumerService.getConsumerSubscriptions).mockResolvedValue([
      {
        topic: 'remote-topic',
        expression: '*',
        type: 'NORMAL',
        filterMode: '全量',
        consistency: '一致',
      },
    ]);
  });

  it('loads consumer groups through the service layer', async () => {
    renderWithProviders(<ConsumerPage />);

    expect(await screen.findByText('remote-cg')).toBeInTheDocument();
    expect(screen.getByText('Push')).toBeInTheDocument();
    expect(consumerService.listConsumerGroups).toHaveBeenCalledTimes(1);
  });

  it('loads detail, subscriptions, and progress when opening a group', async () => {
    const user = userEvent.setup();
    renderWithProviders(<ConsumerPage />);

    await user.click(await screen.findByRole('button', { name: /详情/ }));

    await waitFor(() => expect(consumerService.getConsumerGroup).toHaveBeenCalledWith('remote-cg'));
    expect(consumerService.getConsumerProgress).toHaveBeenCalledWith('remote-cg');
    expect(consumerService.getConsumerSubscriptions).toHaveBeenCalledWith('remote-cg');
    await waitFor(() => expect(screen.getAllByText('remote-topic').length).toBeGreaterThan(0));
  });
});
