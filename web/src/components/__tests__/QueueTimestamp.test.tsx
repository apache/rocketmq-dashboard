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
import { act, fireEvent, render, renderHook, screen } from '@testing-library/react';
import { message } from 'antd';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { getQueueOffsets, locateQueueByTime, pullMessageAtOffset } from '../../api/message';
import type { QueueTimestamp } from '../../api/message';
import { QueueBrowserControls, QueueBrowserResults, useQueueBrowser } from '../QueueBrowser';

vi.mock('../../api/message', () => ({
  getQueueOffsets: vi.fn(),
  locateQueueByTime: vi.fn(),
  pullMessageAtOffset: vi.fn(),
}));

const queue = { brokerName: 'broker-a', queueId: 2, minOffset: 0, maxOffset: 100 };
const position = { ...queue, minOffset: 10, maxOffset: 110, offset: 42 };
const deferred = () => {
  let resolve!: (value: QueueTimestamp) => void;
  const promise = new Promise<QueueTimestamp>((done) => {
    resolve = done;
  });
  return { promise, resolve };
};

async function browser() {
  const hook = renderHook(({ instanceId }) => useQueueBrowser(instanceId), {
    initialProps: { instanceId: 'instance-a' },
  });
  await act(async () => hook.result.current.setTopic('orders'));
  await act(async () => hook.result.current.loadQueues());
  await act(async () => hook.result.current.setTimestamp(1788825600000));
  return hook;
}

describe('queue timestamp navigation', () => {
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
    vi.mocked(locateQueueByTime).mockResolvedValue(position);
    vi.spyOn(message, 'info').mockImplementation(() => undefined as never);
    vi.spyOn(message, 'error').mockImplementation(() => undefined as never);
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it('updates the queue snapshot and uses the located offset on the next View', async () => {
    const { result } = await browser();
    await act(async () => result.current.handleLocate(queue));
    expect(locateQueueByTime).toHaveBeenCalledWith({
      instanceId: 'instance-a',
      topic: 'orders',
      brokerName: 'broker-a',
      queueId: 2,
      timestamp: 1788825600000,
    });
    expect(result.current.queues).toEqual([position]);
    expect(result.current.offsets['broker-a-2']).toBe(42);
    expect(pullMessageAtOffset).not.toHaveBeenCalled();
    await act(async () => result.current.handlePull(queue));
    expect(pullMessageAtOffset).toHaveBeenCalledWith(expect.objectContaining({ offset: 42 }));
  });

  it('reports an empty queue and removes its previous readable range', async () => {
    vi.mocked(locateQueueByTime).mockResolvedValue({
      ...queue,
      minOffset: 100,
      maxOffset: 100,
      offset: null,
    });
    const { result } = await browser();
    await act(async () => result.current.handleLocate(queue));
    expect(result.current.offsets['broker-a-2']).toBe(100);
    expect(message.info).toHaveBeenCalledWith('This queue is empty; no message can be located.');
    render(<QueueBrowserResults state={result.current} />);
    expect(screen.getByRole('button', { name: 'View broker-a queue 2' })).toBeDisabled();
  });

  it('retains the previous position on a failed lookup and permits retry', async () => {
    vi.mocked(locateQueueByTime).mockRejectedValueOnce(new Error('Broker unavailable'));
    const { result } = await browser();
    await act(async () => result.current.handleLocate(queue));
    expect(result.current.offsets['broker-a-2']).toBe(99);
    expect(result.current.locating.size).toBe(0);
    expect(message.error).toHaveBeenCalledWith('Broker unavailable');
    await act(async () => result.current.handleLocate(queue));
    expect(result.current.offsets['broker-a-2']).toBe(42);
  });

  it('discards a lookup completed after changing instances', async () => {
    const pending = deferred();
    vi.mocked(locateQueueByTime).mockReturnValueOnce(pending.promise);
    const { result, rerender } = await browser();
    act(() => {
      void result.current.handleLocate(queue);
    });
    await act(async () => rerender({ instanceId: 'instance-b' }));
    await act(async () => pending.resolve(position));
    expect(result.current.queues).toEqual([]);
    expect(result.current.offsets).toEqual({});
    expect(result.current.locating.size).toBe(0);
  });

  it('ignores the previous timestamp result when the operator changes the time', async () => {
    const pending = deferred();
    vi.mocked(locateQueueByTime).mockReturnValueOnce(pending.promise);
    const { result } = await browser();
    act(() => {
      void result.current.handleLocate(queue);
    });
    act(() => result.current.setTimestamp(0));
    await act(async () => pending.resolve(position));
    expect(result.current.offsets['broker-a-2']).toBe(99);
    await act(async () => result.current.handleLocate(queue));
    expect(locateQueueByTime).toHaveBeenLastCalledWith(expect.objectContaining({ timestamp: 0 }));
    expect(result.current.offsets['broker-a-2']).toBe(42);
  });

  it('does not let an old completion release a new lookup after reloading the same queue', async () => {
    const old = deferred();
    const current = deferred();
    vi.mocked(locateQueueByTime)
      .mockReturnValueOnce(old.promise)
      .mockReturnValueOnce(current.promise);
    const { result } = await browser();
    act(() => {
      void result.current.handleLocate(queue);
    });
    act(() => {
      void result.current.handleLocate(queue);
    });
    expect(locateQueueByTime).toHaveBeenCalledTimes(1);
    await act(async () => result.current.loadQueues());
    act(() => {
      void result.current.handleLocate(queue);
    });
    await act(async () => old.resolve({ ...position, offset: 11 }));
    expect(result.current.locating.has('broker-a-2')).toBe(true);
    expect(result.current.offsets['broker-a-2']).toBe(99);
    act(() => {
      void result.current.handleLocate(queue);
    });
    expect(locateQueueByTime).toHaveBeenCalledTimes(2);
    await act(async () => current.resolve(position));
    expect(result.current.offsets['broker-a-2']).toBe(42);
    expect(result.current.locating.size).toBe(0);
  });

  it('offers a labelled local-time picker and dispatches the row action', async () => {
    const { result } = await browser();
    render(
      <>
        <QueueBrowserControls instanceId="instance-a" state={result.current} topicOptions={[]} />
        <QueueBrowserResults state={result.current} />
      </>,
    );
    expect(screen.getByRole('textbox', { name: 'Queue lookup time' })).toBeEnabled();
    await act(async () =>
      fireEvent.click(screen.getByRole('button', { name: 'Locate broker-a queue 2 by time' })),
    );
    expect(locateQueueByTime).toHaveBeenCalledTimes(1);
  });
});
