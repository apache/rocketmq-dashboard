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
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
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
    # Rule 2:
    - alert: ConsumerLagHigh
      expr: rocketmq_consumer_lag_messages > 100000
      for: 10m
      labels:
        severity: warning
        team: consumer
      annotations:
        summary: "Consumer lag is high"
        description: "Consumer lag has exceeded the threshold"
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
  let createObjectURL: ReturnType<typeof vi.fn>;
  let revokeObjectURL: ReturnType<typeof vi.fn>;
  let clickSpy: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    createObjectURL = vi.fn().mockReturnValue('blob:alert-rules');
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
    vi.mocked(queryAlertRules).mockResolvedValue({ rules: rulesYaml });
  });

  afterEach(() => {
    clickSpy.mockRestore();
  });

  it('loads alert rules after mount', async () => {
    renderWithProviders(<AlertManagementPage />);

    await waitFor(() => {
      expect(queryAlertRules).toHaveBeenCalledTimes(1);
    });

    expect(await screen.findByText('BrokerDown')).toBeInTheDocument();
  });

  it('exports the server-side YAML verbatim when rows are selected', async () => {
    const user = userEvent.setup();
    renderWithProviders(<AlertManagementPage />);

    const brokerRule = await screen.findByText('BrokerDown');
    expect(screen.getByText('ConsumerLagHigh')).toBeInTheDocument();

    const brokerRow = brokerRule.closest('tr');
    expect(brokerRow).not.toBeNull();
    await user.click(within(brokerRow!).getByRole('checkbox'));
    await user.click(screen.getByRole('button', { name: '导出 YAML' }));

    await waitFor(() => {
      expect(queryAlertRules).toHaveBeenCalledTimes(2);
    });
    expect(createObjectURL).toHaveBeenCalledTimes(1);
    const blob = createObjectURL.mock.calls[0][0] as Blob;
    const yaml = await blob.text();
    expect(yaml).toBe(rulesYaml);
    expect(clickSpy).toHaveBeenCalledTimes(1);
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:alert-rules');
  });

  it('exports all enabled alert rules when no rows are selected', async () => {
    const user = userEvent.setup();
    renderWithProviders(<AlertManagementPage />);

    await screen.findByText('BrokerDown');
    await user.click(screen.getByRole('button', { name: '导出 YAML' }));

    const blob = createObjectURL.mock.calls[0][0] as Blob;
    const yaml = await blob.text();
    expect(yaml).toContain('alert: BrokerDown');
    expect(yaml).toContain('alert: ConsumerLagHigh');
  });

  it('keeps the rule unchanged and warns when the toggle is clicked', async () => {
    const user = userEvent.setup();
    renderWithProviders(<AlertManagementPage />);

    const brokerRule = await screen.findByText('BrokerDown');
    const brokerRow = brokerRule.closest('tr');
    expect(brokerRow).not.toBeNull();

    await user.click(within(brokerRow!).getByRole('checkbox'));
    expect(screen.getByRole('button', { name: '导出 YAML' })).toBeInTheDocument();

    await user.click(within(brokerRow!).getByRole('switch'));

    expect(
      await screen.findByText(
        'Alert rule changes are unavailable until a persisted rule editor is available.',
      ),
    ).toBeInTheDocument();
    expect(screen.getByText('BrokerDown')).toBeInTheDocument();
  });

  it('preserves selected rules while filtering the table', async () => {
    const user = userEvent.setup();
    renderWithProviders(<AlertManagementPage />);

    const brokerRule = await screen.findByText('BrokerDown');
    const brokerRow = brokerRule.closest('tr');
    expect(brokerRow).not.toBeNull();
    await user.click(within(brokerRow!).getByRole('checkbox'));

    await user.type(screen.getByRole('textbox'), 'ConsumerLagHigh');

    expect(screen.queryByText('BrokerDown')).not.toBeInTheDocument();
    expect(screen.getByText('ConsumerLagHigh')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '导出 YAML' }));

    const blob = createObjectURL.mock.calls[0][0] as Blob;
    const yaml = await blob.text();
    expect(yaml).toContain('alert: BrokerDown');
    expect(yaml).toContain('alert: ConsumerLagHigh');
  });
});
