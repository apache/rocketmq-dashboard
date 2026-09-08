/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { act, fireEvent, render as renderComponent, screen, waitFor } from '@testing-library/react';
import type { ReactElement } from 'react';
import { ConfigProvider } from 'antd';
import { beforeEach, expect, it, vi } from 'vitest';
import TransactionRecoveryDialog from '../TransactionRecoveryDialog';
import {
  inspectTransactionRecovery,
  recoverTransactionCheck,
  type TransactionRecoveryPreview,
} from '../../api/transactionRecovery';
import useAuthStore from '../../stores/authStore';

vi.mock('../../api/transactionRecovery', () => ({
  inspectTransactionRecovery: vi.fn(),
  recoverTransactionCheck: vi.fn(),
}));
// jsdom does not dispatch CSS animation completion events.
const render = (element: ReactElement) =>
  renderComponent(element, {
    wrapper: ({ children }) => (
      <ConfigProvider theme={{ token: { motion: false } }}>{children}</ConfigProvider>
    ),
  });
const id = '7F00000100002A9F000000000000002A';
const fixture: TransactionRecoveryPreview = {
  offsetMessageId: id,
  brokerAddress: '127.0.0.1:10911',
  originalTopic: 'orders',
  producerGroup: 'payment-producer',
  checkTimes: '15',
  transactionId: null,
  storedAt: 1788825600000,
};
beforeEach(() => {
  vi.clearAllMocks();
  useAuthStore.setState({ admin: true });
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: vi.fn().mockReturnValue({
      matches: false,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    }),
  });
  vi.mocked(inspectTransactionRecovery).mockResolvedValue(fixture);
  vi.mocked(recoverTransactionCheck).mockResolvedValue(fixture);
});

async function inspect() {
  const view = render(<TransactionRecoveryDialog instanceId="instance-a" onClose={vi.fn()} />);
  fireEvent.change(screen.getByLabelText('Discarded physical message ID'), {
    target: { value: id },
  });
  fireEvent.click(screen.getByRole('button', { name: 'Inspect discarded transaction' }));
  await waitFor(() => expect(screen.getByText('payment-producer')).toBeVisible());
  return view;
}

it('requires a discarded physical ID and previews destination without recovery', async () => {
  await inspect();
  expect(screen.getByText('orders')).toBeVisible();
  expect(screen.getByText('15')).toBeVisible();
  expect(screen.getByText('Not reported')).toBeVisible();
  expect(screen.getByRole('button', { name: 'Recover checks' })).toBeDisabled();
  expect(inspectTransactionRecovery).toHaveBeenCalledWith({
    instanceId: 'instance-a',
    offsetMessageId: id,
  });
  expect(recoverTransactionCheck).not.toHaveBeenCalled();
});

it('requires target confirmation and reports acceptance without claiming a transaction outcome', async () => {
  await inspect();
  fireEvent.click(screen.getByRole('checkbox'));
  fireEvent.click(screen.getByRole('button', { name: 'Recover checks' }));
  await waitFor(() =>
    expect(screen.getByText('Broker accepted transaction check recovery')).toBeVisible(),
  );
  expect(screen.getByText(/The transaction outcome is pending producer checks/)).toBeVisible();
  expect(recoverTransactionCheck).toHaveBeenCalledWith({
    instanceId: 'instance-a',
    offsetMessageId: id,
  });
  expect(screen.getByRole('button', { name: 'Recover checks' })).toBeDisabled();
});

it('clears inspected metadata and confirmation when the source ID changes', async () => {
  await inspect();
  fireEvent.click(screen.getByRole('checkbox'));
  fireEvent.change(screen.getByLabelText('Discarded physical message ID'), {
    target: { value: 'not-an-offset-id' },
  });
  expect(screen.queryByText('payment-producer')).not.toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Inspect discarded transaction' })).toBeDisabled();
  expect(screen.getByRole('button', { name: 'Recover checks' })).toBeDisabled();
});

it('locks the dialog and sends only one recovery while pending', async () => {
  let resolve!: (value: TransactionRecoveryPreview) => void;
  vi.mocked(recoverTransactionCheck).mockReturnValue(
    new Promise((done) => {
      resolve = done;
    }),
  );
  await inspect();
  fireEvent.click(screen.getByRole('checkbox'));
  const recover = screen.getByRole('button', { name: 'Recover checks' });
  fireEvent.click(recover);
  fireEvent.click(recover);
  expect(recoverTransactionCheck).toHaveBeenCalledTimes(1);
  expect(screen.getByLabelText('Discarded physical message ID')).toBeDisabled();
  expect(screen.getByRole('button', { name: 'Close' })).toBeDisabled();
  await act(async () => resolve(fixture));
});

it('clears confirmation after failure and does not automatically retry', async () => {
  vi.mocked(recoverTransactionCheck).mockRejectedValue(new Error('Broker rejected recovery'));
  await inspect();
  fireEvent.click(screen.getByRole('checkbox'));
  fireEvent.click(screen.getByRole('button', { name: 'Recover checks' }));
  await waitFor(() => expect(screen.getByText('Broker rejected recovery')).toBeVisible());
  expect(screen.getByRole('checkbox')).not.toBeChecked();
  expect(recoverTransactionCheck).toHaveBeenCalledTimes(1);
  expect(screen.queryByText('Broker accepted transaction check recovery')).not.toBeInTheDocument();
});

it('ignores a late recovery receipt after the owner unmounts', async () => {
  let resolve!: (value: TransactionRecoveryPreview) => void;
  vi.mocked(recoverTransactionCheck).mockReturnValue(
    new Promise((done) => {
      resolve = done;
    }),
  );
  const { unmount } = await inspect();
  fireEvent.click(screen.getByRole('checkbox'));
  fireEvent.click(screen.getByRole('button', { name: 'Recover checks' }));
  unmount();
  await act(async () => resolve(fixture));
  expect(screen.queryByText('Broker accepted transaction check recovery')).not.toBeInTheDocument();
});

it('shows preview failures without exposing a recovery action', async () => {
  vi.mocked(inspectTransactionRecovery).mockRejectedValue(new Error('Source message has expired'));
  render(<TransactionRecoveryDialog instanceId="instance-a" onClose={vi.fn()} />);
  fireEvent.change(screen.getByLabelText('Discarded physical message ID'), {
    target: { value: id },
  });
  fireEvent.click(screen.getByRole('button', { name: 'Inspect discarded transaction' }));
  await waitFor(() => expect(screen.getByText('Source message has expired')).toBeVisible());
  expect(screen.getByRole('button', { name: 'Recover checks' })).toBeDisabled();
});

it('prevents non-admin users from inspecting or recovering transaction messages', () => {
  useAuthStore.setState({ admin: false });
  render(<TransactionRecoveryDialog instanceId="instance-a" onClose={vi.fn()} />);
  expect(screen.getByLabelText('Discarded physical message ID')).toBeDisabled();
  expect(screen.getByRole('button', { name: 'Inspect discarded transaction' })).toBeDisabled();
});
