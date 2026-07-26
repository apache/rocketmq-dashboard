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
import type { DataSource } from '../../../api/settings';
import { listDataSources, testDataSource } from '../../../api/settings';
import { DataSourceTab } from '../index';

vi.mock('../../../api/settings', () => ({
  createDataSource: vi.fn(),
  deleteDataSource: vi.fn(),
  getGeneralSettings: vi.fn(),
  listDataSources: vi.fn(),
  saveGeneralSettings: vi.fn(),
  testDataSource: vi.fn(),
  updateDataSource: vi.fn(),
}));

const sources: DataSource[] = [
  {
    key: 'prom-prod',
    name: 'Prometheus prod',
    type: 'Prometheus',
    url: 'http://prometheus:9090',
    auth: 'None',
    status: 'healthy',
  },
  {
    key: 'thanos-dr',
    name: 'Thanos DR',
    type: 'Thanos',
    url: 'http://thanos:10902',
    auth: 'Bearer Token',
    status: 'healthy',
  },
];

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

describe('DataSourceTab', () => {
  beforeEach(() => {
    vi.mocked(listDataSources).mockResolvedValue(sources);
  });

  it('shows connection test loading only on the clicked row', async () => {
    let resolveTest: (value: { success: boolean; message: string }) => void = () => undefined;
    vi.mocked(testDataSource).mockReturnValue(
      new Promise((resolve) => {
        resolveTest = resolve;
      }),
    );

    const user = userEvent.setup();
    render(
      <App>
        <DataSourceTab />
      </App>,
    );

    await screen.findByText('Prometheus prod');
    const buttons = screen.getAllByRole('button', { name: /测试连接/ });
    await user.click(buttons[0]);

    await waitFor(() => {
      expect(buttons[0]).toHaveClass('ant-btn-loading');
      expect(buttons[1]).not.toHaveClass('ant-btn-loading');
    });

    resolveTest({ success: true, message: 'ok' });
  });
});
