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
import { ConfigProvider } from 'antd';
import type { ReactElement } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import QueueOffsetDialog from '../QueueOffsetDialog';
import {
  applyQueueOffset,
  previewQueueOffset,
  type QueueOffsetPreview,
} from '../../api/queueOffset';

vi.mock('../../api/queueOffset', () => ({
  applyQueueOffset: vi.fn(),
  previewQueueOffset: vi.fn(),
}));
const target = {
  instanceId: 'instance-a',
  group: 'cg-orders',
  topic: 'orders',
  brokerName: 'broker-a',
  queueId: 1,
};
const result: QueueOffsetPreview = {
  brokerAddress: 'master:10911',
  currentOffset: '40',
  targetOffset: '20',
  minOffset: '10',
  maxOffset: '100',
  offsetDelta: '-20',
  projectedLag: '80',
};
const render = (element: ReactElement) =>
  renderComponent(element, {
    wrapper: ({ children }) => (
      <ConfigProvider theme={{ token: { motion: false } }}>{children}</ConfigProvider>
    ),
  });

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(previewQueueOffset).mockResolvedValue(result);
  vi.mocked(applyQueueOffset).mockResolvedValue(result);
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
});

async function preview() {
  fireEvent.change(screen.getByRole('textbox', { name: 'Target offset' }), {
    target: { value: '20' },
  });
  fireEvent.click(screen.getByRole('button', { name: 'Preview change' }));
  await waitFor(() => expect(screen.getByText('Replay 20 queue positions')).toBeVisible());
}

describe('QueueOffsetDialog', () => {
  it('previews one queue and applies the exact target with the observed current offset', async () => {
    const applied = vi.fn();
    render(<QueueOffsetDialog target={target} onClose={vi.fn()} onApplied={applied} />);
    expect(screen.getByRole('button', { name: 'Confirm queue offset' })).toBeDisabled();
    await preview();
    expect(previewQueueOffset).toHaveBeenCalledWith(target, '20');
    expect(applyQueueOffset).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: 'Confirm queue offset' }));
    await waitFor(() =>
      expect(screen.getByText('Broker accepted the queue offset update')).toBeVisible(),
    );
    expect(applyQueueOffset).toHaveBeenCalledWith(target, '20', '40');
    expect(applied).toHaveBeenCalledOnce();
    expect(screen.getByRole('button', { name: 'Confirm queue offset' })).toBeDisabled();
  });

  it('invalidates preview when the requested offset changes and rejects non-integer text', async () => {
    render(<QueueOffsetDialog target={target} onClose={vi.fn()} onApplied={vi.fn()} />);
    await preview();
    fireEvent.change(screen.getByRole('textbox', { name: 'Target offset' }), {
      target: { value: '30' },
    });
    expect(screen.queryByText('Replay 20 queue positions')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Confirm queue offset' })).toBeDisabled();
    fireEvent.change(screen.getByRole('textbox', { name: 'Target offset' }), {
      target: { value: '2.5' },
    });
    expect(screen.getByRole('button', { name: 'Preview change' })).toBeDisabled();
  });

  it('shows a changed-offset conflict and requires a new preview', async () => {
    vi.mocked(applyQueueOffset).mockRejectedValue(
      new Error('Consumer offset changed; inspect the queue again'),
    );
    render(<QueueOffsetDialog target={target} onClose={vi.fn()} onApplied={vi.fn()} />);
    await preview();
    fireEvent.click(screen.getByRole('button', { name: 'Confirm queue offset' }));
    await waitFor(() => expect(screen.getByText(/Consumer offset changed/)).toBeVisible());
    expect(screen.getByRole('button', { name: 'Confirm queue offset' })).toBeDisabled();
  });

  it('prevents duplicate submissions and locks close while the write is pending', async () => {
    let finish!: (value: QueueOffsetPreview) => void;
    vi.mocked(applyQueueOffset).mockReturnValue(
      new Promise((resolve) => {
        finish = resolve;
      }),
    );
    render(<QueueOffsetDialog target={target} onClose={vi.fn()} onApplied={vi.fn()} />);
    await preview();
    const button = screen.getByRole('button', { name: 'Confirm queue offset' });
    fireEvent.click(button);
    fireEvent.click(button);
    expect(applyQueueOffset).toHaveBeenCalledOnce();
    expect(screen.getByRole('button', { name: 'Close' })).toBeDisabled();
    await act(async () => finish(result));
  });

  it('preserves 64-bit offsets without converting input to JavaScript numbers', async () => {
    vi.mocked(previewQueueOffset).mockResolvedValue({
      ...result,
      currentOffset: '9007199254740990',
      targetOffset: '9007199254740993',
      maxOffset: '9007199254740993',
      offsetDelta: '3',
      projectedLag: '0',
    });
    render(<QueueOffsetDialog target={target} onClose={vi.fn()} onApplied={vi.fn()} />);
    fireEvent.change(screen.getByRole('textbox', { name: 'Target offset' }), {
      target: { value: '9007199254740993' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Preview change' }));
    await waitFor(() => expect(screen.getByText('Skip 3 queue positions')).toBeVisible());
    fireEvent.click(screen.getByRole('button', { name: 'Confirm queue offset' }));
    await waitFor(() =>
      expect(applyQueueOffset).toHaveBeenCalledWith(target, '9007199254740993', '9007199254740990'),
    );
  });

  it('does not allow a no-op confirmation', async () => {
    vi.mocked(previewQueueOffset).mockResolvedValue({ ...result, offsetDelta: '0' });
    render(<QueueOffsetDialog target={target} onClose={vi.fn()} onApplied={vi.fn()} />);
    fireEvent.change(screen.getByRole('textbox', { name: 'Target offset' }), {
      target: { value: '40' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Preview change' }));
    await waitFor(() => expect(screen.getByText('No change')).toBeVisible());
    expect(screen.getByRole('button', { name: 'Confirm queue offset' })).toBeDisabled();
  });

  it('discards a response after unmount without refreshing the old instance', async () => {
    let finish!: (value: QueueOffsetPreview) => void;
    vi.mocked(applyQueueOffset).mockReturnValue(
      new Promise((resolve) => {
        finish = resolve;
      }),
    );
    const applied = vi.fn();
    const { unmount } = render(
      <QueueOffsetDialog target={target} onClose={vi.fn()} onApplied={applied} />,
    );
    await preview();
    fireEvent.click(screen.getByRole('button', { name: 'Confirm queue offset' }));
    unmount();
    await act(async () => finish(result));
    expect(applied).not.toHaveBeenCalled();
  });
});
