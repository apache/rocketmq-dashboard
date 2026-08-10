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

import type { ReactElement } from 'react';
import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from 'antd';
import { type ClusterInfo, type NameServerConfigDiffResult } from '../../../api/cluster';
import type { Instance } from '../../../api/instance';
import { LangProvider } from '../../../i18n/LangContext';
import { getNameServerConfigDiff, listClusters } from '../../../services/clusterService';
import { listInstances } from '../../../services/instanceService';
import NameServerConfigDriftPage from '../nameServerConfigDrift';

vi.mock('../../../services/clusterService', () => ({
  getNameServerConfigDiff: vi.fn(),
  listClusters: vi.fn(),
}));

vi.mock('../../../services/instanceService', () => ({
  listInstances: vi.fn(),
}));

const createObjectURL = vi.fn(() => 'blob:nameserver-config-drift');
const revokeObjectURL = vi.fn();

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
  Object.defineProperties(URL, {
    createObjectURL: { configurable: true, value: createObjectURL },
    revokeObjectURL: { configurable: true, value: revokeObjectURL },
  });
});

const cluster = {
  id: 'cluster-a',
  name: 'Production',
} as ClusterInfo;

const instance = {
  id: 'instance-a',
  name: 'Production instance',
  vendor: 'APACHE',
} as Instance;

const driftResult: NameServerConfigDiffResult = {
  cluster: 'cluster-a',
  complete: true,
  driftDetected: true,
  nodeCount: 2,
  reachableNodeCount: 2,
  comparedKeys: ['listenPort', 'serverWorkerThreads'],
  nodes: [
    { address: 'ns-a:9876', reachable: true },
    { address: 'ns-b:9876', reachable: true },
  ],
  differences: [
    {
      key: 'listenPort',
      values: [
        { address: 'ns-a:9876', configured: true, value: '9876' },
        { address: 'ns-b:9876', configured: true, value: '19876' },
      ],
    },
  ],
};

const renderWithProviders = (ui: ReactElement) =>
  render(
    <App>
      <LangProvider>{ui}</LangProvider>
    </App>,
  );

describe('NameServerConfigDriftPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(listInstances).mockResolvedValue([instance]);
    vi.mocked(listClusters).mockResolvedValue([cluster]);
    vi.mocked(getNameServerConfigDiff).mockResolvedValue(driftResult);
  });

  it('checks the first cluster and renders node configuration differences', async () => {
    renderWithProviders(<NameServerConfigDriftPage />);

    await waitFor(() => {
      expect(listClusters).toHaveBeenCalledWith('instance-a');
      expect(getNameServerConfigDiff).toHaveBeenCalledWith('cluster-a', 'instance-a');
    });
    expect(await screen.findByText('检测到配置漂移')).toBeInTheDocument();
    expect(screen.getByText('listenPort')).toBeInTheDocument();
    expect(screen.getByText('19876')).toBeInTheDocument();
    expect(screen.getByText('ns-a:9876 · 可达')).toBeInTheDocument();
  });

  it('reports a consistent result when no safe configuration differs', async () => {
    vi.mocked(getNameServerConfigDiff).mockResolvedValue({
      ...driftResult,
      driftDetected: false,
      differences: [],
    });

    renderWithProviders(<NameServerConfigDriftPage />);

    expect(await screen.findByText('配置一致')).toBeInTheDocument();
    expect(screen.queryByText('配置差异')).not.toBeInTheDocument();
  });

  it('preserves partial results when a NameServer is unreachable', async () => {
    vi.mocked(getNameServerConfigDiff).mockResolvedValue({
      ...driftResult,
      complete: false,
      reachableNodeCount: 1,
      nodes: [
        { address: 'ns-a:9876', reachable: true },
        { address: 'ns-b:9876', reachable: false },
      ],
    });

    renderWithProviders(<NameServerConfigDriftPage />);

    expect(await screen.findByText('检查结果不完整')).toBeInTheDocument();
    expect(screen.getByText('ns-b:9876 · 不可达')).toBeInTheDocument();
  });

  it('runs the check again from the refresh control', async () => {
    const user = userEvent.setup();
    renderWithProviders(<NameServerConfigDriftPage />);

    await waitFor(() => {
      expect(getNameServerConfigDiff).toHaveBeenCalledTimes(1);
    });
    await user.click(screen.getByRole('button', { name: '重新检查' }));

    await waitFor(() => {
      expect(getNameServerConfigDiff).toHaveBeenCalledTimes(2);
    });
  });

  it('exports the current result as a cluster-scoped JSON file', async () => {
    const user = userEvent.setup();
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});

    renderWithProviders(<NameServerConfigDriftPage />);
    await user.click(await screen.findByRole('button', { name: '导出结果' }));

    const downloadedLink = click.mock.instances[0] as HTMLAnchorElement;
    expect(createObjectURL).toHaveBeenCalledWith(expect.any(Blob));
    expect(downloadedLink.download).toBe('nameserver-config-drift-cluster-a.json');
    expect(downloadedLink.href).toBe('blob:nameserver-config-drift');
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:nameserver-config-drift');
    click.mockRestore();
  });

  it('renders an empty state without starting a check when no cluster exists', async () => {
    vi.mocked(listClusters).mockResolvedValue([]);

    renderWithProviders(<NameServerConfigDriftPage />);

    expect(await screen.findByText('暂无可检查的集群')).toBeInTheDocument();
    expect(getNameServerConfigDiff).not.toHaveBeenCalled();
  });
});
