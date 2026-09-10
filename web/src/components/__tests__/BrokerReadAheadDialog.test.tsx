/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
import { act, fireEvent, render, screen } from '@testing-library/react';
import { ConfigProvider } from 'antd';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { BrokerReadAheadDialog } from '../BrokerReadAheadDialog';
import {
  inspectBrokerReadAhead,
  updateBrokerReadAhead,
  type ReadAheadSnapshot,
} from '../../api/brokerReadAhead';

vi.mock('../../api/brokerReadAhead', () => ({
  inspectBrokerReadAhead: vi.fn(),
  updateBrokerReadAhead: vi.fn(),
}));
const target = { instanceId: 'instance-a', brokerName: 'broker-a', address: 'master:10911' };
const before: ReadAheadSnapshot = {
  brokerName: target.brokerName,
  address: target.address,
  enabled: true,
  sampledAt: '2026-09-08T00:00:00Z',
};
const open = (onClose = vi.fn()) =>
  render(
    <ConfigProvider theme={{ token: { motion: false } }}>
      <BrokerReadAheadDialog target={target} onClose={onClose} />
    </ConfigProvider>,
  );
const inspect = async () => {
  fireEvent.click(screen.getByRole('button', { name: /Inspect current mode/ }));
  await screen.findByRole('radio', { name: 'Random access advice' });
};
const choose = () => {
  fireEvent.click(screen.getByRole('radio', { name: 'Random access advice' }));
  fireEvent.click(screen.getByRole('checkbox'));
};
const apply = () => fireEvent.click(screen.getByRole('button', { name: /Apply runtime mode/ }));

describe('Broker read-ahead', () => {
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
    vi.mocked(inspectBrokerReadAhead).mockResolvedValue(before);
    vi.mocked(updateBrokerReadAhead).mockResolvedValue({
      status: 'CONFIG_CONFIRMED',
      acknowledged: true,
      before,
      observed: { ...before, enabled: false },
    });
  });

  it('requires a changed mode and explicit confirmation after inspection', async () => {
    open();
    expect(inspectBrokerReadAhead).not.toHaveBeenCalled();
    await inspect();
    expect(screen.getByRole('button', { name: /Apply runtime mode/ })).toBeDisabled();
    choose();
    apply();
    await screen.findByText('CONFIG_CONFIRMED');
    expect(updateBrokerReadAhead).toHaveBeenCalledWith(
      target,
      true,
      false,
      expect.any(AbortSignal),
    );
    expect(screen.getByText('Received')).toBeInTheDocument();
    expect(
      screen.getByText(/do not prove that every OS advice call succeeded/),
    ).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Apply runtime mode/ })).toBeDisabled();
  });

  it('changing the requested mode clears confirmation', async () => {
    open();
    await inspect();
    choose();
    fireEvent.click(screen.getByRole('radio', { name: 'Normal read-ahead' }));
    expect(screen.getByRole('checkbox')).not.toBeChecked();
    expect(screen.getByRole('button', { name: /Apply runtime mode/ })).toBeDisabled();
  });

  it('locks the dialog during a write and sends only one request', async () => {
    let resolve!: (value: Awaited<ReturnType<typeof updateBrokerReadAhead>>) => void;
    vi.mocked(updateBrokerReadAhead).mockImplementation(
      () =>
        new Promise((done) => {
          resolve = done;
        }),
    );
    const close = vi.fn();
    open(close);
    await inspect();
    choose();
    apply();
    apply();
    expect(updateBrokerReadAhead).toHaveBeenCalledTimes(1);
    fireEvent.click(screen.getByRole('button', { name: 'Close' }));
    expect(close).not.toHaveBeenCalled();
    await act(async () =>
      resolve({
        status: 'CONFIG_CONFIRMED',
        acknowledged: true,
        before,
        observed: { ...before, enabled: false },
      }),
    );
  });

  it('distinguishes an acknowledged but uncertain readback', async () => {
    vi.mocked(updateBrokerReadAhead).mockResolvedValue({
      status: 'UNKNOWN',
      acknowledged: true,
      before,
      observed: null,
    });
    open();
    await inspect();
    choose();
    apply();
    await screen.findByText('UNKNOWN');
    expect(screen.getByText('Received')).toBeInTheDocument();
    expect(screen.getByText('Unavailable')).toBeInTheDocument();
    expect(screen.getByText(/The result is uncertain/)).toBeInTheDocument();
  });

  it('requires a new inspection after the response is lost', async () => {
    vi.mocked(updateBrokerReadAhead).mockRejectedValue(new Error('connection lost'));
    open();
    await inspect();
    choose();
    apply();
    await screen.findByText(/Update was not confirmed/);
    expect(screen.queryByRole('checkbox')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Apply runtime mode/ })).toBeDisabled();
  });

  it('aborts on unmount and discards late inspection results', async () => {
    let resolve!: (value: ReadAheadSnapshot) => void;
    vi.mocked(inspectBrokerReadAhead).mockImplementation(
      () =>
        new Promise((done) => {
          resolve = done;
        }),
    );
    const view = open();
    fireEvent.click(screen.getByRole('button', { name: /Inspect current mode/ }));
    const signal = vi.mocked(inspectBrokerReadAhead).mock.calls[0][1];
    view.unmount();
    expect(signal?.aborted).toBe(true);
    await act(async () => resolve(before));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });
});
