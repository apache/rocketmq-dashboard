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
import { act, cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { LangProvider } from '../../../i18n/LangContext';
import type { AuditRecord } from '../../../api/ops';
import * as opsService from '../../../services/opsService';
import { downloadBlob } from '../../../utils/download';
import ResourceOperationTimelineDrawer, {
  type AuditTimelineResource,
} from '../ResourceOperationTimelineDrawer';

vi.mock('../../../services/opsService', () => ({
  exportAuditLogs: vi.fn(),
  listAuditRecords: vi.fn(),
}));

vi.mock('../../../utils/download', () => ({
  downloadBlob: vi.fn(),
}));

const resource = {
  resourceType: 'TOPIC',
  target: 'topic-a',
  clusterId: 'prod-cn',
};

const timelineRecord: AuditRecord = {
  id: 7,
  timestamp: '2026-08-02 11:00:00',
  operator: 'ops-user',
  operationType: 'UPDATE_TOPIC',
  resourceType: 'TOPIC',
  target: 'topic-a',
  clusterId: 'prod-cn',
  detail: 'readQueueNums=8',
  result: 'SUCCESS',
  errorMessage: '',
};

const deferred = <T,>() => {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((resolvePromise) => {
    resolve = resolvePromise;
  });
  return { promise, resolve };
};

const renderDrawer = (selectedResource: AuditTimelineResource = resource) =>
  render(
    <App>
      <LangProvider>
        <ResourceOperationTimelineDrawer open resource={selectedResource} onClose={vi.fn()} />
      </LangProvider>
    </App>,
  );

describe('ResourceOperationTimelineDrawer', () => {
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
    vi.mocked(opsService.listAuditRecords).mockResolvedValue({
      items: [timelineRecord],
      total: 1,
      page: 1,
      size: 20,
    });
    vi.mocked(opsService.exportAuditLogs).mockResolvedValue('timeline,csv');
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it('loads and exports the exact selected resource timeline', async () => {
    const user = userEvent.setup();
    renderDrawer();

    expect(await screen.findByText(/ops-user/)).toBeInTheDocument();
    expect(opsService.listAuditRecords).toHaveBeenCalledWith({
      page: 1,
      pageSize: 20,
      resourceType: 'TOPIC',
      target: 'topic-a',
      clusterId: 'prod-cn',
    });

    await user.click(screen.getByRole('button', { name: '导出时间线' }));

    await waitFor(() =>
      expect(opsService.exportAuditLogs).toHaveBeenCalledWith({
        resourceType: 'TOPIC',
        target: 'topic-a',
        clusterId: 'prod-cn',
      }),
    );
    expect(downloadBlob).toHaveBeenCalledWith(expect.any(Blob), expect.stringMatching(/\.csv$/));
  });

  it('keeps resources without a cluster in their own timeline scope', async () => {
    renderDrawer({ resourceType: 'SETTINGS', target: 'general', clusterId: null });

    await waitFor(() =>
      expect(opsService.listAuditRecords).toHaveBeenCalledWith({
        page: 1,
        pageSize: 20,
        resourceType: 'SETTINGS',
        target: 'general',
        clusterIdMissing: true,
      }),
    );
  });

  it('loads another server page within the same resource scope', async () => {
    const user = userEvent.setup();
    vi.mocked(opsService.listAuditRecords).mockResolvedValue({
      items: [timelineRecord],
      total: 21,
      page: 1,
      size: 20,
    });
    const { container } = renderDrawer();
    expect(await screen.findByText(/ops-user/)).toBeInTheDocument();

    const secondPage = container.ownerDocument.querySelector('.ant-pagination-item-2');
    if (!(secondPage instanceof HTMLElement)) throw new Error('Timeline page 2 was not rendered');
    await user.click(secondPage);

    await waitFor(() =>
      expect(opsService.listAuditRecords).toHaveBeenLastCalledWith({
        page: 2,
        pageSize: 20,
        resourceType: 'TOPIC',
        target: 'topic-a',
        clusterId: 'prod-cn',
      }),
    );
  });

  it('applies the selected date range to the resource query and export', async () => {
    const user = userEvent.setup();
    renderDrawer();
    expect(await screen.findByText(/ops-user/)).toBeInTheDocument();
    const dateInputs = screen.getAllByLabelText('时间线日期范围');

    await user.type(dateInputs[0], '2026-08-01');
    await user.keyboard('{Enter}');
    await user.type(dateInputs[1], '2026-08-02');
    await user.keyboard('{Enter}');

    await waitFor(() =>
      expect(opsService.listAuditRecords).toHaveBeenLastCalledWith({
        page: 1,
        pageSize: 20,
        resourceType: 'TOPIC',
        target: 'topic-a',
        clusterId: 'prod-cn',
        startDate: '2026-08-01',
        endDate: '2026-08-02',
      }),
    );

    await user.click(screen.getByRole('button', { name: '导出时间线' }));
    await waitFor(() =>
      expect(opsService.exportAuditLogs).toHaveBeenCalledWith({
        resourceType: 'TOPIC',
        target: 'topic-a',
        clusterId: 'prod-cn',
        startDate: '2026-08-01',
        endDate: '2026-08-02',
      }),
    );
  });

  it('ignores an older timeline response after the selected resource changes', async () => {
    const stale = deferred<Awaited<ReturnType<typeof opsService.listAuditRecords>>>();
    vi.mocked(opsService.listAuditRecords)
      .mockImplementationOnce(() => stale.promise)
      .mockResolvedValueOnce({
        items: [{ ...timelineRecord, id: 8, target: 'topic-b', operator: 'latest-user' }],
        total: 1,
        page: 1,
        size: 20,
      });
    const { rerender } = renderDrawer();

    rerender(
      <App>
        <LangProvider>
          <ResourceOperationTimelineDrawer
            open
            resource={{ ...resource, target: 'topic-b' }}
            onClose={vi.fn()}
          />
        </LangProvider>
      </App>,
    );

    expect(await screen.findByText(/latest-user/)).toBeInTheDocument();
    await act(async () => {
      stale.resolve({
        items: [{ ...timelineRecord, operator: 'stale-user' }],
        total: 1,
        page: 1,
        size: 20,
      });
    });

    expect(screen.queryByText(/stale-user/)).not.toBeInTheDocument();
    expect(screen.getByText(/latest-user/)).toBeInTheDocument();
  });
});
