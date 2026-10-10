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

import { App, message } from 'antd';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type React from 'react';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { LangProvider } from '../../../i18n/LangContext';
import { LANGUAGE_STORAGE_KEY } from '../../../i18n/languagePreference';
import * as opsService from '../../../services/opsService';
import AuditPage from '../audit';

vi.mock('../../../services/opsService', () => ({
  cleanupAuditLogs: vi.fn(),
  exportAuditLogs: vi.fn(),
  getAuditFilterOptions: vi.fn(),
  getAuditSummary: vi.fn(),
  listAuditRecords: vi.fn(),
}));

const renderInEnglish = (ui: React.ReactElement) => {
  localStorage.setItem(LANGUAGE_STORAGE_KEY, 'en');
  render(
    <App>
      <LangProvider>{ui}</LangProvider>
    </App>,
  );
};

const chinese = /[\u4e00-\u9fff]/;

describe('Audit page localization', () => {
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
      operationTypes: [],
      resourceTypes: [],
      clusterIds: [],
      results: [],
    });
    vi.mocked(opsService.listAuditRecords).mockResolvedValue({
      items: [],
      total: 0,
      page: 1,
      size: 20,
    });
    vi.mocked(opsService.getAuditSummary).mockResolvedValue({
      total: 0,
      successful: 0,
      failed: 0,
      partial: 0,
      uniqueOperators: 0,
      latestAt: null,
      byOperation: [],
      byResourceType: [],
    });
  });

  afterEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
    vi.clearAllMocks();
  });

  it('routes the record and summary load failures through the translation table', async () => {
    const errorSpy = vi.spyOn(message, 'error').mockImplementation(vi.fn());
    vi.mocked(opsService.listAuditRecords).mockRejectedValue(new Error('unavailable'));
    vi.mocked(opsService.getAuditSummary).mockRejectedValue(new Error('unavailable'));

    renderInEnglish(<AuditPage />);

    await waitFor(() =>
      expect(errorSpy).toHaveBeenCalledWith('Failed to load audit logs. Try again later.'),
    );
    await waitFor(() =>
      expect(errorSpy).toHaveBeenCalledWith('Failed to load the audit summary. Try again later.'),
    );
    expect(errorSpy.mock.calls.map((call) => String(call[0])).join(' ')).not.toMatch(chinese);
  });

  it('routes the cleanup and export failures through the translation table', async () => {
    const user = userEvent.setup();
    const errorSpy = vi.spyOn(message, 'error').mockImplementation(vi.fn());
    vi.mocked(opsService.cleanupAuditLogs).mockRejectedValue(new Error('unavailable'));
    vi.mocked(opsService.exportAuditLogs).mockRejectedValue(new Error('unavailable'));

    renderInEnglish(<AuditPage />);

    await user.click(await screen.findByRole('button', { name: /Export/ }));
    await waitFor(() =>
      expect(errorSpy).toHaveBeenCalledWith('Failed to export audit logs. Try again later.'),
    );

    await user.click(await screen.findByRole('button', { name: 'Cleanup' }));
    await user.click(await screen.findByRole('button', { name: 'Confirm Cleanup' }));
    await waitFor(() =>
      expect(errorSpy).toHaveBeenCalledWith('Failed to clean audit logs. Try again later.'),
    );
    expect(errorSpy.mock.calls.map((call) => String(call[0])).join(' ')).not.toMatch(chinese);
  });

  it('renders the cleanup-window suffix through the translation table', async () => {
    const user = userEvent.setup();

    renderInEnglish(<AuditPage />);

    await user.click(await screen.findByRole('button', { name: 'Cleanup' }));

    expect(await screen.findByText('days of logs')).toBeInTheDocument();
    expect(screen.queryByText('天之前的日志')).not.toBeInTheDocument();
  });
});
