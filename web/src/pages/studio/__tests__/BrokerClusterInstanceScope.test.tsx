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
import { LangProvider, useLang } from '../../../i18n/LangContext';
import { listClusters } from '../../../services/clusterService';
import { listInstances } from '../../../services/instanceService';
import type { ClusterInfo } from '../../../api/cluster';
import type { Instance } from '../../../api/instance';
import BrokerCluster from '../BrokerCluster';

vi.mock('../../../services/clusterService', () => ({
  listClusters: vi.fn(),
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

vi.mock('react-router-dom', () => ({
  useNavigate: () => vi.fn(),
  useParams: () => ({}),
}));

const buildCluster = (id: string, brokerName: string): ClusterInfo => ({
  id,
  name: id,
  nsClusterName: id,
  type: 'V5_PROXY_CLUSTER',
  endpoint: '10.0.0.1:9876',
  status: 'healthy',
  version: '5.3.0',
  brokers: [
    {
      name: brokerName,
      addr: `10.0.0.2:10911`,
      version: '5.3.0',
      status: 'running',
      diskUsage: 10,
      tpsIn: 1,
      tpsOut: 1,
    },
  ],
  proxies: [],
  nameServers: [],
  config: {
    flushDiskType: 'ASYNC_FLUSH',
    autoCreateTopicEnable: false,
    autoCreateSubscriptionGroup: false,
    maxMessageSize: 4194304,
    msgTraceTopicName: 'RMQ_SYS_TRACE_TOPIC',
    fileReservedTime: 72,
    writeQueueNums: 8,
    readQueueNums: 8,
    brokerPermission: 6,
    deleteWhen: '04',
  },
  topicCount: 1,
  groupCount: 1,
  tpsHistory: [],
});

const instanceFixture = (id: number, name: string): Instance => ({
  id,
  name,
  remark: '',
  type: 'DIRECT',
  endpoint: '10.0.0.1:9876',
  topicCount: 0,
  consumerGroupCount: 0,
  gmtCreate: '',
  gmtModified: '',
});

const LanguageSwitch = () => {
  const { setLang } = useLang();
  return (
    <button type="button" onClick={() => setLang('en')}>
      switch-language
    </button>
  );
};

const renderPage = () =>
  render(
    <App>
      <LangProvider>
        <LanguageSwitch />
        <BrokerCluster />
      </LangProvider>
    </App>,
  );

describe('BrokerCluster instance scope', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(listInstances).mockResolvedValue([
      instanceFixture(1, 'instance-1'),
      instanceFixture(2, 'instance-2'),
    ]);
    vi.mocked(listClusters).mockImplementation(async (instanceId?: string) =>
      instanceId === 'instance-2'
        ? [buildCluster('cluster-2', 'broker-from-instance-2')]
        : [buildCluster('cluster-1', 'broker-from-instance-1')],
    );
  });

  it('keeps the instance the user selected when the display language changes', async () => {
    const user = userEvent.setup();
    renderPage();

    await screen.findByText('broker-from-instance-1');

    await user.click(screen.getByRole('combobox', { name: '选择实例' }));
    await user.click(
      await screen.findByText('instance-2', { selector: '.ant-select-item-option-content' }),
    );
    await waitFor(() => expect(listClusters).toHaveBeenLastCalledWith('instance-2'));
    expect(await screen.findByText('broker-from-instance-2')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'switch-language' }));
    // Let every effect triggered by the language change settle before asserting.
    await act(async () => {
      await Promise.resolve();
    });

    // The topology must stay scoped to the instance the user picked.
    await waitFor(() => expect(listClusters).toHaveBeenLastCalledWith('instance-2'));
    expect(screen.queryByText('broker-from-instance-1')).not.toBeInTheDocument();
    expect(await screen.findByText('broker-from-instance-2')).toBeInTheDocument();
  }, 20_000);

  it('falls back to the first instance when the selected one is gone', async () => {
    const user = userEvent.setup();
    renderPage();

    await screen.findByText('broker-from-instance-1');
    await user.click(screen.getByRole('combobox', { name: '选择实例' }));
    await user.click(
      await screen.findByText('instance-2', { selector: '.ant-select-item-option-content' }),
    );
    await waitFor(() => expect(listClusters).toHaveBeenLastCalledWith('instance-2'));

    // The next discovery — a language change re-runs it — no longer lists instance-2.
    vi.mocked(listInstances).mockResolvedValue([instanceFixture(1, 'instance-1')]);
    await user.click(screen.getByRole('button', { name: 'switch-language' }));

    // Keeping a selection that no longer exists would leave the page pointed at nothing.
    await waitFor(() => expect(listClusters).toHaveBeenLastCalledWith('instance-1'));
    expect(await screen.findByText('broker-from-instance-1')).toBeInTheDocument();
  }, 20_000);
});
