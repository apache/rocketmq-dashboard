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

import { describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { useInstanceFilter } from './useInstanceFilter';

const instanceServiceMocks = vi.hoisted(() => ({
  listInstances: vi.fn(),
}));

vi.mock('../services/instanceService', () => instanceServiceMocks);

function InstanceRouteProbe() {
  const { pathname } = useLocation();
  const { selectedInstanceId } = useInstanceFilter();
  return <output>{`${pathname}|${selectedInstanceId}`}</output>;
}

const instanceA = {
  id: 7,
  name: 'instance-a',
  remark: '',
  type: 'PROXY_CLUSTER',
  endpoint: '127.0.0.1:8080',
  topicCount: 0,
  consumerGroupCount: 0,
  gmtCreate: '2026-01-01T00:00:00Z',
  gmtModified: '2026-01-01T00:00:00Z',
};

const renderProbe = (entry: string) =>
  render(
    <MemoryRouter initialEntries={[entry]}>
      <Routes>
        <Route path="/instance/:instanceId/topic" element={<InstanceRouteProbe />} />
        <Route path="/instance/topic" element={<InstanceRouteProbe />} />
      </Routes>
    </MemoryRouter>,
  );

describe('useInstanceFilter', () => {
  it('replaces an unknown route instance with the first available instance', async () => {
    instanceServiceMocks.listInstances.mockResolvedValue([instanceA]);

    renderProbe('/instance/missing/topic');

    await waitFor(() => {
      expect(screen.getByText('/instance/instance-a/topic|instance-a')).toBeInTheDocument();
    });
  });

  it('recovers from a malformed encoded instance id', async () => {
    instanceServiceMocks.listInstances.mockResolvedValue([
      {
        ...instanceA,
        type: 'PROXY',
      },
    ]);

    renderProbe('/instance/%E0%A4%A/topic');

    await waitFor(() => {
      expect(screen.getByText('/instance/instance-a/topic|instance-a')).toBeInTheDocument();
    });
  });

  it('keeps a known instance id in the route without rewriting it', async () => {
    instanceServiceMocks.listInstances.mockResolvedValue([instanceA]);

    renderProbe('/instance/instance-a/topic');

    await waitFor(() => {
      expect(screen.getByText('/instance/instance-a/topic|instance-a')).toBeInTheDocument();
    });
  });

  it('leaves an unknown route untouched when no instance is available', async () => {
    instanceServiceMocks.listInstances.mockResolvedValue([]);

    renderProbe('/instance/missing/topic');

    await waitFor(() => {
      expect(screen.getByText('/instance/missing/topic|undefined')).toBeInTheDocument();
    });
  });

  it('stays on the routed instance when the instance list fails to load', async () => {
    instanceServiceMocks.listInstances.mockRejectedValue(new Error('backend unavailable'));

    renderProbe('/instance/instance-a/topic');

    await waitFor(() => {
      expect(screen.getByText('/instance/instance-a/topic|undefined')).toBeInTheDocument();
    });
  });

  it('redirects a section-only route to the first instance of that section', async () => {
    instanceServiceMocks.listInstances.mockResolvedValue([instanceA]);

    renderProbe('/instance/topic');

    await waitFor(() => {
      expect(screen.getByText('/instance/instance-a/topic|instance-a')).toBeInTheDocument();
    });
  });
});
