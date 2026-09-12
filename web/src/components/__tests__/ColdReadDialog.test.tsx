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
import ColdReadDialog from '../ColdReadDialog';
import {
  changeColdRead,
  inspectColdRead,
  type ColdReadReceipt,
  type ColdReadSnapshot,
} from '../../api/coldRead';
import useAuthStore from '../../stores/authStore';

vi.mock('../../api/coldRead', () => ({ inspectColdRead: vi.fn(), changeColdRead: vi.fn() }));
// jsdom does not dispatch CSS animation completion events.
const render = (element: ReactElement) =>
  renderComponent(element, {
    wrapper: ({ children }) => (
      <ConfigProvider theme={{ token: { motion: false } }}>{children}</ConfigProvider>
    ),
  });
const fixture: ColdReadSnapshot = {
  brokerName: 'broker-a',
  address: 'master:10911',
  sampledAt: 1788825600000,
  enabled: 'false',
  adaptiveEnabled: 'true',
  defaultThreshold: '1000',
  globalThreshold: '10000',
  globalBytes: '9007199254740993',
  groups: [
    {
      name: 'orders',
      configuredThreshold: '5000',
      adaptiveThreshold: '1000',
      coldBytes: null,
      lastReadMillis: null,
    },
  ],
};
const receipt: ColdReadReceipt = {
  address: 'master:10911',
  group: 'orders',
  action: 'SET',
  threshold: '9007199254740995',
  verified: true,
  verificationError: null,
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
  vi.mocked(inspectColdRead).mockResolvedValue(structuredClone(fixture));
  vi.mocked(changeColdRead).mockResolvedValue(receipt);
});

async function open() {
  const view = render(
    <ColdReadDialog instanceId="instance-a" brokerName="broker-a" onClose={vi.fn()} />,
  );
  await waitFor(() => expect(screen.getByText('9007199254740993')).toBeVisible());
  return view;
}

function selectAndConfirm(threshold = '9007199254740995') {
  fireEvent.click(screen.getByRole('button', { name: 'Use group' }));
  fireEvent.change(screen.getByLabelText('Positive threshold in bytes'), {
    target: { value: threshold },
  });
  fireEvent.click(screen.getByRole('checkbox'));
}

it('explains reset counters, disabled flow control and volatile broker scope', async () => {
  await open();
  expect(screen.getByText(/Counters are bytes accumulated in a reset interval/)).toBeVisible();
  expect(screen.getByText(/Cold-data flow control is disabled/)).toBeVisible();
  expect(screen.getAllByText('Not reported')).toHaveLength(2);
  expect(screen.getByRole('button', { name: 'Apply group limit' })).toBeDisabled();
});

it('submits an exact byte threshold only after target confirmation', async () => {
  await open();
  selectAndConfirm();
  fireEvent.click(screen.getByRole('button', { name: 'Apply group limit' }));
  await waitFor(() => expect(screen.getByText('Broker configuration verified')).toBeVisible());
  expect(changeColdRead).toHaveBeenCalledWith({
    instanceId: 'instance-a',
    brokerName: 'broker-a',
    group: 'orders',
    action: 'SET',
    threshold: '9007199254740995',
  });
  expect(screen.getByRole('button', { name: 'Apply group limit' })).toBeDisabled();
});

it('invalidates confirmation after changing the operation and omits threshold on removal', async () => {
  await open();
  selectAndConfirm();
  fireEvent.click(screen.getByLabelText('Remove admin override'));
  expect(screen.getByRole('checkbox')).not.toBeChecked();
  expect(screen.queryByLabelText('Positive threshold in bytes')).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('checkbox'));
  fireEvent.click(screen.getByRole('button', { name: 'Apply group limit' }));
  await waitFor(() =>
    expect(changeColdRead).toHaveBeenCalledWith({
      instanceId: 'instance-a',
      brokerName: 'broker-a',
      group: 'orders',
      action: 'REMOVE',
    }),
  );
});

it('rejects overflow, fractional limits and reserved adaptive names in the form', async () => {
  await open();
  for (const text of ['9223372036854775808', '1.5', '0']) {
    selectAndConfirm(text);
    expect(screen.getByRole('button', { name: 'Apply group limit' })).toBeDisabled();
  }
  selectAndConfirm('123');
  fireEvent.change(screen.getByLabelText('Consumer group'), {
    target: { value: 'orders||adaptive' },
  });
  fireEvent.click(screen.getByRole('checkbox'));
  expect(screen.getByRole('button', { name: 'Apply group limit' })).toBeDisabled();
  expect(changeColdRead).not.toHaveBeenCalled();
});

