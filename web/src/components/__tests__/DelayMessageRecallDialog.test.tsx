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
import DelayMessageRecallDialog from '../DelayMessageRecallDialog';
import {
  previewMessageRecall,
  recallDelayMessage,
  type RecallTarget,
  type RecallReceipt,
} from '../../api/messageRecall';

vi.mock('../../api/messageRecall', () => ({
  previewMessageRecall: vi.fn(),
  recallDelayMessage: vi.fn(),
}));

const target: RecallTarget = {
  topic: 'orders',
  brokerName: 'broker-a',
  messageId: 'original-message',
  deliveryTimestamp: 1888825600000,
};
const receipt: RecallReceipt = { messageId: 'original-message', acceptedAt: 1788825600000 };

const render = (element: ReactElement) =>
  renderComponent(element, {
    wrapper: ({ children }) => (
      <ConfigProvider theme={{ token: { motion: false } }}>{children}</ConfigProvider>
    ),
  });

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(previewMessageRecall).mockResolvedValue(target);
  vi.mocked(recallDelayMessage).mockResolvedValue(receipt);
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

const open = () =>
  render(
    <DelayMessageRecallDialog instanceId="instance-a" initialTopic="orders" onClose={vi.fn()} />,
  );

async function inspect() {
  fireEvent.change(screen.getByLabelText('Recall handle'), {
    target: { value: ' original-handle ' },
  });
  fireEvent.click(screen.getByRole('button', { name: 'Inspect handle' }));
  await screen.findByText('original-message');
}

describe('DelayMessageRecallDialog', () => {
  it('requires inspection and then submits the exact inspected target after confirmation', async () => {
    open();
    expect(screen.getByRole('button', { name: 'Confirm recall' })).toBeDisabled();
    await inspect();
    expect(previewMessageRecall).toHaveBeenCalledWith({
      instanceId: 'instance-a',
      topic: 'orders',
      recallHandle: 'original-handle',
    });
    await waitFor(() => expect(screen.getByText('broker-a')).toBeVisible());
    expect(recallDelayMessage).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: 'Confirm recall' }));
    await waitFor(() =>
      expect(screen.getByText('Broker accepted the recall request')).toBeVisible(),
    );
    expect(recallDelayMessage).toHaveBeenCalledWith({
      instanceId: 'instance-a',
      topic: 'orders',
      recallHandle: 'original-handle',
    });
    expect(screen.getByRole('button', { name: 'Confirm recall' })).toBeDisabled();
  });

  it('rejects empty form fields without invoking either endpoint', async () => {
    open();
    fireEvent.click(screen.getByRole('button', { name: 'Inspect handle' }));
    await waitFor(() => expect(screen.getByText('Recall handle is required')).toBeVisible());
    expect(previewMessageRecall).not.toHaveBeenCalled();
    expect(recallDelayMessage).not.toHaveBeenCalled();
  });

  it('invalidates the confirmation whenever the handle or topic changes', async () => {
    open();
    await inspect();
    fireEvent.change(screen.getByLabelText('Topic'), { target: { value: 'other-topic' } });
    expect(screen.queryByText('original-message')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Confirm recall' })).toBeDisabled();
  });

  it('shows a malformed handle error and keeps recall disabled', async () => {
    vi.mocked(previewMessageRecall).mockRejectedValue(new Error('Recall handle is invalid'));
    open();
    fireEvent.change(screen.getByLabelText('Recall handle'), { target: { value: 'bad' } });
    fireEvent.click(screen.getByRole('button', { name: 'Inspect handle' }));
    await waitFor(() => expect(screen.getByText('Recall handle is invalid')).toBeVisible());
    expect(screen.getByRole('button', { name: 'Confirm recall' })).toBeDisabled();
  });

  it('keeps broker rejection visible and does not manufacture a success receipt', async () => {
    vi.mocked(recallDelayMessage).mockRejectedValue(new Error('recall failed, timestamp invalid'));
    open();
    await inspect();
    fireEvent.click(screen.getByRole('button', { name: 'Confirm recall' }));
    await waitFor(() => expect(screen.getByText('recall failed, timestamp invalid')).toBeVisible());
    expect(screen.queryByText('Broker accepted the recall request')).not.toBeInTheDocument();
  });

  it('submits only once and keeps the dialog locked while recall is pending', async () => {
    let finish!: (value: RecallReceipt) => void;
    vi.mocked(recallDelayMessage).mockReturnValue(
      new Promise((resolve) => {
        finish = resolve;
      }),
    );
    open();
    await inspect();
    const button = screen.getByRole('button', { name: 'Confirm recall' });
    fireEvent.click(button);
    fireEvent.click(button);
    expect(recallDelayMessage).toHaveBeenCalledTimes(1);
    expect(screen.getByLabelText('Topic')).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Close' })).toBeDisabled();
    await act(async () => finish(receipt));
    await waitFor(() =>
      expect(screen.getByText('Broker accepted the recall request')).toBeVisible(),
    );
  });

  it('ignores a late inspection after the dialog has closed', async () => {
    let finish!: (value: RecallTarget) => void;
    vi.mocked(previewMessageRecall).mockReturnValue(
      new Promise((resolve) => {
        finish = resolve;
      }),
    );
    const { unmount } = open();
    fireEvent.change(screen.getByLabelText('Recall handle'), {
      target: { value: 'original-handle' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Inspect handle' }));
    await waitFor(() => expect(previewMessageRecall).toHaveBeenCalledTimes(1));
    unmount();
    await act(async () => finish(target));
    expect(screen.queryByText('original-message')).not.toBeInTheDocument();
    expect(recallDelayMessage).not.toHaveBeenCalled();
  });
});
