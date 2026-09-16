/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
import { act, fireEvent, render, screen } from '@testing-library/react';
import { ConfigProvider } from 'antd';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import BrokerRocksdbCheckDialog from '../BrokerRocksdbCheckDialog';
import {
  previewRocksdbCheck,
  submitRocksdbCheck,
  type RocksdbCheckPreview,
} from '../../api/brokerRocksdbCheck';

vi.mock('../../api/brokerRocksdbCheck', () => ({
  previewRocksdbCheck: vi.fn(),
  submitRocksdbCheck: vi.fn(),
}));
const target = { instanceId: 'instance-a', brokerName: 'broker-a', address: 'master:10911' };
const sample: RocksdbCheckPreview = {
  brokerName: target.brokerName,
  address: target.address,
  sampledAt: '2026-09-08T00:00:00Z',
  eligible: true,
  topics: ['orders'],
  settings: { doubleWriteEnabled: true, loadingStores: ['default', 'defaultRocksDB'] },
};
const result = {
  brokerName: target.brokerName,
  address: target.address,
  topic: 'orders',
  checkFromMillis: '1000',
  status: 'ACCEPTED' as const,
  brokerStatus: 2,
  brokerRemark: null,
  submittedAt: '2026-09-08T00:00:00Z',
  receivedAt: '2026-09-08T00:00:01Z',
};
const open = (onClose = vi.fn()) =>
  render(
    <ConfigProvider theme={{ token: { motion: false } }}>
      <BrokerRocksdbCheckDialog target={target} onClose={onClose} />
    </ConfigProvider>,
  );
async function review() {
  fireEvent.click(screen.getByRole('button', { name: /Read storage scope/ }));
  await screen.findByText('Double write');
  fireEvent.mouseDown(screen.getByRole('combobox'));
  fireEvent.click(document.querySelector('.ant-select-item-option-content')!);
  fireEvent.change(screen.getByLabelText('Check from store time (epoch milliseconds)'), {
    target: { value: '1000' },
  });
}
const confirm = () => fireEvent.click(screen.getByRole('checkbox'));
const submit = () => fireEvent.click(screen.getByRole('button', { name: /Start check/ }));

describe('RocksDB queue check', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    Object.defineProperty(window, 'matchMedia', {
      writable: true,
      value: vi.fn().mockImplementation((query) => ({
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
    vi.mocked(previewRocksdbCheck).mockResolvedValue(sample);
    vi.mocked(submitRocksdbCheck).mockResolvedValue(result);
  });
  it('requires explicit review and returns an accepted receipt without claiming a match', async () => {
    open();
    expect(previewRocksdbCheck).not.toHaveBeenCalled();
    expect(screen.getByRole('button', { name: /Start check/ })).toBeDisabled();
    await review();
    confirm();
    submit();
    await screen.findByText('ACCEPTED');
    expect(submitRocksdbCheck).toHaveBeenCalledWith(
      target,
      'orders',
      '1000',
      sample.settings,
      expect.any(AbortSignal),
    );
    expect(screen.getByText(/not a verified consistency result/)).toBeInTheDocument();
    expect(screen.getByText('checkRocksdbCqWriteProgress result:')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Start check/ })).toBeDisabled();
  });
  it('clears confirmation when the checkpoint changes and rejects invalid time', async () => {
    open();
    await review();
    confirm();
    fireEvent.change(screen.getByLabelText('Check from store time (epoch milliseconds)'), {
      target: { value: '9223372036854775807' },
    });
    expect(screen.getByRole('checkbox')).not.toBeChecked();
    expect(screen.getByRole('button', { name: /Start check/ })).toBeDisabled();
    expect(screen.getByText('Enter positive epoch milliseconds in the past.')).toBeInTheDocument();
  });
  it('blocks unsupported storage instead of interpreting a no-op check as healthy', async () => {
    vi.mocked(previewRocksdbCheck).mockResolvedValue({ ...sample, eligible: false });
    open();
    fireEvent.click(screen.getByRole('button', { name: /Read storage scope/ }));
    await screen.findByText(/Requires double write/);
    expect(screen.getByRole('button', { name: /Start check/ })).toBeDisabled();
    expect(submitRocksdbCheck).not.toHaveBeenCalled();
  });
  it('locks submission and close while the command is pending', async () => {
    let resolve!: (value: typeof result) => void;
    vi.mocked(submitRocksdbCheck).mockImplementation(
      () =>
        new Promise((done) => {
          resolve = done;
        }),
    );
    const close = vi.fn();
    open(close);
    await review();
    confirm();
    submit();
    submit();
    expect(submitRocksdbCheck).toHaveBeenCalledTimes(1);
    fireEvent.click(screen.getByRole('button', { name: 'Close' }));
    expect(close).not.toHaveBeenCalled();
    await act(async () => resolve(result));
  });
  it('keeps a lost response uncertain and consumes the review to prevent immediate retry', async () => {
    vi.mocked(submitRocksdbCheck).mockRejectedValue(new Error('timeout'));
    open();
    await review();
    confirm();
    submit();
    await screen.findByText(/Submission outcome is unknown/);
    expect(screen.getByRole('button', { name: /Start check/ })).toBeDisabled();
    expect(submitRocksdbCheck).toHaveBeenCalledTimes(1);
  });
  it('aborts pending reads when leaving the selected instance', async () => {
    let resolve!: (value: RocksdbCheckPreview) => void;
    vi.mocked(previewRocksdbCheck).mockImplementation(
      () =>
        new Promise((done) => {
          resolve = done;
        }),
    );
    const view = open();
    fireEvent.click(screen.getByRole('button', { name: /Read storage scope/ }));
    const signal = vi.mocked(previewRocksdbCheck).mock.calls[0][1];
    view.unmount();
    expect(signal?.aborted).toBe(true);
    await act(async () => resolve(sample));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });
});
