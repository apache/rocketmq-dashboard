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

describe('useInstanceFilter', () => {
  it('replaces an unknown route instance with the first available instance', async () => {
    instanceServiceMocks.listInstances.mockResolvedValue([
      {
        id: 7,
        name: 'instance-a',
        remark: '',
        type: 'PROXY',
        endpoint: '127.0.0.1:8080',
        topicCount: 0,
        consumerGroupCount: 0,
        gmtCreate: '2026-01-01T00:00:00Z',
        gmtModified: '2026-01-01T00:00:00Z',
      },
    ]);

    render(
      <MemoryRouter initialEntries={['/instance/missing/topic']}>
        <Routes>
          <Route path="/instance/:instanceId/topic" element={<InstanceRouteProbe />} />
        </Routes>
      </MemoryRouter>,
    );

    await waitFor(() => {
      expect(screen.getByText('/instance/7/topic|7')).toBeInTheDocument();
    });
  });
});
