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
import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { LangProvider } from '../../../i18n/LangContext';
import { listAlertRulesPage, type AlertRule } from '../../../services/opsService';
import AlertsPage from '../alerts';

vi.mock('../../../services/instanceService', () => ({
  listInstances: vi.fn().mockResolvedValue([]),
}));
vi.mock('../../../services/opsService', () => ({
  listAlertRulesPage: vi.fn(),
  listAlertRuleRuntime: vi.fn().mockResolvedValue([]),
  listNativeAlertMetrics: vi.fn().mockResolvedValue([]),
  toggleAlertRule: vi.fn(),
  bulkToggleAlertRules: vi.fn(),
  bulkDeleteAlertRules: vi.fn(),
}));

const rules: AlertRule[] = [
  {
    id: 1,
    name: 'Broker disk usage',
    metric: 'disk',
    operator: '>',
    threshold: 85,
    thresholdUnit: '%',
    duration: '5m',
    channels: ['email'],
    enabled: false,
    lastTriggered: null,
    description: 'disk usage',
  },
];

describe('AlertsPage pagination total', () => {
  beforeAll(() => {
    Object.defineProperty(window, 'matchMedia', {
      writable: true,
      value: vi.fn().mockImplementation(() => ({
        matches: false,
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
    localStorage.setItem('language', 'zh');
    vi.mocked(listAlertRulesPage).mockResolvedValue({
      items: rules,
      total: rules.length,
      page: 1,
      size: 20,
    });
  });

  it('shows the total count in the pagination total text', async () => {
    render(
      <App>
        <LangProvider>
          <AlertsPage />
        </LangProvider>
      </App>,
    );

    await screen.findByText('Broker disk usage');
    await waitFor(() => {
      const text = document.querySelector('.ant-pagination-total-text')?.textContent ?? '';
      // The totalRules template has no {count} placeholder, so the count must
      // be appended explicitly - otherwise the pagination shows only the label.
      expect(text).toMatch(/\d/);
      expect(text).toContain(String(rules.length));
    });
  });
});