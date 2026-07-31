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

import type { ReactElement } from 'react';
import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { App } from 'antd';
import { LangProvider } from '../../../i18n/LangContext';
import AlertManagementPage from '../AlertManagement';
import { queryAlertRules } from '../../../api/alertManagement';

vi.mock('../../../api/alertManagement', () => ({
  queryAlertRules: vi.fn(),
}));

const rulesYaml = `
groups:
- name: rocketmq-broker.rules
  rules:
    # Rule 1:
    - alert: BrokerDown
      expr: up{job="rocketmq-broker"} == 0
      for: 5m
      labels:
        severity: critical
        team: broker
      annotations:
        summary: "Broker unavailable"
        description: "Broker has been unavailable for five minutes"
`;

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

const renderWithProviders = (ui: ReactElement) => {
  return render(
    <App>
      <LangProvider>{ui}</LangProvider>
    </App>,
  );
};

describe('AlertManagementPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    vi.mocked(queryAlertRules).mockResolvedValue({ rules: rulesYaml });
  });

  it('loads alert rules after mount', async () => {
    renderWithProviders(<AlertManagementPage />);

    await waitFor(() => {
      expect(queryAlertRules).toHaveBeenCalledTimes(1);
    });

    expect(await screen.findByText('BrokerDown')).toBeInTheDocument();
  });
});
