/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
import { act, fireEvent, render, screen } from '@testing-library/react';
import { ConfigProvider } from 'antd';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import StaticTopicMappingDialog from '../StaticTopicMappingDialog';
import {
  inspectStaticTopicMapping,
  type StaticMappingSnapshot,
} from '../../api/staticTopicMapping';

vi.mock('../../api/staticTopicMapping', () => ({ inspectStaticTopicMapping: vi.fn() }));
const target = { instanceId: 'instance-a', topic: 'orders' };
const sample: StaticMappingSnapshot = {
  topic: 'orders',
  startedAt: '2026-09-08T00:00:00Z',
  finishedAt: '2026-09-08T00:00:01Z',
  partial: false,
  nodes: [
    {
      brokerName: 'broker-a',
      address: 'master:10911',
      status: 'MAPPING',
      error: null,
      advertised: {
        epoch: '9007199254740993',
        scope: '__global__',
        totalQueues: 2,
        currentQueues: [{ logicalQueueId: 0, physicalQueueId: 3 }],
      },
      local: {
        epoch: '9007199254740993',
        scope: '__global__',
        totalQueues: 2,
        dirty: false,
        queues: [
          {
            logicalQueueId: 0,
            lastMappedBroker: 'broker-a',
            segments: [
              {
                generation: 0,
                brokerName: 'old-broker',
                physicalQueueId: 1,
                logicalStart: '0',
                physicalStart: '0',
                physicalEndExclusive: '9007199254740993',
              },
              {
                generation: 1,
                brokerName: 'broker-a',
                physicalQueueId: 3,
                logicalStart: '-1',
                physicalStart: '0',
                physicalEndExclusive: '-1',
              },
            ],
          },
        ],
      },
    },
  ],
};
const open = (onClose = vi.fn()) =>
  render(
    <ConfigProvider theme={{ token: { motion: false } }}>
      <StaticTopicMappingDialog target={target} onClose={onClose} />
    </ConfigProvider>,
  );
const read = () => fireEvent.click(screen.getByRole('button', { name: /Read mappings/ }));
describe('Static topic mapping inspection', () => {
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
    vi.mocked(inspectStaticTopicMapping).mockResolvedValue(sample);
  });
  it('reads only on request and preserves large values and undecided boundaries', async () => {
    open();
    expect(inspectStaticTopicMapping).not.toHaveBeenCalled();
    read();
    await screen.findByText('9007199254740993');
    expect(screen.getByText('-1 (undecided)')).toBeInTheDocument();
    expect(screen.getByText('-1 (open)')).toBeInTheDocument();
    expect(screen.getByText('Logical 0 → physical 3')).toBeInTheDocument();
    expect(inspectStaticTopicMapping).toHaveBeenCalledWith(target, expect.any(AbortSignal));
    expect(screen.queryByText(/metadata differ/)).not.toBeInTheDocument();
  });
  it('warns about route and local epoch differences without declaring either authoritative', async () => {
    vi.mocked(inspectStaticTopicMapping).mockResolvedValue({
      ...sample,
      nodes: [
        {
          ...sample.nodes[0],
          local: { ...sample.nodes[0].local!, epoch: '9007199254740994', dirty: true },
        },
      ],
    });
    open();
    read();
    await screen.findByText('Route and local mapping metadata differ');
    expect(screen.getByText('9007199254740994 / __global__ / 2')).toBeInTheDocument();
    expect(screen.getByText('true')).toBeInTheDocument();
  });
  it('retains successful segments when another broker cannot be read', async () => {
    vi.mocked(inspectStaticTopicMapping).mockResolvedValue({
      ...sample,
      partial: true,
      nodes: [
        ...sample.nodes,
        {
          brokerName: 'broker-b',
          address: null,
          status: 'UNAVAILABLE',
          error: 'No registered master address',
          advertised: null,
          local: null,
        },
      ],
    });
    open();
    read();
    await screen.findByText('Some broker mappings are unavailable');
    expect(screen.getByText('old-broker')).toBeInTheDocument();
    expect(screen.getByText('No registered master address')).toBeInTheDocument();
  });
  it('separates no local mapping from an empty hosted mapping', async () => {
    vi.mocked(inspectStaticTopicMapping).mockResolvedValue({
      ...sample,
      nodes: [
        { ...sample.nodes[0], local: { ...sample.nodes[0].local!, queues: [] } },
        {
          brokerName: 'broker-b',
          address: 'b:10911',
          status: 'NO_MAPPING',
          error: null,
          advertised: null,
          local: null,
        },
      ],
    });
    open();
    read();
    await screen.findByText('No hosted queue history');
    expect(screen.getByText('This broker returned no local static mapping')).toBeInTheDocument();
  });
  it('drops an old sample before a refresh that fails', async () => {
    open();
    read();
    await screen.findByText('old-broker');
    vi.mocked(inspectStaticTopicMapping).mockRejectedValue(new Error('route unavailable'));
    read();
    await screen.findByText('route unavailable');
    expect(screen.queryByText('old-broker')).not.toBeInTheDocument();
  });
  it('prevents overlapping reads and discards late responses after unmount', async () => {
    let resolve!: (value: StaticMappingSnapshot) => void;
    vi.mocked(inspectStaticTopicMapping).mockImplementation(
      () =>
        new Promise((done) => {
          resolve = done;
        }),
    );
    const view = open();
    read();
    read();
    expect(inspectStaticTopicMapping).toHaveBeenCalledTimes(1);
    const signal = vi.mocked(inspectStaticTopicMapping).mock.calls[0][1];
    view.unmount();
    expect(signal?.aborted).toBe(true);
    await act(async () => resolve(sample));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });
});
