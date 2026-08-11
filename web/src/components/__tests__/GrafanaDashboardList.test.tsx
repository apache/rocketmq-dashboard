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
import { act, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';

import { LangProvider } from '../../i18n/LangContext';
import {
  exportGrafanaDashboard,
  exportGrafanaDashboards,
  getGrafanaDashboard,
  listGrafanaDashboards,
} from '../../services/grafanaService';
import GrafanaDashboardList from '../GrafanaDashboardList';

vi.mock('../../services/grafanaService', () => ({
  listGrafanaDashboards: vi.fn(),
  getGrafanaDashboard: vi.fn(),
  exportGrafanaDashboard: vi.fn(),
  exportGrafanaDashboards: vi.fn(),
}));

const dashboards = [
  {
    uid: 'rocketmq-overview',
    title: 'RocketMQ Cluster Overview',
    description: 'Overview',
    tags: ['rocketmq'],
  },
  { uid: 'rocketmq-broker', title: 'RocketMQ Broker', description: 'Broker', tags: ['rocketmq'] },
];

const dashboardModel = {
  uid: 'rocketmq-overview',
  title: 'RocketMQ Cluster Overview',
  schemaVersion: 39,
  panels: [{ id: 1, title: 'Messages In TPS', type: 'timeseries' }],
};

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

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(listGrafanaDashboards).mockResolvedValue(dashboards);
  vi.mocked(getGrafanaDashboard).mockResolvedValue(dashboardModel);
  vi.mocked(exportGrafanaDashboard).mockResolvedValue(
    new Blob([JSON.stringify(dashboardModel, null, 2)], { type: 'application/json' }),
  );
  vi.mocked(exportGrafanaDashboards).mockResolvedValue(
    new Blob(['zip-content'], { type: 'application/zip' }),
  );
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe('GrafanaDashboardList', () => {
  it('lists the configured dashboards', async () => {
    render(
      <App>
        <LangProvider>
          <GrafanaDashboardList />
        </LangProvider>
      </App>,
    );

    expect(await screen.findByText('RocketMQ Cluster Overview')).toBeInTheDocument();
    expect(screen.getByText('RocketMQ Broker')).toBeInTheDocument();
  });

  it('opens the view modal and renders the dashboard JSON', async () => {
    const user = userEvent.setup();
    render(
      <App>
        <LangProvider>
          <GrafanaDashboardList />
        </LangProvider>
      </App>,
    );

    await screen.findByText('RocketMQ Cluster Overview');
    const viewButtons = screen.getAllByRole('button', { name: /View|查看/ });
    await user.click(viewButtons[0]);

    const dialog = await screen.findByRole('dialog');
    await waitFor(() => expect(getGrafanaDashboard).toHaveBeenCalledWith('rocketmq-overview'));
    expect(within(dialog).getByText(/"uid": "rocketmq-overview"/)).toBeInTheDocument();
  });

  it('keeps the latest preview when an earlier request resolves last', async () => {
    let resolveOverview!: (value: typeof dashboardModel) => void;
    let resolveBroker!: (value: typeof dashboardModel) => void;
    vi.mocked(getGrafanaDashboard).mockImplementation(
      (uid) =>
        new Promise((resolve) => {
          if (uid === 'rocketmq-overview') resolveOverview = resolve;
          else resolveBroker = resolve;
        }),
    );
    render(
      <App>
        <LangProvider>
          <GrafanaDashboardList />
        </LangProvider>
      </App>,
    );

    await screen.findByText('RocketMQ Cluster Overview');
    const viewButtons = screen.getAllByRole('button', { name: /View|查看/ });
    await userEvent.click(viewButtons[0]);
    await userEvent.click(viewButtons[1]);

    await act(async () => {
      resolveBroker({ ...dashboardModel, uid: 'rocketmq-broker', title: 'RocketMQ Broker' });
    });
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByText(/"uid": "rocketmq-broker"/)).toBeInTheDocument();

    await act(async () => {
      resolveOverview(dashboardModel);
    });
    expect(within(dialog).getByText(/"uid": "rocketmq-broker"/)).toBeInTheDocument();
    expect(within(dialog).queryByText(/"uid": "rocketmq-overview"/)).not.toBeInTheDocument();
  });

  it('tracks simultaneous dashboard exports independently', async () => {
    vi.mocked(exportGrafanaDashboard).mockImplementation(() => new Promise(() => {}));
    const user = userEvent.setup();
    render(
      <App>
        <LangProvider>
          <GrafanaDashboardList />
        </LangProvider>
      </App>,
    );

    await screen.findByText('RocketMQ Cluster Overview');
    // Exact-name match: the toolbar "Export all" button also matches /Export|导出/.
    const exportButtons = screen.getAllByRole('button', { name: /^(Export|导出)$/ });
    await user.click(exportButtons[0]);
    await user.click(exportButtons[1]);

    await waitFor(() => {
      expect(exportGrafanaDashboard).toHaveBeenCalledTimes(2);
      expect(exportButtons[0]).toHaveClass('ant-btn-loading');
      expect(exportButtons[1]).toHaveClass('ant-btn-loading');
    });
  });

  it('exports a dashboard and triggers a download', async () => {
    const user = userEvent.setup();
    const createObjectURL = vi.fn().mockReturnValue('blob:grafana');
    const revokeObjectURL = vi.fn();
    const clickSpy = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
    Object.defineProperty(URL, 'createObjectURL', { writable: true, value: createObjectURL });
    Object.defineProperty(URL, 'revokeObjectURL', { writable: true, value: revokeObjectURL });

    render(
      <App>
        <LangProvider>
          <GrafanaDashboardList />
        </LangProvider>
      </App>,
    );

    await screen.findByText('RocketMQ Cluster Overview');
    const exportButtons = screen.getAllByRole('button', { name: /Export|导出/ });
    await user.click(exportButtons[1]);

    await waitFor(() => expect(exportGrafanaDashboard).toHaveBeenCalledWith('rocketmq-overview'));
    expect(createObjectURL).toHaveBeenCalledTimes(1);
    expect(clickSpy).toHaveBeenCalled();

    clickSpy.mockRestore();
  });

  it('exports all dashboards and downloads the archive', async () => {
    const user = userEvent.setup();
    const createObjectURL = vi.fn().mockReturnValue('blob:grafana-all');
    const revokeObjectURL = vi.fn();
    let downloadedFilename = '';
    const clickSpy = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (
      this: HTMLAnchorElement,
    ) {
      downloadedFilename = this.download;
    });
    Object.defineProperty(URL, 'createObjectURL', { writable: true, value: createObjectURL });
    Object.defineProperty(URL, 'revokeObjectURL', { writable: true, value: revokeObjectURL });

    render(
      <App>
        <LangProvider>
          <GrafanaDashboardList />
        </LangProvider>
      </App>,
    );

    await screen.findByText('RocketMQ Cluster Overview');
    await user.click(screen.getByRole('button', { name: /Export all|导出全部/ }));

    await waitFor(() => expect(exportGrafanaDashboards).toHaveBeenCalledTimes(1));
    expect(downloadedFilename).toBe('rocketmq-grafana-dashboards.zip');
    expect(createObjectURL).toHaveBeenCalledTimes(1);
    expect(clickSpy).toHaveBeenCalled();

    clickSpy.mockRestore();
  });
});
