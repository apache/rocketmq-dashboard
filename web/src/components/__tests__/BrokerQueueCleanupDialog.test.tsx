/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
import { act, fireEvent, render, screen } from '@testing-library/react';
import { ConfigProvider } from 'antd';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { BrokerQueueCleanupDialog } from '../BrokerQueueCleanupDialog';
import {
  applyBrokerQueueCleanup,
  previewBrokerQueueCleanup,
  type QueueCleanupPreview,
} from '../../api/brokerQueueCleanup';

vi.mock('../../api/brokerQueueCleanup', () => ({
  applyBrokerQueueCleanup: vi.fn(),
  previewBrokerQueueCleanup: vi.fn(),
}));
const target = { instanceId: 'instance-a', brokerName: 'broker-a', address: 'master:10911' };
const sample: QueueCleanupPreview = {
  brokerName: target.brokerName,
  address: target.address,
  sampledAt: '2026-09-08T00:00:00Z',
  configuredTopics: ['orders', 'payments'],
};
const result = {
  operation: 'UNUSED_TOPIC_QUEUES' as const,
  brokerName: target.brokerName,
  address: target.address,
  status: 'BROKER_REPORTED_COMPLETION' as const,
  finishedAt: '2026-09-08T00:01:00Z',
};
const open = (onClose = vi.fn()) =>
  render(
    <ConfigProvider theme={{ token: { motion: false } }}>
      <BrokerQueueCleanupDialog target={target} onClose={onClose} />
    </ConfigProvider>,
  );
const preview = async () => {
  fireEvent.click(screen.getByRole('button', { name: /Review node scope/ }));
  await screen.findByText('orders');
};
const confirm = () =>
  fireEvent.change(screen.getByLabelText('Node address confirmation'), {
    target: { value: target.address },
  });
const apply = () => fireEvent.click(screen.getByRole('button', { name: /Run cleanup/ }));

describe('Broker queue cleanup', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(previewBrokerQueueCleanup).mockResolvedValue(sample);
    vi.mocked(applyBrokerQueueCleanup).mockResolvedValue(result);
  });

  it('requires scope review and exact node confirmation before sending cleanup', async () => {
    open();
    expect(previewBrokerQueueCleanup).not.toHaveBeenCalled();
    await preview();
    expect(screen.getByText(/not a list of deletion candidates/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Run cleanup/ })).toBeDisabled();
    fireEvent.change(screen.getByLabelText('Node address confirmation'), {
      target: { value: 'other:10911' },
    });
    expect(screen.getByRole('button', { name: /Run cleanup/ })).toBeDisabled();
    confirm();
    apply();
    await screen.findByText('BROKER_REPORTED_COMPLETION');
    expect(applyBrokerQueueCleanup).toHaveBeenCalledWith(
      target,
      'UNUSED_TOPIC_QUEUES',
      ['orders', 'payments'],
      target.address,
      expect.any(AbortSignal),
    );
    expect(
      screen.getByText(/No deleted-file count or reclaimed-byte measurement/),
    ).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Run cleanup/ })).toBeDisabled();
  });

  it('changing cleanup type consumes both scope review and confirmation', async () => {
    open();
    await preview();
    confirm();
    fireEvent.click(screen.getByRole('radio', { name: 'Remove expired ConsumeQueues' }));
    expect(screen.queryByText('orders')).not.toBeInTheDocument();
    expect(screen.queryByLabelText('Node address confirmation')).not.toBeInTheDocument();
    expect(screen.getByText(/still-configured topics/)).toBeInTheDocument();
    await preview();
    confirm();
    apply();
    await screen.findByText('BROKER_REPORTED_COMPLETION');
    expect(applyBrokerQueueCleanup).toHaveBeenCalledWith(
      target,
      'EXPIRED_CONSUME_QUEUES',
      ['orders', 'payments'],
      target.address,
      expect.any(AbortSignal),
    );
  });

  it('locks close and form changes and prevents duplicate cleanup while pending', async () => {
    let resolve!: (value: typeof result) => void;
    vi.mocked(applyBrokerQueueCleanup).mockImplementation(
      () =>
        new Promise((done) => {
          resolve = done;
        }),
    );
    const close = vi.fn();
    open(close);
    await preview();
    confirm();
    apply();
    apply();
    expect(applyBrokerQueueCleanup).toHaveBeenCalledTimes(1);
    expect(screen.getByRole('radio', { name: 'Remove expired ConsumeQueues' })).toBeDisabled();
    fireEvent.click(screen.getByRole('button', { name: 'Close' }));
    expect(close).not.toHaveBeenCalled();
    await act(async () => resolve(result));
  });

  it('does not claim rollback or zero deletions after a lost response', async () => {
    vi.mocked(applyBrokerQueueCleanup).mockRejectedValue(new Error('timeout'));
    open();
    await preview();
    confirm();
    apply();
    await screen.findByText(/some files may already have been removed/);
    expect(screen.getByRole('button', { name: /Run cleanup/ })).toBeDisabled();
    expect(screen.queryByLabelText('Node address confirmation')).not.toBeInTheDocument();
  });

  it('shows an uncertain broker response and requires manual verification', async () => {
    vi.mocked(applyBrokerQueueCleanup).mockResolvedValue({ ...result, status: 'UNKNOWN' });
    open();
    await preview();
    confirm();
    apply();
    await screen.findByText('UNKNOWN');
    expect(screen.getByText(/Cleanup may be partially complete/)).toBeInTheDocument();
  });

  it('aborts a pending preview on unmount and ignores late results', async () => {
    let resolve!: (value: QueueCleanupPreview) => void;
    vi.mocked(previewBrokerQueueCleanup).mockImplementation(
      () =>
        new Promise((done) => {
          resolve = done;
        }),
    );
    const view = open();
    fireEvent.click(screen.getByRole('button', { name: /Review node scope/ }));
    const signal = vi.mocked(previewBrokerQueueCleanup).mock.calls[0][1];
    view.unmount();
    expect(signal?.aborted).toBe(true);
    await act(async () => resolve(sample));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });
});
