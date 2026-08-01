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
    vi.mocked(opsService.listAuditRecords).mockResolvedValue({
      items: [
        {
          id: 'audit-1',
          timestamp: '2026-08-01 10:00:00',
          operator: 'admin',
          operationType: '删除Topic',
          target: 'topic-a',
          detail: 'removed topic-a',
          ipAddress: '127.0.0.1',
          result: 'SUCCESS',
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
});
