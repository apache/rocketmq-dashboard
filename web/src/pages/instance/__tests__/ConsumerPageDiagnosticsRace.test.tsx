/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

import { App } from 'antd';
import { cleanup, render, screen, waitFor, within, act } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { ConsumerGroup } from '../../../api/metadata';
import { LangProvider } from '../../../i18n/LangContext';
import * as consumerService from '../../../services/consumerService';
import * as instanceService from '../../../services/instanceService';
import ConsumerPage from '../consumer';

vi.mock('../../../services/consumerService', () => ({
  getConsumerProgress: vi.fn(),
  getConsumerSubscriptions: vi.fn(),
  listConsumerGroupPage: vi.fn(),
  listAllConsumerGroups: vi.fn(),
  refreshConsumerGroup: vi.fn(),
}));

vi.mock('../../../services/instanceService', () => ({
  listInstances: vi.fn(),
}));

const group: ConsumerGroup = {
  name: 'remote-cg',
  namespace: 'remote-ns',
  clusterId: 'cluster-a',
  instanceId: 'instance-1',
  subscriptionMode: 'Push',
  consumeType: 'CLUSTERING',
  onlineInstances: 1,
  totalLag: 10,
  subscribedTopics: ['remote-topic'],
  subscriptionDataType: 'NORMAL',
  retryMaxTimes: 16,
  gmtCreate: '2026-07-23T00:00:00Z',
  gmtModified: '2026-07-23T00:00:00Z',
  delaySeconds: 3,
  instances: [],
};

const deferred = <T,>() => {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((complete) => {
    resolve = complete;
  });
  return { promise, resolve };
};

const progress = (diffTotal: number) => [
  {
    topic: 'remote-topic',
    broker: 'broker-a',
    queueId: 0,
    brokerOffset: diffTotal,
    consumerOffset: 0,
    diffTotal,
  },
];

const renderPage = () =>
  render(
    <App>
      <LangProvider>
        <MemoryRouter initialEntries={['/instance/consumer']}>
          <ConsumerPage />
        </MemoryRouter>
      </LangProvider>
    </App>,
  );

describe('Consumer page diagnostic request ownership', () => {
  beforeEach(() => {
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
    vi.mocked(instanceService.listInstances).mockResolvedValue([
      {
        id: 1,
        name: 'instance-1',
        remark: '',
        type: 'PROXY_CLUSTER',
        endpoint: '10.0.0.1:8080',
        topicCount: 0,
        consumerGroupCount: 0,
        gmtCreate: '2026-01-01T00:00:00Z',
        gmtModified: '2026-01-01T00:00:00Z',
      },
    ]);
    vi.mocked(consumerService.listConsumerGroupPage).mockResolvedValue({
      items: [group],
      total: 1,
      page: 1,
      size: 20,
    });
    vi.mocked(consumerService.listAllConsumerGroups).mockResolvedValue([group]);
    vi.mocked(consumerService.getConsumerSubscriptions).mockResolvedValue([]);
    vi.mocked(consumerService.refreshConsumerGroup).mockResolvedValue(group);
  });

  afterEach(() => {
    cleanup();
    vi.resetAllMocks();
  });

  it('does not let an older progress response overwrite a newer diagnostic refresh', async () => {
    // Opening the details starts P1. Selecting health starts P2, and the
    // explicit re-diagnosis starts P3. Keep P1 and P2 pending so the test
    // controls the exact completion order without relying on timing.
    const first = deferred<ReturnType<typeof progress>>();
    const second = deferred<ReturnType<typeof progress>>();
    const third = deferred<ReturnType<typeof progress>>();
    vi.mocked(consumerService.getConsumerProgress)
      .mockReturnValueOnce(first.promise)
      .mockReturnValueOnce(second.promise)
      .mockReturnValueOnce(third.promise);

    const user = userEvent.setup();
    renderPage();

    const row = await screen.findByRole('row', { name: /remote-cg/ });
    await user.click(within(row).getByRole('button', { name: '详情' }));
    const dialog = await screen.findByRole('dialog', { name: /remote-cg/ });
    await user.click(within(dialog).getByRole('tab', { name: '健康诊断' }));
    const panel = await screen.findByRole('tabpanel', { name: '健康诊断' });

    await user.click(within(panel).getByRole('button', { name: '重新诊断' }));
    await waitFor(() => expect(consumerService.getConsumerProgress).toHaveBeenCalledTimes(3));

    // Establish that the newest response really rendered before the stale
    // response settles. The final failure must therefore be caused by P1.
    await act(async () => third.resolve(progress(777)));
    expect(within(panel).getByText('777')).toBeInTheDocument();

    await act(async () => first.resolve(progress(13)));
    expect(within(panel).getByText('777')).toBeInTheDocument();
    expect(within(panel).queryByText('13')).not.toBeInTheDocument();
  });
});
