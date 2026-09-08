/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { ConfigProvider } from 'antd';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ConsumeQueueDialog } from '../ConsumeQueueDialog';
import { inspectConsumeQueue, type ConsumeQueueSnapshot } from '../../api/consumeQueue';

vi.mock('../../api/consumeQueue', () => ({ inspectConsumeQueue: vi.fn() }));
const sample: ConsumeQueueSnapshot = {
  brokerAddress: 'master-a:10911',
  minIndex: '0',
  maxIndex: '9007199254740999',
  requestedIndex: '9007199254740993',
  count: 16,
  atEnd: false,
  expressionType: 'SQL92',
  expression: 'amount > 10',
  filterData: 'subscription metadata',
  entries: [
    {
      ordinal: 1,
      physicalOffset: '9007199254740993',
      physicalSize: 128,
      tagsCode: '-9007199254740993',
      extension: null,
      bitmap: null,
      indexMatch: null,
      message: 'Extension missing',
    },
    {
      ordinal: 2,
      physicalOffset: '9007199254740995',
      physicalSize: 64,
      tagsCode: '10',
      extension: '{}',
      bitmap: '1010',
      indexMatch: true,
      message: null,
    },
    {
      ordinal: 3,
      physicalOffset: '9007199254740997',
      physicalSize: 64,
      tagsCode: '11',
      extension: '{}',
      bitmap: '0010',
      indexMatch: false,
      message: null,
    },
  ],
};
const open = (onClose = vi.fn()) =>
  render(
    <ConfigProvider theme={{ token: { motion: false } }}>
      <ConsumeQueueDialog
        instanceId="instance-a"
        target={{ topic: 'orders', brokerName: 'broker-a', queueId: 2 }}
        onClose={onClose}
      />
    </ConfigProvider>,
  );

describe('ConsumeQueue inspection', () => {
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
    vi.mocked(inspectConsumeQueue).mockResolvedValue(sample);
  });

  it('preserves large decimal input and three distinct filter states', async () => {
    open();
    fireEvent.change(screen.getByLabelText('Start index'), {
      target: { value: '9007199254740993' },
    });
    fireEvent.change(screen.getByLabelText('Consumer group'), { target: { value: ' group-a ' } });
    fireEvent.click(screen.getByRole('button', { name: 'Read indices' }));
    expect(await screen.findByText('Not evaluated')).toBeInTheDocument();
    expect(screen.getByText('Passed')).toBeInTheDocument();
    expect(screen.getByText('Rejected')).toBeInTheDocument();
    expect(screen.getByText('-9007199254740993')).toBeInTheDocument();
    expect(inspectConsumeQueue).toHaveBeenCalledWith(
      {
        instanceId: 'instance-a',
        topic: 'orders',
        brokerName: 'broker-a',
        queueId: 2,
        index: '9007199254740993',
        count: 16,
        consumerGroup: 'group-a',
      },
      expect.any(AbortSignal),
    );
    fireEvent.click(screen.getAllByRole('button', { name: 'Expand row' })[0]);
    expect(screen.getByText('Broker message: Extension missing')).toBeInTheDocument();
    expect(screen.getByText('Extension: Unavailable')).toBeInTheDocument();
  });

  it.each(['-1', '1e3', '9223372036854775808', ''])('rejects invalid index %s', async (index) => {
    open();
    fireEvent.change(screen.getByLabelText('Start index'), { target: { value: index } });
    fireEvent.click(screen.getByRole('button', { name: 'Read indices' }));
    expect(
      await screen.findByText('Index must be a nonnegative signed 64-bit decimal string'),
    ).toBeInTheDocument();
    expect(inspectConsumeQueue).not.toHaveBeenCalled();
  });

  it('clears stale results whenever the request changes', async () => {
    open();
    fireEvent.click(screen.getByRole('button', { name: 'Read indices' }));
    await screen.findByText('Not evaluated');
    fireEvent.change(screen.getByLabelText('Consumer group'), { target: { value: 'new-group' } });
    expect(screen.queryByText('Not evaluated')).not.toBeInTheDocument();
  });

  it('reports sampled end and unavailable subscriptions without a match claim', async () => {
    vi.mocked(inspectConsumeQueue).mockResolvedValue({
      ...sample,
      atEnd: true,
      expression: null,
      expressionType: null,
      filterData: null,
      entries: [],
    });
    open();
    fireEvent.click(screen.getByRole('button', { name: 'Read indices' }));
    expect(await screen.findByText('Index is at the sampled queue end')).toBeInTheDocument();
    expect(screen.getByText('Unavailable: Unavailable')).toBeInTheDocument();
    expect(screen.queryByText('Passed')).not.toBeInTheDocument();
  });

  it('reports failure and permits a fresh manual request', async () => {
    vi.mocked(inspectConsumeQueue).mockRejectedValueOnce(new Error('Broker offline'));
    open();
    fireEvent.click(screen.getByRole('button', { name: 'Read indices' }));
    expect(await screen.findByText('Broker offline')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Read indices' }));
    await screen.findByText('Not evaluated');
    expect(screen.queryByText('Broker offline')).not.toBeInTheDocument();
  });

  it('locks concurrent reads and aborts on unmount even if the request ignores cancellation', async () => {
    let resolve!: (value: ConsumeQueueSnapshot) => void;
    vi.mocked(inspectConsumeQueue).mockReturnValue(
      new Promise((done) => {
        resolve = done;
      }),
    );
    const view = open();
    const read = screen.getByRole('button', { name: 'Read indices' });
    fireEvent.click(read);
    fireEvent.click(read);
    expect(inspectConsumeQueue).toHaveBeenCalledTimes(1);
    expect(screen.getByLabelText('Start index')).toBeDisabled();
    const signal = vi.mocked(inspectConsumeQueue).mock.calls[0][1];
    view.unmount();
    expect(signal?.aborted).toBe(true);
    await act(async () => resolve(sample));
    expect(screen.queryByText('Not evaluated')).not.toBeInTheDocument();
  });

  it('closes through the explicit footer control', async () => {
    const close = vi.fn();
    open(close);
    const buttons = screen.getAllByRole('button', { name: 'Close' });
    fireEvent.click(buttons[buttons.length - 1]);
    await waitFor(() => expect(close).toHaveBeenCalledTimes(1));
  });
});
