/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { getQueueOffsets, previewQueueFilter } from '../../api/message';
import type { QueueFilterPage, MessageRecord } from '../../api/message';
import QueueFilterPreview from '../QueueFilterPreview';
import { QueueBrowserResults, useQueueBrowser } from '../QueueBrowser';

vi.mock('../../api/message', () => ({
  getQueueOffsets: vi.fn(),
  previewQueueFilter: vi.fn(),
  pullMessageAtOffset: vi.fn(),
}));

const queue = { brokerName: 'broker-a', queueId: 0, minOffset: 10, maxOffset: 100 };
const emptyBatch: QueueFilterPage = {
  items: [],
  startOffset: 10,
  nextOffset: 50,
  minOffset: 10,
  maxOffset: 100,
  hasMore: true,
  offsetAdjusted: false,
  status: 'NO_MATCHED_MSG',
};
const matched: MessageRecord = {
  msgId: 'message-a',
  topic: 'orders',
  tag: 'paid',
  key: null,
  brokerName: 'broker-a',
  queueId: 0,
  queueOffset: 55,
  body: 'event payload',
  storeTime: '2026-09-08T00:00:00Z',
  bornHost: '127.0.0.1',
  storeHost: '127.0.0.1',
  properties: { amount: '200' },
  size: 13,
};

function BrowserHarness() {
  const state = useQueueBrowser('instance-a');
  return (
    <>
      <button onClick={() => state.setTopic('orders')}>Select topic</button>
      <button onClick={() => void state.loadQueues()}>Load queues</button>
      <QueueBrowserResults state={state} />
    </>
  );
}

const renderPreview = () =>
  render(
    <QueueFilterPreview
      instanceId="instance-a"
      topic="orders"
      queue={queue}
      initialOffset={10}
      onClose={vi.fn()}
    />,
  );

describe('QueueFilterPreview', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.stubGlobal(
      'matchMedia',
      vi.fn().mockImplementation((query: string) => ({
        matches: false,
        media: query,
        onchange: null,
        addListener: vi.fn(),
        removeListener: vi.fn(),
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
        dispatchEvent: vi.fn(),
      })),
    );
    vi.mocked(getQueueOffsets).mockResolvedValue([queue]);
    vi.mocked(previewQueueFilter).mockResolvedValue(emptyBatch);
  });
  afterEach(() => vi.unstubAllGlobals());

  it('continues after an empty filtered batch and shows the matching message properties', async () => {
    const user = userEvent.setup();
    vi.mocked(previewQueueFilter)
      .mockResolvedValueOnce(emptyBatch)
      .mockResolvedValueOnce({
        ...emptyBatch,
        items: [matched],
        startOffset: 50,
        nextOffset: 100,
        hasMore: false,
        status: 'FOUND',
      });
    renderPreview();
    await user.click(screen.getByRole('button', { name: 'Preview from offset' }));
    expect(
      await screen.findByText('No matches in this batch. Continue to inspect later offsets.'),
    ).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Next batch' }));
    expect(await screen.findByText('message-a')).toBeInTheDocument();
    expect(previewQueueFilter).toHaveBeenLastCalledWith(
      expect.objectContaining({ offset: 50 }),
      expect.any(AbortSignal),
    );
    expect(screen.getByRole('button', { name: 'Next batch' })).toBeDisabled();
    await user.click(screen.getByRole('button', { name: 'Expand row' }));
    expect(screen.getByText('event payload')).toBeInTheDocument();
    expect(screen.getByText(/"amount": "200"/)).toBeInTheDocument();
  });

  it('passes SQL92 unchanged and keeps broker rejection visible for correction', async () => {
    const user = userEvent.setup();
    vi.mocked(previewQueueFilter).mockRejectedValueOnce(
      new Error('Property filtering is disabled'),
    );
    renderPreview();
    await user.click(screen.getByRole('combobox', { name: 'Expression type' }));
    const options = screen.getAllByText('SQL92', { exact: true });
    await user.click(options[options.length - 1]);
    fireEvent.change(screen.getByRole('textbox', { name: 'Filter expression' }), {
      target: { value: "amount > 100 AND region = 'east'" },
    });
    await user.click(screen.getByRole('button', { name: 'Preview from offset' }));
    expect(await screen.findByText('Property filtering is disabled')).toBeInTheDocument();
    expect(previewQueueFilter).toHaveBeenCalledTimes(1);
    expect(previewQueueFilter).toHaveBeenCalledWith(
      expect.objectContaining({
        expressionType: 'SQL92',
        expression: "amount > 100 AND region = 'east'",
      }),
      expect.any(AbortSignal),
    );
    expect(screen.getByRole('textbox', { name: 'Filter expression' })).toBeEnabled();
  });

  it('clears the continuation when the operator edits the expression', async () => {
    const user = userEvent.setup();
    renderPreview();
    await user.click(screen.getByRole('button', { name: 'Preview from offset' }));
    await waitFor(() => expect(screen.getByRole('button', { name: 'Next batch' })).toBeEnabled());
    fireEvent.change(screen.getByRole('textbox', { name: 'Filter expression' }), {
      target: { value: 'paid || shipped' },
    });
    expect(screen.getByRole('button', { name: 'Next batch' })).toBeDisabled();
    await user.click(screen.getByRole('button', { name: 'Preview from offset' }));
    expect(previewQueueFilter).toHaveBeenLastCalledWith(
      expect.objectContaining({ offset: 10, expression: 'paid || shipped' }),
      expect.any(AbortSignal),
    );
  });

  it('aborts a pending request when the drawer is removed', async () => {
    let complete!: (page: QueueFilterPage) => void;
    vi.mocked(previewQueueFilter).mockReturnValue(
      new Promise((resolve) => {
        complete = resolve;
      }),
    );
    const { unmount } = renderPreview();
    fireEvent.click(screen.getByRole('button', { name: 'Preview from offset' }));
    const signal = vi.mocked(previewQueueFilter).mock.calls[0][1];
    expect(signal?.aborted).toBe(false);
    unmount();
    expect(signal?.aborted).toBe(true);
    await act(async () => complete(emptyBatch));
  });

  it('opens from the selected queue and uses its current slider position', async () => {
    const user = userEvent.setup();
    render(<BrowserHarness />);
    await user.click(screen.getByRole('button', { name: 'Select topic' }));
    await user.click(screen.getByRole('button', { name: 'Load queues' }));
    await user.click(
      await screen.findByRole('button', { name: 'Preview filter on broker-a queue 0' }),
    );
    expect(screen.getByRole('spinbutton', { name: 'Starting offset' })).toHaveValue('99');
    await user.click(screen.getByRole('button', { name: 'Preview from offset' }));
    expect(previewQueueFilter).toHaveBeenCalledWith(
      expect.objectContaining({
        instanceId: 'instance-a',
        topic: 'orders',
        brokerName: 'broker-a',
        queueId: 0,
        offset: 99,
      }),
      expect.any(AbortSignal),
    );
  });
});
