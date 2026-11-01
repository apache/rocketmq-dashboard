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
import { downloadCsv } from '../../../utils/download';

vi.mock('../../../services/clusterService', () => ({
  listClusters: vi.fn(),
}));

vi.mock('../../../services/instanceService', () => ({
  listInstances: vi.fn(),
}));

vi.mock('../../../utils/download', async () => {
  const actual =
    await vi.importActual<typeof import('../../../utils/download')>('../../../utils/download');
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

  it.each([
    { tab: /Broker 管理/, oldNode: 'broker-from-instance-1', newNode: 'broker-from-instance-2' },
    { tab: /NameServer 管理/, oldNode: 'nameserver-one:9876', newNode: 'nameserver-two:9876' },
    { tab: /Proxy 管理/, oldNode: 'proxy-one:8080', newNode: 'proxy-two:8080' },
  ])(
    'hides $oldNode and blocks export while the next instance loads',
    async ({ tab, oldNode, newNode }) => {
      let resolveNext!: (value: ClusterInfo[]) => void;
      const next = new Promise<ClusterInfo[]>((resolve) => {
        resolveNext = resolve;
      });
      const firstCluster = {
        ...buildCluster('cluster-1', 'broker-from-instance-1'),
        nameServers: [{ addr: 'nameserver-one:9876', status: 'healthy' }],
        proxies: [
          {
            addr: 'proxy-one:8080',
            grpcPort: 8081,
            remotingPort: 8080,
            connections: 1,
            status: 'healthy',
          },
        ],
      };
      const nextCluster = {
        ...buildCluster('cluster-2', 'broker-from-instance-2'),
        nameServers: [{ addr: 'nameserver-two:9876', status: 'healthy' }],
        proxies: [
          {
            addr: 'proxy-two:8080',
            grpcPort: 8081,
            remotingPort: 8080,
            connections: 2,
            status: 'healthy',
          },
        ],
      };
      vi.mocked(listClusters).mockImplementation((instanceId) =>
        instanceId === 'instance-2' ? next : Promise.resolve([firstCluster]),
      );
      const user = userEvent.setup();
      renderPage();
      await screen.findByText('broker-from-instance-1');
      await user.click(screen.getByRole('tab', { name: tab }));
      expect(await screen.findAllByText(oldNode)).not.toHaveLength(0);
      expect(screen.getByRole('button', { name: '导出' })).toBeEnabled();

      await user.click(screen.getByRole('combobox', { name: '选择实例' }));
      await user.click(
        await screen.findByText('instance-2', { selector: '.ant-select-item-option-content' }),
      );
      await waitFor(() => expect(listClusters).toHaveBeenLastCalledWith('instance-2'));
      expect.soft(screen.queryAllByText(oldNode)).toHaveLength(0);
      expect.soft(screen.getByRole('button', { name: '导出' })).toBeDisabled();
      await user.click(screen.getByRole('button', { name: '导出' }));
      expect.soft(downloadCsv).not.toHaveBeenCalled();
      vi.mocked(downloadCsv).mockClear();

      await act(async () => resolveNext([nextCluster]));
      expect(await screen.findAllByText(newNode)).not.toHaveLength(0);
      expect(screen.queryAllByText(oldNode)).toHaveLength(0);
      expect(screen.getByRole('button', { name: '导出' })).toBeEnabled();
      await user.click(screen.getByRole('button', { name: '导出' }));
      expect(downloadCsv).toHaveBeenCalledTimes(1);
      const csv = vi.mocked(downloadCsv).mock.calls[0][1];
      expect(csv).toContain(newNode);
      expect(csv).not.toContain(oldNode);
    },
    20_000,
  );

  it('retains completed topology during a refresh within the same instance', async () => {
    let resolveRefresh!: (value: ClusterInfo[]) => void;
    const refresh = new Promise<ClusterInfo[]>((resolve) => {
      resolveRefresh = resolve;
    });
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('broker-from-instance-1');
    vi.mocked(listClusters).mockReturnValueOnce(refresh);
    await user.click(screen.getByRole('button', { name: '重置' }));
    expect(screen.getByText('broker-from-instance-1')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '导出' })).toBeEnabled();
    await user.click(screen.getByRole('button', { name: '导出' }));
    expect(vi.mocked(downloadCsv).mock.calls[0][1]).toContain('broker-from-instance-1');
    await act(async () => resolveRefresh([buildCluster('cluster-1', 'refreshed-broker')]));
    expect(await screen.findByText('refreshed-broker')).toBeInTheDocument();
  }, 20_000);

  it('does not restore old topology when the selected instance request fails', async () => {
    let rejectNext!: (reason: Error) => void;
    const next = new Promise<ClusterInfo[]>((_, reject) => {
      rejectNext = reject;
    });
    vi.mocked(listClusters).mockImplementation((instanceId) =>
      instanceId === 'instance-2'
        ? next
        : Promise.resolve([buildCluster('cluster-1', 'broker-from-instance-1')]),
    );
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('broker-from-instance-1');
    await user.click(screen.getByRole('combobox', { name: '选择实例' }));
    await user.click(
      await screen.findByText('instance-2', { selector: '.ant-select-item-option-content' }),
    );
    await waitFor(() => expect(listClusters).toHaveBeenLastCalledWith('instance-2'));
    await act(async () => rejectNext(new Error('instance-2 unavailable')));
    expect(screen.queryByText('broker-from-instance-1')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: '导出' })).toBeDisabled();
    expect(downloadCsv).not.toHaveBeenCalled();
  }, 20_000);

  it('ignores an initial previous-instance response after the new scope completes', async () => {
    let resolveOld!: (value: ClusterInfo[]) => void;
    const old = new Promise<ClusterInfo[]>((resolve) => {
      resolveOld = resolve;
    });
    vi.mocked(listClusters).mockImplementation((instanceId) =>
      instanceId === 'instance-1'
        ? old
        : Promise.resolve([buildCluster('cluster-2', 'broker-from-instance-2')]),
    );
    const user = userEvent.setup();
    renderPage();
    await waitFor(() => expect(listClusters).toHaveBeenCalledWith('instance-1'));
    await user.click(screen.getByRole('combobox', { name: '选择实例' }));
    await user.click(
      await screen.findByText('instance-2', { selector: '.ant-select-item-option-content' }),
    );
    await screen.findByText('broker-from-instance-2');
    await act(async () => resolveOld([buildCluster('cluster-1', 'broker-from-instance-1')]));
    expect(screen.queryByText('broker-from-instance-1')).not.toBeInTheDocument();
    expect(screen.getByText('broker-from-instance-2')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '导出' }));
    expect(vi.mocked(downloadCsv).mock.calls[0][1]).toContain('broker-from-instance-2');
  }, 20_000);

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

  it('clears completed topology while a discovery fallback loads', async () => {
    let resolveFallback!: (value: ClusterInfo[]) => void;
    const fallback = new Promise<ClusterInfo[]>((resolve) => {
      resolveFallback = resolve;
    });
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('broker-from-instance-1');
    await user.click(screen.getByRole('combobox', { name: '选择实例' }));
    await user.click(
      await screen.findByText('instance-2', { selector: '.ant-select-item-option-content' }),
    );
    await screen.findByText('broker-from-instance-2');
    vi.mocked(listInstances).mockResolvedValue([instanceFixture(1, 'instance-1')]);
    vi.mocked(listClusters).mockImplementation((instanceId) =>
      instanceId === 'instance-1'
        ? fallback
        : Promise.resolve([buildCluster('cluster-2', 'broker-from-instance-2')]),
    );

    await user.click(screen.getByRole('button', { name: 'switch-language' }));
    await waitFor(() => expect(listClusters).toHaveBeenLastCalledWith('instance-1'));
    expect.soft(screen.queryByText('broker-from-instance-2')).not.toBeInTheDocument();
    expect.soft(screen.getByRole('button', { name: 'Export' })).toBeDisabled();
    await user.click(screen.getByRole('button', { name: 'Export' }));
    expect.soft(downloadCsv).not.toHaveBeenCalled();
    vi.mocked(downloadCsv).mockClear();

    await act(async () => resolveFallback([buildCluster('cluster-1', 'broker-from-instance-1')]));
    await screen.findByText('broker-from-instance-1');
    await user.click(screen.getByRole('button', { name: 'Export' }));
    const csv = vi.mocked(downloadCsv).mock.calls[0][1];
    expect(csv).toContain('broker-from-instance-1');
    expect(csv).not.toContain('broker-from-instance-2');
  }, 20_000);

  it('clears topology and disables export when discovery removes every instance', async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('broker-from-instance-1');
    vi.mocked(listInstances).mockResolvedValue([]);

    await user.click(screen.getByRole('button', { name: 'switch-language' }));
    await waitFor(() =>
      expect(screen.queryByText('broker-from-instance-1')).not.toBeInTheDocument(),
    );
    expect(screen.getByRole('button', { name: 'Export' })).toBeDisabled();
    await user.click(screen.getByRole('button', { name: 'Export' }));
    expect(downloadCsv).not.toHaveBeenCalled();
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
