/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
import { act, fireEvent, render, screen } from '@testing-library/react';
import { ConfigProvider } from 'antd';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ConsumerTimeSpanDialog } from '../ConsumerTimeSpanDialog';
import {
  inspectConsumerTimeSpan,
  type ConsumerQueueTimeSpan,
  type ConsumerTimeSpanSnapshot,
} from '../../api/consumerTimeSpan';

vi.mock('../../api/consumerTimeSpan', () => ({ inspectConsumerTimeSpan: vi.fn() }));
const queue: ConsumerQueueTimeSpan = {
  brokerName: 'broker-a',
  queueId: 2,
  minOffset: '10',
  maxOffset: '9007199254740995',
  consumerOffset: '9007199254740993',
  earliestTime: '1700000000000',
  latestTime: '1700000100000',
  cursorTime: '1700000042000',
  cursorState: 'RECORDED_OFFSET_REFERENCE',
  spanAvailable: true,
};
const sample: ConsumerTimeSpanSnapshot = {
  topic: 'orders',
  group: 'group-a',
  sampledAt: '2026-09-08T00:00:00Z',
  queues: [queue],
};
const open = (onClose = vi.fn()) =>
  render(
    <ConfigProvider theme={{ token: { motion: false } }}>
      <ConsumerTimeSpanDialog
        instanceId="instance-a"
        topic="orders"
        group="group-a"
        onClose={onClose}
      />
    </ConfigProvider>,
  );

describe('Consumer time spans', () => {
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
    vi.mocked(inspectConsumerTimeSpan).mockResolvedValue(sample);
  });

  it('reads only on demand and renders exact offsets and UTC reference times', async () => {
    open();
    expect(inspectConsumerTimeSpan).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: 'Read time spans' }));
    expect(await screen.findByText('9007199254740993')).toBeInTheDocument();
    expect(screen.getByText('[10, 9007199254740995)')).toBeInTheDocument();
    expect(screen.getByText('Recorded offset reference')).toBeInTheDocument();
    expect(
      screen.getByRole('img', { name: /Retained time range:.*offset reference:/ }),
    ).toBeInTheDocument();
    expect(inspectConsumerTimeSpan).toHaveBeenCalledWith(
      {
        instanceId: 'instance-a',
        topic: 'orders',
        group: 'group-a',
      },
      expect.any(AbortSignal),
    );
  });

  it('does not plot fallback timestamps as recorded consumption', async () => {
    vi.mocked(inspectConsumerTimeSpan).mockResolvedValue({
      ...sample,
      queues: [
        {
          ...queue,
          consumerOffset: '0',
          cursorTime: queue.earliestTime,
          cursorState: 'EARLIEST_MESSAGE_FALLBACK',
        },
      ],
    });
    open();
    fireEvent.click(screen.getByRole('button', { name: 'Read time spans' }));
    expect(await screen.findByText('Earliest message fallback')).toBeInTheDocument();
    expect(screen.getByRole('img', { name: /Retained time range:/ })).not.toHaveAccessibleName(
      /offset reference:/,
    );
  });

  it('shows missing span and offset metadata explicitly', async () => {
    vi.mocked(inspectConsumerTimeSpan).mockResolvedValue({
      ...sample,
      queues: [
        {
          ...queue,
          consumerOffset: null,
          earliestTime: null,
          latestTime: null,
          cursorTime: null,
          spanAvailable: false,
          cursorState: 'OFFSET_UNAVAILABLE',
        },
      ],
    });
    open();
    fireEvent.click(screen.getByRole('button', { name: 'Read time spans' }));
    expect(
      await screen.findByText('Broker returned no time span for this queue'),
    ).toBeInTheDocument();
    expect(screen.getByText('Consumer offset unavailable')).toBeInTheDocument();
    expect(screen.queryByRole('img', { name: /Retained time range:/ })).not.toBeInTheDocument();
  });

  it('does not plot a reference outside the sampled timestamp range', async () => {
    vi.mocked(inspectConsumerTimeSpan).mockResolvedValue({
      ...sample,
      queues: [
        {
          ...queue,
          cursorTime: '1700001000000',
        },
      ],
    });
    open();
    fireEvent.click(screen.getByRole('button', { name: 'Read time spans' }));
    await screen.findByText('Recorded offset reference');
    expect(screen.getByRole('img', { name: /Retained time range:/ })).not.toHaveAccessibleName(
      /offset reference:/,
    );
  });

  it('handles equal timestamps and values beyond the browser date range', async () => {
    vi.mocked(inspectConsumerTimeSpan).mockResolvedValue({
      ...sample,
      queues: [
        { ...queue, earliestTime: queue.cursorTime, latestTime: queue.cursorTime },
        {
          ...queue,
          queueId: 3,
          earliestTime: '9223372036854775807',
          latestTime: null,
          cursorTime: null,
          cursorState: 'TIMESTAMP_UNAVAILABLE',
        },
      ],
    });
    open();
    fireEvent.click(screen.getByRole('button', { name: 'Read time spans' }));
    expect(
      await screen.findByText('Outside displayable date range (9223372036854775807)'),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('img', { name: /Retained time range:.*offset reference:/ }),
    ).toBeInTheDocument();
  });

  it('reports errors and clears the old sample before refreshing', async () => {
    open();
    fireEvent.click(screen.getByRole('button', { name: 'Read time spans' }));
    await screen.findByText('9007199254740993');
    vi.mocked(inspectConsumerTimeSpan).mockRejectedValueOnce(new Error('Broker unavailable'));
    fireEvent.click(screen.getByRole('button', { name: 'Read time spans' }));
    expect(screen.queryByText('9007199254740993')).not.toBeInTheDocument();
    expect(await screen.findByText('Broker unavailable')).toBeInTheDocument();
  });

  it('bounds concurrent requests and ignores late completion after unmount', async () => {
    let resolve!: (value: ConsumerTimeSpanSnapshot) => void;
    vi.mocked(inspectConsumerTimeSpan).mockReturnValue(
      new Promise((done) => {
        resolve = done;
      }),
    );
    const view = open();
    fireEvent.click(screen.getByRole('button', { name: 'Read time spans' }));
    fireEvent.click(screen.getByRole('button', { name: /Read time spans/ }));
    expect(inspectConsumerTimeSpan).toHaveBeenCalledTimes(1);
    const signal = vi.mocked(inspectConsumerTimeSpan).mock.calls[0][1];
    view.unmount();
    expect(signal?.aborted).toBe(true);
    await act(async () => resolve(sample));
    expect(screen.queryByText('9007199254740993')).not.toBeInTheDocument();
  });

  it('shows empty metadata without a healthy or caught-up claim and closes', async () => {
    vi.mocked(inspectConsumerTimeSpan).mockResolvedValue({ ...sample, queues: [] });
    const close = vi.fn();
    open(close);
    fireEvent.click(screen.getByRole('button', { name: 'Read time spans' }));
    expect(await screen.findByText('No queues returned in the topic metadata')).toBeInTheDocument();
    const buttons = screen.getAllByRole('button', { name: 'Close' });
    fireEvent.click(buttons[buttons.length - 1]);
    expect(close).toHaveBeenCalledTimes(1);
  });
});