it('locks the form and prevents duplicate requests while saving', async () => {
  let resolve!: (value: ColdReadReceipt) => void;
  vi.mocked(changeColdRead).mockReturnValue(
    new Promise((done) => {
      resolve = done;
    }),
  );
  await open();
  selectAndConfirm();
  const apply = screen.getByRole('button', { name: 'Apply group limit' });
  fireEvent.click(apply);
  fireEvent.click(apply);
  expect(changeColdRead).toHaveBeenCalledTimes(1);
  expect(screen.getByLabelText('Consumer group')).toBeDisabled();
  expect(screen.getByRole('button', { name: 'Close' })).toBeDisabled();
  await act(async () => resolve(receipt));
});

it('shows incomplete verification without claiming the limit was applied', async () => {
  vi.mocked(changeColdRead).mockResolvedValue({
    ...receipt,
    verified: false,
    verificationError: 'Refresh before retrying.',
  });
  await open();
  selectAndConfirm();
  fireEvent.click(screen.getByRole('button', { name: 'Apply group limit' }));
  await waitFor(() =>
    expect(screen.getByText('Broker acknowledged; verification incomplete')).toBeVisible(),
  );
  expect(screen.queryByText('Broker configuration verified')).not.toBeInTheDocument();
  expect(changeColdRead).toHaveBeenCalledTimes(1);
});

it('keeps failed writes visible without automatic retries', async () => {
  vi.mocked(changeColdRead).mockRejectedValue(new Error('Write denied'));
  await open();
  selectAndConfirm();
  fireEvent.click(screen.getByRole('button', { name: 'Apply group limit' }));
  await waitFor(() => expect(screen.getByText('Write denied')).toBeVisible());
  expect(changeColdRead).toHaveBeenCalledTimes(1);
});

it('aborts a closing inspection and ignores the result', async () => {
  let resolve!: (value: ColdReadSnapshot) => void;
  vi.mocked(inspectColdRead).mockReturnValue(
    new Promise((done) => {
      resolve = done;
    }),
  );
  const { unmount } = render(
    <ColdReadDialog instanceId="instance-a" brokerName="broker-a" onClose={vi.fn()} />,
  );
  await waitFor(() => expect(inspectColdRead).toHaveBeenCalledTimes(1));
  const signal = vi.mocked(inspectColdRead).mock.calls[0][2];
  unmount();
  expect(signal.aborted).toBe(true);
  await act(async () => resolve(fixture));
  expect(screen.queryByText('9007199254740993')).not.toBeInTheDocument();
});

it('ignores a write receipt after the owner unmounts the dialog', async () => {
  let resolve!: (value: ColdReadReceipt) => void;
  vi.mocked(changeColdRead).mockReturnValue(
    new Promise((done) => {
      resolve = done;
    }),
  );
  const { unmount } = await open();
  selectAndConfirm();
  fireEvent.click(screen.getByRole('button', { name: 'Apply group limit' }));
  unmount();
  await act(async () => resolve(receipt));
  expect(screen.queryByText('Broker configuration verified')).not.toBeInTheDocument();
});

it('allows a reader to inspect but disables modification', async () => {
  useAuthStore.setState({ admin: false });
  await open();
  expect(screen.getByText('Administrator access is required to change limits.')).toBeVisible();
  expect(screen.getByLabelText('Consumer group')).toBeDisabled();
  expect(screen.getByRole('button', { name: 'Use group' })).toBeDisabled();
});

it('can refresh an unavailable snapshot', async () => {
  vi.mocked(inspectColdRead).mockRejectedValueOnce(new Error('Broker unsupported'));
  render(<ColdReadDialog instanceId="instance-a" brokerName="broker-a" onClose={vi.fn()} />);
  await waitFor(() => expect(screen.getByText('Broker unsupported')).toBeVisible());
  fireEvent.click(screen.getByRole('button', { name: 'Refresh snapshot' }));
  await waitFor(() => expect(screen.getByText('9007199254740993')).toBeVisible());
  expect(screen.queryByText('Broker unsupported')).not.toBeInTheDocument();
});
