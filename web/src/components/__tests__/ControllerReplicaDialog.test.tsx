/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
import { act, fireEvent, render, screen } from '@testing-library/react';
import { ConfigProvider } from 'antd';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ControllerReplicaDialog } from '../ControllerReplicaDialog';
import {
  inspectControllerReplicas,
  type ControllerReplicaSnapshot,
} from '../../api/controllerReplicas';

vi.mock('../../api/controllerReplicas', () => ({ inspectControllerReplicas: vi.fn() }));
const sample: ControllerReplicaSnapshot = {
  brokerName: 'broker-a',
  configSource: 'master:10911',
  mode: 'ENABLED',
  sampledAt: '2026-09-08T00:00:00Z',
  agreement: 'MATCHING',
  controllers: [
    {
      address: 'controller-a:9878',
      group: 'controller-group',
      leaderId: 'n0',
      leaderAddress: 'controller-a:9878',
      leader: true,
      peers: 'n0-controller-a:9878',
      error: null,
    },
  ],
  membership: {
    discoveryAddress: 'controller-a:9878',
    masterBrokerId: '9007199254740993',
    masterAddress: 'master:10911',
    masterEpoch: 12,
    syncStateSetEpoch: 18,
    replicas: [
      { brokerId: '9007199254740993', address: 'master:10911', inSyncSet: true, alive: true },
      { brokerId: '2', address: 'replica:10911', inSyncSet: true, alive: false },
      { brokerId: '3', address: null, inSyncSet: false, alive: null },
    ],
  },
  membershipError: null,
};
const open = (onClose = vi.fn()) =>
  render(
    <ConfigProvider theme={{ token: { motion: false } }}>
      <ControllerReplicaDialog instanceId="instance-a" brokerName="broker-a" onClose={onClose} />
    </ConfigProvider>,
  );
const read = () => fireEvent.click(screen.getByRole('button', { name: /Read controller state/ }));

describe('Controller replica diagnostics', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    Object.defineProperty(window, 'matchMedia', {
      writable: true,
      value: vi.fn().mockImplementation(() => ({
        matches: false,
        addListener: vi.fn(),
        removeListener: vi.fn(),
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
      })),
    });
    vi.mocked(inspectControllerReplicas).mockResolvedValue(sample);
  });

  it('queries manually and keeps liveness separate from sync-set membership', async () => {
    open();
    expect(inspectControllerReplicas).not.toHaveBeenCalled();
    read();
    await screen.findByText('9007199254740993');
    expect(inspectControllerReplicas).toHaveBeenCalledWith(
      'instance-a',
      'broker-a',
      expect.any(AbortSignal),
    );
    expect(screen.getAllByText('In set')).toHaveLength(2);
    expect(screen.getByText('Not alive')).toBeInTheDocument();
    expect(screen.getByText('Unknown')).toBeInTheDocument();
    expect(screen.getByText('Outside set')).toBeInTheDocument();
    expect(screen.getByText('MATCHING')).toBeInTheDocument();
    expect(screen.getByText(/Matching metadata does not prove/)).toBeInTheDocument();
  });

  it('shows a disabled mode without inventing membership', async () => {
    vi.mocked(inspectControllerReplicas).mockResolvedValue({
      ...sample,
      mode: 'DISABLED',
      agreement: 'UNAVAILABLE',
      controllers: [],
      membership: null,
      membershipError: 'Controller mode is disabled',
    });
    open();
    read();
    await screen.findByText('DISABLED');
    expect(screen.getByText('Controller mode is disabled')).toBeInTheDocument();
    expect(screen.queryByText('Broker replica membership')).not.toBeInTheDocument();
  });

  it('preserves metadata while showing a membership failure', async () => {
    vi.mocked(inspectControllerReplicas).mockResolvedValue({
      ...sample,
      membership: null,
      membershipError: 'Broker response code: 1',
    });
    open();
    read();
    await screen.findByText('Broker response code: 1');
    expect(screen.getByText('controller-group')).toBeInTheDocument();
    expect(screen.getByText('Reports leader')).toBeInTheDocument();
  });

  it('displays divergent and failed controller observations explicitly', async () => {
    vi.mocked(inspectControllerReplicas).mockResolvedValue({
      ...sample,
      agreement: 'DIVERGENT',
      controllers: [
        ...sample.controllers,
        {
          address: 'controller-b:9878',
          group: null,
          leaderId: null,
          leaderAddress: null,
          leader: null,
          peers: null,
          error: 'Controller request failed',
        },
      ],
    });
    open();
    read();
    await screen.findByText('DIVERGENT');
    expect(screen.getByText('Controller request failed')).toBeInTheDocument();
    expect(screen.getByText('Unknown role')).toBeInTheDocument();
  });

  it('prevents concurrent reads and discards a response after closing', async () => {
    let resolve!: (value: ControllerReplicaSnapshot) => void;
    vi.mocked(inspectControllerReplicas).mockImplementation(
      () =>
        new Promise((done) => {
          resolve = done;
        }),
    );
    const view = open();
    read();
    read();
    expect(inspectControllerReplicas).toHaveBeenCalledTimes(1);
    const signal = vi.mocked(inspectControllerReplicas).mock.calls[0][2];
    view.unmount();
    expect(signal?.aborted).toBe(true);
    await act(async () => resolve(sample));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('clears stale results before refreshing and reports read failures', async () => {
    open();
    read();
    await screen.findByText('MATCHING');
    vi.mocked(inspectControllerReplicas).mockRejectedValue(new Error('Broker is not registered'));
    read();
    await screen.findByText('Broker is not registered');
    expect(screen.queryByText('MATCHING')).not.toBeInTheDocument();
  });
});
