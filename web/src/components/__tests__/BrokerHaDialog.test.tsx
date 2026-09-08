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
import { beforeEach, describe, expect, it, vi } from 'vitest';
import BrokerHaDialog from '../BrokerHaDialog';
import { inspectBrokerHa, type BrokerHaSnapshot } from '../../api/brokerHa';

vi.mock('../../api/brokerHa', () => ({ inspectBrokerHa: vi.fn() }));

// jsdom does not dispatch CSS animation completion events.
const render = (element: ReactElement) =>
  renderComponent(element, {
    wrapper: ({ children }) => (
      <ConfigProvider theme={{ token: { motion: false } }}>{children}</ConfigProvider>
    ),
  });

const fixture: BrokerHaSnapshot = {
  brokerName: 'broker-a',
  sampledAt: 1788825600000,
  complete: true,
  nodes: [
    {
      brokerId: '0',
      address: 'master:10911',
      error: null,
      master: true,
      maxOffset: '9007199254740993',
      inSyncSlaveCount: 0,
      connections: [
        {
          address: 'replica:10912',
          inSync: false,
          ackOffset: '9007199254740000',
          differenceBytes: '993',
          transferOffset: '9007199254740001',
          bytesPerSecond: '1024',
        },
      ],
      replica: null,
    },
  ],
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
  vi.mocked(inspectBrokerHa).mockResolvedValue(structuredClone(fixture));
});

describe('BrokerHaDialog', () => {
  it('keeps byte offsets exact and expands master connection details', async () => {
    render(<BrokerHaDialog instanceId="instance-a" brokerName="broker-a" onClose={vi.fn()} />);
    await waitFor(() => expect(screen.getByText('9007199254740993')).toBeVisible());
    fireEvent.click(screen.getByRole('button', { name: 'Expand row' }));
    await waitFor(() => expect(screen.getByText('9007199254740001')).toBeVisible());
    expect(screen.getByText('993')).toBeVisible();
    expect(screen.getByText('No')).toBeVisible();
    expect(inspectBrokerHa).toHaveBeenCalledWith('instance-a', 'broker-a', expect.any(AbortSignal));
  });

  it('shows partial errors without labeling an unavailable node as a replica', async () => {
    vi.mocked(inspectBrokerHa).mockResolvedValue({
      ...fixture,
      complete: false,
      nodes: [
        { ...fixture.nodes[0], master: null, maxOffset: null, error: 'HA service unsupported' },
      ],
    });
    render(<BrokerHaDialog instanceId="instance-a" brokerName="broker-a" onClose={vi.fn()} />);
    await waitFor(() => expect(screen.getByText(/Partial snapshot/)).toBeVisible());
    expect(screen.getByText('Unavailable')).toBeVisible();
    fireEvent.click(screen.getByRole('button', { name: 'Expand row' }));
    await waitFor(() => expect(screen.getByText('HA service unsupported')).toBeVisible());
  });

  it('displays replica progress and distinguishes unreported timestamps', async () => {
    vi.mocked(inspectBrokerHa).mockResolvedValue({
      ...fixture,
      nodes: [
        {
          ...fixture.nodes[0],
          master: false,
          connections: [],
          inSyncSlaveCount: null,
          replica: {
            masterAddress: 'master:10912',
            maxOffset: '123',
            masterFlushOffset: '100',
            bytesPerSecond: '2048',
            lastReadTimestamp: 0,
            lastWriteTimestamp: 1788825600000,
          },
        },
      ],
    });
    render(<BrokerHaDialog instanceId="instance-a" brokerName="broker-a" onClose={vi.fn()} />);
    await screen.findByText('Replica');
    fireEvent.click(screen.getByRole('button', { name: 'Expand row' }));
    await waitFor(() => expect(screen.getByText('master:10912')).toBeVisible());
    expect(screen.getByText('Not reported')).toBeVisible();
    expect(screen.getByText('2048')).toBeVisible();
  });

  it('retries failed requests and prevents concurrent refreshes', async () => {
    vi.mocked(inspectBrokerHa).mockRejectedValueOnce(new Error('Instance unavailable'));
    render(<BrokerHaDialog instanceId="instance-a" brokerName="broker-a" onClose={vi.fn()} />);
    await waitFor(() => expect(screen.getByText('Instance unavailable')).toBeVisible());
    fireEvent.click(screen.getByRole('button', { name: /Refresh snapshot/ }));
    await waitFor(() => expect(screen.getByText('9007199254740993')).toBeVisible());
    expect(screen.queryByText('Instance unavailable')).not.toBeInTheDocument();
    expect(inspectBrokerHa).toHaveBeenCalledTimes(2);
  });

  it('aborts a closed dialog request and ignores its late result', async () => {
    let resolve!: (value: BrokerHaSnapshot) => void;
    vi.mocked(inspectBrokerHa).mockReturnValue(
      new Promise((done) => {
        resolve = done;
      }),
    );
    const { unmount } = render(
      <BrokerHaDialog instanceId="instance-a" brokerName="broker-a" onClose={vi.fn()} />,
    );
    await waitFor(() => expect(inspectBrokerHa).toHaveBeenCalledTimes(1));
    const signal = vi.mocked(inspectBrokerHa).mock.calls[0][2];
    unmount();
    expect(signal.aborted).toBe(true);
    await act(async () => resolve(fixture));
    expect(screen.queryByText('9007199254740993')).not.toBeInTheDocument();
  });

  it('does not let an old instance replace the new instance snapshot', async () => {
    let resolve!: (value: BrokerHaSnapshot) => void;
    vi.mocked(inspectBrokerHa).mockReturnValueOnce(
      new Promise((done) => {
        resolve = done;
      }),
    );
    const { rerender } = render(
      <BrokerHaDialog instanceId="instance-a" brokerName="broker-a" onClose={vi.fn()} />,
    );
    await waitFor(() => expect(inspectBrokerHa).toHaveBeenCalledTimes(1));
    const signal = vi.mocked(inspectBrokerHa).mock.calls[0][2];
    rerender(<BrokerHaDialog instanceId="instance-b" brokerName="broker-b" onClose={vi.fn()} />);
    await screen.findByText('9007199254740993');
    await act(async () => resolve({ ...fixture, nodes: [] }));
    expect(signal.aborted).toBe(true);
    await waitFor(() => expect(screen.getByText('9007199254740993')).toBeVisible());
  });

  it('close invokes the owner and does not issue any mutation', async () => {
    const close = vi.fn();
    render(<BrokerHaDialog instanceId="instance-a" brokerName="broker-a" onClose={close} />);
    await waitFor(() => expect(inspectBrokerHa).toHaveBeenCalledTimes(1));
    fireEvent.click(screen.getAllByRole('button', { name: 'Close' })[1]);
    expect(close).toHaveBeenCalledOnce();
  });
});
