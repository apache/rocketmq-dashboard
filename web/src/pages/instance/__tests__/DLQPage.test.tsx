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
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type React from 'react';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import type { DLQGroup } from '../../../api/message';
import { LangProvider } from '../../../i18n/LangContext';
import * as messageService from '../../../services/messageService';
import DLQPage from '../dlq';

vi.mock('../../../services/messageService', () => ({
  listDLQGroups: vi.fn(),
  resendDLQ: vi.fn(),
}));

const dlqGroup: DLQGroup = {
  groupName: 'cg-order',
  dlqTopic: '%DLQ%cg-order',
  messageCount: 7,
  lastEnqueueTime: '2026-07-24T10:00:00Z',
  retryCount: 3,
  status: 'ACTIVE',
};

const renderWithProviders = (ui: React.ReactElement) =>
  render(
    <App>
      <LangProvider>{ui}</LangProvider>
    </App>,
  );

describe('DLQ page', () => {
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
    createObjectURL = vi.fn().mockReturnValue('blob:dlq');
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
    vi.mocked(messageService.listDLQGroups).mockResolvedValue([dlqGroup]);
  });

  afterEach(() => {
    clickSpy.mockRestore();
    vi.clearAllMocks();
  });

  it('loads DLQ groups through the service layer', async () => {
    renderWithProviders(<DLQPage />);

    expect(await screen.findByText('cg-order')).toBeInTheDocument();
    expect(screen.getByText('%DLQ%cg-order')).toBeInTheDocument();
    expect(messageService.listDLQGroups).toHaveBeenCalledTimes(1);
  });

  it('opens a detail dialog with the selected group metadata', async () => {
    const user = userEvent.setup();
    renderWithProviders(<DLQPage />);

    await screen.findByText('cg-order');
    await user.click(screen.getByRole('button', { name: /查看详情/ }));

    expect(await screen.findByText('死信队列详情')).toBeInTheDocument();
    expect(screen.getAllByText('cg-order')).toHaveLength(2);
    expect(screen.getAllByText('%DLQ%cg-order')).toHaveLength(2);
    expect(screen.getByText('ACTIVE')).toBeInTheDocument();
  });

  it('exports the selected group summary as CSV', async () => {
    const user = userEvent.setup();
    renderWithProviders(<DLQPage />);

    await screen.findByText('cg-order');
    await user.click(screen.getByRole('button', { name: /导出/ }));

    expect(createObjectURL).toHaveBeenCalledTimes(1);
    const blob = createObjectURL.mock.calls[0][0] as Blob;
    await expect(blob.text()).resolves.toContain('"cg-order","%DLQ%cg-order","7","3","ACTIVE"');
    expect(clickSpy).toHaveBeenCalledTimes(1);
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:dlq');
  });
});
