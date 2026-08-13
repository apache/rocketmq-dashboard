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
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { LangProvider } from '../../../i18n/LangContext';
import ResourcePlanPage from '../resourcePlan';

const resourcePlanServiceMocks = vi.hoisted(() => ({
  RESOURCE_PLAN_SAMPLE: JSON.stringify({
    topics: [{ name: 'orders', type: 'NORMAL', writeQueues: 8, readQueues: 8, perm: 'RW' }],
    consumerGroups: [{ name: 'cg-orders', consumeType: 'CLUSTERING' }],
  }),
  parseResourceBundle: vi.fn(),
  previewResourcePlan: vi.fn(),
}));

const instanceServiceMocks = vi.hoisted(() => ({
  listInstances: vi.fn(),
}));

vi.mock('../../../services/resourcePlanService', () => resourcePlanServiceMocks);
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
});

const renderWithProviders = () =>
  render(
    <App>
      <LangProvider>
        <MemoryRouter initialEntries={['/instance/instance-proxy-1/resource-plan']}>
          <Routes>
            <Route path="/instance/:instanceId/resource-plan" element={<ResourcePlanPage />} />
          </Routes>
        </MemoryRouter>
      </LangProvider>
    </App>,
  );

describe('ResourcePlanPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    instanceServiceMocks.listInstances.mockResolvedValue([
      {
        id: 'instance-proxy-1',
        name: 'instance-proxy-1',
        remark: '',
        type: 'PROXY',
        endpoint: '10.0.0.1:8080',
        topicCount: 0,
        consumerGroupCount: 0,
        createdAt: '2026-01-01T00:00:00Z',
        updatedAt: '2026-01-01T00:00:00Z',
      },
    ]);
    resourcePlanServiceMocks.parseResourceBundle.mockReturnValue({
      topics: [{ name: 'orders', type: 'NORMAL', writeQueues: 8, readQueues: 8, perm: 'RW' }],
      consumerGroups: [{ name: 'cg-orders', consumeType: 'CLUSTERING' }],
    });
    resourcePlanServiceMocks.previewResourcePlan.mockResolvedValue({
      instanceId: 'instance-proxy-1',
      summary: {
        total: 2,
        creates: 1,
        updates: 1,
        skips: 0,
        conflicts: 0,
        invalids: 0,
        applicable: 2,
      },
      entries: [
        {
          resourceType: 'TOPIC',
          name: 'orders',
          rowIndex: 1,
          action: 'UPDATE',
          applicable: true,
          reason: 'Topic exists with different configuration',
          changes: [{ field: 'writeQueues', currentValue: '16', desiredValue: '8' }],
        },
        {
          resourceType: 'CONSUMER_GROUP',
          name: 'cg-orders',
          rowIndex: 1,
          action: 'CREATE',
          applicable: true,
          reason: 'Consumer group does not exist in the selected instance',
          changes: [],
        },
      ],
    });
  });

  it('previews the pasted resource bundle for the selected instance', async () => {
    const user = userEvent.setup();
    renderWithProviders();

    expect(await screen.findByText('资源变更计划')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /生成计划/ }));

    await waitFor(() =>
      expect(resourcePlanServiceMocks.previewResourcePlan).toHaveBeenCalledWith({
        instanceId: 'instance-proxy-1',
        topics: [{ name: 'orders', type: 'NORMAL', writeQueues: 8, readQueues: 8, perm: 'RW' }],
        consumerGroups: [{ name: 'cg-orders', consumeType: 'CLUSTERING' }],
      }),
    );

    expect(screen.getByText('orders')).toBeInTheDocument();
    expect(screen.getByText('cg-orders')).toBeInTheDocument();
    expect(
      within(screen.getByText('总资源').closest('.ant-card')!).getByText('2'),
    ).toBeInTheDocument();
    expect(screen.getByText('writeQueues: 16 → 8')).toBeInTheDocument();
  });
});
