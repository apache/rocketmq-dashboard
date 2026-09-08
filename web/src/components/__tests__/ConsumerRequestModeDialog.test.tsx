/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
import { act, fireEvent, render, screen } from '@testing-library/react';
import { ConfigProvider } from 'antd';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ConsumerRequestModeDialog } from '../ConsumerRequestModeDialog';
import {
  applyConsumerRequestMode,
  previewConsumerRequestMode,
  type RequestModePreview,
} from '../../api/consumerRequestMode';

vi.mock('../../api/consumerRequestMode', () => ({
  applyConsumerRequestMode: vi.fn(),
  previewConsumerRequestMode: vi.fn(),
}));
const before = {
  brokerName: 'broker-a',
  address: 'master:10911',
  mode: 'PULL' as const,
  popShareQueueNum: 0,
  explicit: false,
  serverLoadBalancerEnable: 'true',
};
const sample: RequestModePreview = { topic: 'orders', group: 'group-a', brokers: [before] };
const observed = { ...before, mode: 'POP' as const, popShareQueueNum: -1, explicit: true };
const open = (onClose = vi.fn()) =>
  render(
    <ConfigProvider theme={{ token: { motion: false } }}>
      <ConsumerRequestModeDialog
        instanceId="instance-a"
        topic="orders"
        group="group-a"
        onClose={onClose}
      />
    </ConfigProvider>,
  );
const preview = async () => {
  fireEvent.click(screen.getByRole('button', { name: /Preview broker modes/ }));
  await screen.findByText('Broker default');
};
const confirm = () => fireEvent.click(screen.getByRole('checkbox'));
const apply = () => fireEvent.click(screen.getByRole('button', { name: /Apply request mode/ }));

describe('Consumer request mode', () => {
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
    vi.mocked(previewConsumerRequestMode).mockResolvedValue(sample);
    vi.mocked(applyConsumerRequestMode).mockResolvedValue({
      brokers: [{ before, status: 'CONFIRMED', observed }],
    });
  });

  it('requires preview and confirmation and submits the reviewed topic scope', async () => {
    open();
    expect(previewConsumerRequestMode).not.toHaveBeenCalled();
    expect(screen.getByRole('button', { name: /Apply request mode/ })).toBeDisabled();
    await preview();
    expect(previewConsumerRequestMode).toHaveBeenCalledWith(
      { instanceId: 'instance-a', topic: 'orders', group: 'group-a' },
      expect.any(AbortSignal),
    );
    expect(screen.getByRole('button', { name: /Apply request mode/ })).toBeDisabled();
    confirm();
    apply();
    await screen.findByText('CONFIRMED');
    expect(applyConsumerRequestMode).toHaveBeenCalledWith(
      { instanceId: 'instance-a', topic: 'orders', group: 'group-a' },
      'POP',
      -1,
      [before],
      expect.any(AbortSignal),
    );
    expect(screen.getByText('POP / -1')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Apply request mode/ })).toBeDisabled();
  });

  it('invalidates the preview when POP sharing changes', async () => {
    open();
    await preview();
    confirm();
    fireEvent.change(screen.getByLabelText('POP sharing'), { target: { value: '3' } });
    expect(screen.queryByText('Broker default')).not.toBeInTheDocument();
    expect(screen.queryByRole('checkbox')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Apply request mode/ })).toBeDisabled();
  });

  it('sets sharing to zero and invalidates confirmation when switching to PULL', async () => {
    open();
    await preview();
    confirm();
    fireEvent.mouseDown(screen.getByRole('combobox', { name: 'Requested mode' }));
    const option = await screen.findByText('PULL', { selector: '.ant-select-item-option-content' });
    fireEvent.click(option);
    expect(screen.getByLabelText('POP sharing')).toHaveValue('0');
    expect(screen.getByLabelText('POP sharing')).toBeDisabled();
    expect(screen.getByRole('button', { name: /Apply request mode/ })).toBeDisabled();
  });

  it('locks writes and form edits during an apply request', async () => {
    let resolve!: (value: Awaited<ReturnType<typeof applyConsumerRequestMode>>) => void;
    vi.mocked(applyConsumerRequestMode).mockImplementation(
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
    expect(applyConsumerRequestMode).toHaveBeenCalledTimes(1);
    expect(screen.getByLabelText('POP sharing')).toBeDisabled();
    fireEvent.click(screen.getByRole('button', { name: 'Close' }));
    expect(close).not.toHaveBeenCalled();
    await act(async () => resolve({ brokers: [{ before, status: 'CONFIRMED', observed }] }));
  });

  it('never offers the consumed preview after a lost response', async () => {
    vi.mocked(applyConsumerRequestMode).mockRejectedValue(new Error('timeout'));
    open();
    await preview();
    confirm();
    apply();
    await screen.findByText(/Update was not confirmed/);
    expect(screen.queryByRole('checkbox')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Apply request mode/ })).toBeDisabled();
  });

  it('preserves partial outcomes without showing all brokers as failed', async () => {
    vi.mocked(applyConsumerRequestMode).mockResolvedValue({
      brokers: [
        { before, status: 'CONFIRMED', observed },
        { before: { ...before, brokerName: 'broker-b' }, status: 'UNKNOWN', observed: null },
        { before: { ...before, brokerName: 'broker-c' }, status: 'NOT_ATTEMPTED', observed: null },
      ],
    });
    open();
    await preview();
    confirm();
    apply();
    await screen.findByText('CONFIRMED');
    expect(screen.getByText('UNKNOWN')).toBeInTheDocument();
    expect(screen.getByText('NOT_ATTEMPTED')).toBeInTheDocument();
    expect(screen.getByText(/Update stopped after/)).toBeInTheDocument();
  });

  it('cancels a preview on unmount and ignores its late completion', async () => {
    let resolve!: (value: RequestModePreview) => void;
    vi.mocked(previewConsumerRequestMode).mockImplementation(
      () =>
        new Promise((done) => {
          resolve = done;
        }),
    );
    const view = open();
    fireEvent.click(screen.getByRole('button', { name: /Preview broker modes/ }));
    const signal = vi.mocked(previewConsumerRequestMode).mock.calls[0][1];
    view.unmount();
    expect(signal?.aborted).toBe(true);
    await act(async () => resolve(sample));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('keeps changes disabled when the preview is rejected', async () => {
    vi.mocked(previewConsumerRequestMode).mockRejectedValue(new Error('Stop the consumer group'));
    open();
    fireEvent.click(screen.getByRole('button', { name: /Preview broker modes/ }));
    await screen.findByText('Stop the consumer group');
    expect(applyConsumerRequestMode).not.toHaveBeenCalled();
  });
});
