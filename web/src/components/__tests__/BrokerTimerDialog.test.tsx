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
import BrokerTimerDialog from '../BrokerTimerDialog';
import { inspectBrokerTimer, type BrokerTimerSnapshot } from '../../api/brokerTimer';

vi.mock('../../api/brokerTimer', () => ({ inspectBrokerTimer: vi.fn() }));

// jsdom does not dispatch CSS animation completion events.
const render = (element: ReactElement) =>
  renderComponent(element, {
    wrapper: ({ children }) => (
      <ConfigProvider theme={{ token: { motion: false } }}>{children}</ConfigProvider>
    ),
  });

const fixture: BrokerTimerSnapshot = {
  brokerName: 'broker-a',
  address: 'master:10911',
  sampledAt: 1788825600000,
  configuration: { values: { timerWheelEnable: 'true', timerPrecisionMs: '1000' }, error: null },
  runtime: { values: { timerOffsetBehind: '9007199254740993', timerReadBehind: '7' }, error: null },
};

beforeEach(() => {
  vi.clearAllMocks();
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
  vi.mocked(inspectBrokerTimer).mockResolvedValue(structuredClone(fixture));
});

it('shows exact counters, units, missing fields and sampling limitations', async () => {
  render(<BrokerTimerDialog instanceId="instance-a" brokerName="broker-a" onClose={vi.fn()} />);
  await waitFor(() => expect(screen.getByText('9007199254740993')).toBeVisible());
  expect(screen.getByText('Dequeue behind (seconds)')).toBeVisible();
  expect(screen.getByText('Enqueue behind (queue positions)')).toBeVisible();
  expect(screen.getAllByText('Not reported').length).toBeGreaterThan(0);
  expect(screen.getByText(/Pending entries include messages that are not due yet/)).toBeVisible();
  expect(inspectBrokerTimer).toHaveBeenCalledWith(
    'instance-a',
    'broker-a',
    expect.any(AbortSignal),
  );
});

it('warns when the timer wheel is disabled even with zero runtime metrics', async () => {
  vi.mocked(inspectBrokerTimer).mockResolvedValue({
    ...fixture,
    configuration: { values: { timerWheelEnable: 'false' }, error: null },
    runtime: { values: { timerReadBehind: '0' }, error: null },
  });
  render(<BrokerTimerDialog instanceId="instance-a" brokerName="broker-a" onClose={vi.fn()} />);
  await waitFor(() => expect(screen.getByText(/Timer wheel is disabled/)).toBeVisible());
  expect(screen.getByText('0')).toBeVisible();
});

it('keeps configuration visible after the runtime RPC fails', async () => {
  vi.mocked(inspectBrokerTimer).mockResolvedValue({
    ...fixture,
    runtime: { values: {}, error: 'Runtime denied' },
  });
  render(<BrokerTimerDialog instanceId="instance-a" brokerName="broker-a" onClose={vi.fn()} />);
  await waitFor(() => expect(screen.getByText('Runtime denied')).toBeVisible());
  expect(screen.getByText('1000')).toBeVisible();
  expect(screen.getByText(/Partial snapshot/)).toBeVisible();
});

it('refreshes after a top-level failure and clears its error', async () => {
  vi.mocked(inspectBrokerTimer).mockRejectedValueOnce(new Error('Master unavailable'));
  render(<BrokerTimerDialog instanceId="instance-a" brokerName="broker-a" onClose={vi.fn()} />);
  await waitFor(() => expect(screen.getByText('Master unavailable')).toBeVisible());
  fireEvent.click(screen.getByRole('button', { name: /Refresh snapshot/ }));
  await waitFor(() => expect(screen.getByText('9007199254740993')).toBeVisible());
  expect(screen.queryByText('Master unavailable')).not.toBeInTheDocument();
  expect(inspectBrokerTimer).toHaveBeenCalledTimes(2);
});

it('aborts on unmount and ignores a late result', async () => {
  let resolve!: (value: BrokerTimerSnapshot) => void;
  vi.mocked(inspectBrokerTimer).mockReturnValue(
    new Promise((done) => {
      resolve = done;
    }),
  );
  const { unmount } = render(
    <BrokerTimerDialog instanceId="instance-a" brokerName="broker-a" onClose={vi.fn()} />,
  );
  await waitFor(() => expect(inspectBrokerTimer).toHaveBeenCalledTimes(1));
  const signal = vi.mocked(inspectBrokerTimer).mock.calls[0][2];
  unmount();
  expect(signal.aborted).toBe(true);
  await act(async () => resolve(fixture));
  expect(screen.queryByText('9007199254740993')).not.toBeInTheDocument();
});

it('isolates a late response from a previous instance', async () => {
  let resolve!: (value: BrokerTimerSnapshot) => void;
  vi.mocked(inspectBrokerTimer).mockReturnValueOnce(
    new Promise((done) => {
      resolve = done;
    }),
  );
  const { rerender } = render(
    <BrokerTimerDialog instanceId="instance-a" brokerName="broker-a" onClose={vi.fn()} />,
  );
  await waitFor(() => expect(inspectBrokerTimer).toHaveBeenCalledTimes(1));
  const signal = vi.mocked(inspectBrokerTimer).mock.calls[0][2];
  rerender(<BrokerTimerDialog instanceId="instance-b" brokerName="broker-b" onClose={vi.fn()} />);
  await waitFor(() => expect(screen.getByText('9007199254740993')).toBeVisible());
  await act(async () => resolve({ ...fixture, runtime: { values: {}, error: 'Old response' } }));
  expect(signal.aborted).toBe(true);
  expect(screen.queryByText('Old response')).not.toBeInTheDocument();
});

it('closes through the owner callback', async () => {
  const close = vi.fn();
  render(<BrokerTimerDialog instanceId="instance-a" brokerName="broker-a" onClose={close} />);
  await waitFor(() => expect(inspectBrokerTimer).toHaveBeenCalledTimes(1));
  fireEvent.click(screen.getAllByRole('button', { name: 'Close' })[1]);
  expect(close).toHaveBeenCalledOnce();
});
