/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
import { act, fireEvent, render, screen } from '@testing-library/react';
import { ConfigProvider } from 'antd';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ConsumerOffsetCopyDialog } from '../ConsumerOffsetCopyDialog';
import {
  applyConsumerOffsetCopy,
  previewConsumerOffsetCopy,
  type CopyPreview,
} from '../../api/consumerOffsetCopy';

vi.mock('../../api/consumerOffsetCopy', () => ({
  applyConsumerOffsetCopy: vi.fn(),
  previewConsumerOffsetCopy: vi.fn(),
}));
const expected = {
  brokerName: 'broker-a',
  brokerAddr: 'master:10911',
  queueId: 0,
  sourceOffset: '9007199254740993',
  targetOffset: '20',
};
const sample: CopyPreview = {
  topic: 'orders',
  sourceGroup: 'source',
  targetGroup: 'target',
  queues: [{ expected, minOffset: '10', maxOffset: '9223372036854775807' }],
};
const selection = {
  instanceId: 'instance-a',
  topic: 'orders',
  sourceGroup: 'source',
  targetGroup: 'target',
};
const open = (onClose = vi.fn()) =>
  render(
    <ConfigProvider theme={{ token: { motion: false } }}>
      <ConsumerOffsetCopyDialog
        instanceId="instance-a"
        topic="orders"
        sourceGroup="source"
        onClose={onClose}
      />
    </ConfigProvider>,
  );
const preview = async () => {
  fireEvent.change(screen.getByLabelText('Existing target group'), { target: { value: 'target' } });
  fireEvent.click(screen.getByRole('button', { name: 'Preview offsets' }));
  await screen.findByText('9007199254740993');
};
const confirm = () => fireEvent.click(screen.getByRole('checkbox'));
const copy = () => fireEvent.click(screen.getByRole('button', { name: /Copy reviewed offsets/ }));

describe('Consumer offset copy', () => {
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
    vi.mocked(previewConsumerOffsetCopy).mockResolvedValue(sample);
    vi.mocked(applyConsumerOffsetCopy).mockResolvedValue({
      queues: [{ queue: expected, status: 'CONFIRMED', observedOffset: expected.sourceOffset }],
    });
  });

  it('requires a manual preview and confirmation before sending exact string offsets', async () => {
    open();
    expect(previewConsumerOffsetCopy).not.toHaveBeenCalled();
    expect(screen.getByRole('button', { name: 'Copy reviewed offsets' })).toBeDisabled();
    await preview();
    expect(previewConsumerOffsetCopy).toHaveBeenCalledWith(selection, expect.any(AbortSignal));
    expect(screen.getByRole('button', { name: 'Copy reviewed offsets' })).toBeDisabled();
    confirm();
    copy();
    await screen.findByText('CONFIRMED');
    expect(applyConsumerOffsetCopy).toHaveBeenCalledWith(
      selection,
      [expected],
      expect.any(AbortSignal),
    );
    expect(screen.getByRole('button', { name: 'Copy reviewed offsets' })).toBeDisabled();
  });

  it('invalidates the preview when the target changes', async () => {
    open();
    await preview();
    confirm();
    fireEvent.change(screen.getByLabelText('Existing target group'), {
      target: { value: 'another' },
    });
    expect(screen.queryByText('9007199254740993')).not.toBeInTheDocument();
    expect(screen.queryByRole('checkbox')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Copy reviewed offsets' })).toBeDisabled();
  });

  it('shows absent target offsets without presenting them as zero', async () => {
    vi.mocked(previewConsumerOffsetCopy).mockResolvedValue({
      ...sample,
      queues: [{ ...sample.queues[0], expected: { ...expected, targetOffset: null } }],
    });
    open();
    await preview();
    expect(screen.getByText('Not stored')).toBeInTheDocument();
  });

  it('locks edits and close and sends only one request while applying', async () => {
    let resolve!: (value: Awaited<ReturnType<typeof applyConsumerOffsetCopy>>) => void;
    vi.mocked(applyConsumerOffsetCopy).mockImplementation(
      () =>
        new Promise((done) => {
          resolve = done;
        }),
    );
    const close = vi.fn();
    open(close);
    await preview();
    confirm();
    copy();
    copy();
    expect(applyConsumerOffsetCopy).toHaveBeenCalledTimes(1);
    expect(screen.getByLabelText('Existing target group')).toBeDisabled();
    fireEvent.click(screen.getByRole('button', { name: 'Close' }));
    expect(close).not.toHaveBeenCalled();
    await act(async () =>
      resolve({
        queues: [{ queue: expected, status: 'CONFIRMED', observedOffset: expected.sourceOffset }],
      }),
    );
    expect(screen.getByText('CONFIRMED')).toBeInTheDocument();
  });

  it('consumes the preview after a lost response and explains recovery', async () => {
    vi.mocked(applyConsumerOffsetCopy).mockRejectedValue(new Error('network error'));
    open();
    await preview();
    confirm();
    copy();
    await screen.findByText(/Copy was not confirmed/);
    expect(screen.getByRole('button', { name: 'Copy reviewed offsets' })).toBeDisabled();
    expect(applyConsumerOffsetCopy).toHaveBeenCalledTimes(1);
  });

  it('shows uncertain and unattempted outcomes separately', async () => {
    vi.mocked(applyConsumerOffsetCopy).mockResolvedValue({
      queues: [
        { queue: expected, status: 'UNKNOWN', observedOffset: '21' },
        { queue: { ...expected, queueId: 1 }, status: 'NOT_ATTEMPTED', observedOffset: null },
      ],
    });
    open();
    await preview();
    confirm();
    copy();
    await screen.findByText('UNKNOWN');
    expect(screen.getByText('NOT_ATTEMPTED')).toBeInTheDocument();
    expect(screen.getByText('21')).toBeInTheDocument();
    expect(screen.getByText(/Copy stopped after/)).toBeInTheDocument();
  });

  it('aborts the request on unmount and ignores a late preview', async () => {
    let resolve!: (value: CopyPreview) => void;
    vi.mocked(previewConsumerOffsetCopy).mockImplementation(
      () =>
        new Promise((done) => {
          resolve = done;
        }),
    );
    const view = open();
    fireEvent.change(screen.getByLabelText('Existing target group'), {
      target: { value: 'target' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Preview offsets' }));
    const signal = vi.mocked(previewConsumerOffsetCopy).mock.calls[0][1];
    view.unmount();
    expect(signal?.aborted).toBe(true);
    await act(async () => resolve(sample));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('surfaces preview failure and keeps copying disabled', async () => {
    vi.mocked(previewConsumerOffsetCopy).mockRejectedValue(new Error('Stop both consumer groups'));
    open();
    fireEvent.change(screen.getByLabelText('Existing target group'), {
      target: { value: 'target' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Preview offsets' }));
    await screen.findByText('Stop both consumer groups');
    expect(applyConsumerOffsetCopy).not.toHaveBeenCalled();
  });
});
