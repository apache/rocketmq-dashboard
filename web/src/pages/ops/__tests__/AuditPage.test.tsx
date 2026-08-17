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
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type React from 'react';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { LangProvider } from '../../../i18n/LangContext';
import * as opsService from '../../../services/opsService';
import AuditPage from '../audit';

vi.mock('../../../services/opsService', () => ({
  cleanupAuditLogs: vi.fn(),
  exportAuditLogs: vi.fn(),
  getAuditFilterOptions: vi.fn(),
  listAuditRecords: vi.fn(),
}));

const renderWithProviders = (ui: React.ReactElement) =>
  render(
    <App>
      <LangProvider>{ui}</LangProvider>
    </App>,
  );

describe('Audit page', () => {
  let createObjectURL: ReturnType<typeof vi.fn>;
  let revokeObjectURL: ReturnType<typeof vi.fn>;
  let clickSpy: ReturnType<typeof vi.spyOn>;

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
    vi.mocked(opsService.getAuditFilterOptions).mockResolvedValue({
      operationTypes: ['CREATE_TOPIC', 'RESET_OFFSET'],
      resourceTypes: ['CONSUMER_GROUP', 'TOPIC'],
      clusterIds: ['prod-cn', 'prod-sh'],
      results: ['FAILED', 'PARTIAL', 'SUCCESS'],
    });
    vi.mocked(opsService.listAuditRecords).mockResolvedValue({
      items: [
        {
          id: 1,
          timestamp: '2026-08-01 10:00:00',
          operator: 'admin',
          operationType: 'DELETE_TOPIC',
          resourceType: 'TOPIC',
          target: 'topic-a',
          clusterId: 'prod-cn',
          detail: 'removed topic-a',
          result: 'SUCCESS',
          errorMessage: '',
        },
      ],
      total: 1,
      page: 1,
      size: 20,
    });
    vi.mocked(opsService.exportAuditLogs).mockResolvedValue(
      '\uFEFFtimestamp,operator\r\n"2026-08-01 10:00:00","admin"\r\n',
    );
    createObjectURL = vi.fn().mockReturnValue('blob:audit');
    revokeObjectURL = vi.fn();
    Object.defineProperty(URL, 'createObjectURL', {
      configurable: true,
      value: createObjectURL,
    });
    Object.defineProperty(URL, 'revokeObjectURL', {
      configurable: true,
      value: revokeObjectURL,
    });
    clickSpy = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
  });

  afterEach(() => {
    clickSpy.mockRestore();
    vi.clearAllMocks();
  });

  it('exports all audit records matching the current filters', async () => {
    const user = userEvent.setup();
    renderWithProviders(<AuditPage />);

    expect(await screen.findByText('topic-a')).toBeInTheDocument();
    await user.type(screen.getByPlaceholderText('搜索操作人或操作对象'), 'topic-a');
    await user.click(screen.getByRole('button', { name: /导出/ }));

    await waitFor(() =>
      expect(opsService.exportAuditLogs).toHaveBeenCalledWith({
        search: 'topic-a',
        operationType: undefined,
        resourceType: undefined,
        clusterId: undefined,
        startDate: undefined,
        endDate: undefined,
        result: undefined,
      }),
    );
    expect(createObjectURL).toHaveBeenCalledTimes(1);
    const blob = createObjectURL.mock.calls[0][0] as Blob;
    await expect(blob.text()).resolves.toContain('"2026-08-01 10:00:00","admin"');
    expect(clickSpy).toHaveBeenCalledTimes(1);
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:audit');
  });

  it('loads persisted filter values and forwards their original codes', async () => {
    const user = userEvent.setup();
    renderWithProviders(<AuditPage />);

    expect(await screen.findByText('topic-a')).toBeInTheDocument();
    await user.click(screen.getByRole('combobox', { name: '操作类型' }));
    await user.click(
      await screen.findByText('CREATE TOPIC', { selector: '.ant-select-item-option-content' }),
    );
    expect(screen.getByText('SUCCESS')).toBeInTheDocument();
    await user.click(screen.getByRole('combobox', { name: '资源类型' }));
    await user.click(
      await screen.findByText('CONSUMER GROUP', {
        selector: '.ant-select-item-option-content',
      }),
    );
    await user.click(screen.getByRole('combobox', { name: '集群' }));
    await user.click(
      await screen.findByText('prod-sh', { selector: '.ant-select-item-option-content' }),
    );

    await waitFor(() =>
      expect(opsService.listAuditRecords).toHaveBeenLastCalledWith({
        page: 1,
        pageSize: 20,
        search: undefined,
        operationType: 'CREATE_TOPIC',
        resourceType: 'CONSUMER_GROUP',
        clusterId: 'prod-sh',
        startDate: undefined,
        endDate: undefined,
        result: undefined,
      }),
    );
  });

  it('shows loading state while refreshed records are pending', async () => {
    const user = userEvent.setup();
    vi.mocked(opsService.listAuditRecords)
      .mockResolvedValueOnce({
        items: [
          {
            id: 2,
            timestamp: '2026-08-01 10:00:00',
            operator: 'admin',
            operationType: 'DELETE_TOPIC',
            resourceType: 'TOPIC',
            target: 'topic-a',
            clusterId: 'prod-cn',
            detail: 'removed topic-a',
            result: 'SUCCESS',
            errorMessage: '',
          },
        ],
        total: 1,
        page: 1,
        size: 20,
      })
      .mockImplementationOnce(() => new Promise(() => {}));
    const { container } = renderWithProviders(<AuditPage />);
    expect(await screen.findByText('topic-a')).toBeInTheDocument();

    await user.click(screen.getByRole('combobox', { name: '操作类型' }));
    await user.click(
      await screen.findByText('CREATE TOPIC', { selector: '.ant-select-item-option-content' }),
    );

    await waitFor(() => expect(opsService.listAuditRecords).toHaveBeenCalledTimes(2));
    expect(container.querySelector('.ant-spin-spinning')).not.toBeNull();
  });

  it('still loads audit records when filter options cannot be loaded', async () => {
    vi.mocked(opsService.getAuditFilterOptions).mockRejectedValueOnce(new Error('unavailable'));

    renderWithProviders(<AuditPage />);

    expect(await screen.findByText('topic-a')).toBeInTheDocument();
    expect(opsService.listAuditRecords).toHaveBeenCalled();
  });

  it('refreshes filter options after audit logs are cleaned up', async () => {
    const user = userEvent.setup();
    vi.mocked(opsService.cleanupAuditLogs).mockResolvedValue(3);

    renderWithProviders(<AuditPage />);

    expect(await screen.findByText('topic-a')).toBeInTheDocument();
    expect(opsService.getAuditFilterOptions).toHaveBeenCalledTimes(1);
    await user.click(screen.getByRole('button', { name: '清理日志' }));
    await user.click(await screen.findByRole('button', { name: '确认清理' }));

    await waitFor(() => expect(opsService.getAuditFilterOptions).toHaveBeenCalledTimes(2));
  });
});
