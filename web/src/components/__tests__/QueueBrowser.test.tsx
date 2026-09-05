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
import { App } from 'antd';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { MessageRecord, QueueOffset } from '../../api/message';
import { getQueueOffsets, pullMessageAtOffset } from '../../api/message';
import { formatTimeMs, QueueBrowserResults, useQueueBrowser } from '../QueueBrowser';
import { getQueueBacklog } from '../../utils/queueBrowserBacklog';

vi.mock('../../api/message', () => ({
  getQueueOffsets: vi.fn(),
  pullMessageAtOffset: vi.fn(),
}));

const createDeferred = <T,>() => {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((promiseResolve) => {
    resolve = promiseResolve;
  });
  return { promise, resolve };
};

const queue = (brokerName: string): QueueOffset => ({
  brokerName,
  queueId: 0,
  minOffset: 0,
  maxOffset: 3,
});

const messageRecord = (msgId: string): MessageRecord => ({
  msgId,
  topic: 'topic-a',
  tag: null,
  key: null,
  brokerName: 'broker-a',
  queueId: 0,
  queueOffset: 2,
  body: '{}',
  storeTime: '2026-08-25T00:00:00Z',
  bornHost: '127.0.0.1:1000',
  storeHost: '127.0.0.1:10911',
  properties: {},
  size: 2,
});

function QueueBrowserProbe({ instanceId = 'instance-a' }: { instanceId?: string }) {
  const state = useQueueBrowser(instanceId);
  const firstQueue = state.queues[0];
  return (
    <div>
      <button type="button" onClick={() => state.setTopic('topic-a')}>
        topic-a
      </button>
      <button type="button" onClick={() => state.setTopic('topic-b')}>
        topic-b
      </button>
      <button type="button" onClick={() => void state.loadQueues()}>
        load
      </button>
      <button
        type="button"
        disabled={!firstQueue}
        onClick={() => firstQueue && void state.handlePull(firstQueue)}
      >
        pull
      </button>
      <output aria-label="topic">{state.topic ?? ''}</output>
      <output aria-label="queues">{state.queues.map((item) => item.brokerName).join(',')}</output>
      <output aria-label="entries">
        {state.entries.map((entry) => entry.message?.msgId ?? 'empty').join(',')}
      </output>
      <output aria-label="loading">{String(state.loading)}</output>
      <output aria-label="pulling">{state.pulling.size > 0 ? 'true' : 'false'}</output>
    </div>
  );
}

function QueueBrowserViewProbe({ instanceId = 'instance-a' }: { instanceId?: string }) {
  const state = useQueueBrowser(instanceId);
  return (
    <App>
      <div>
        <button type="button" onClick={() => state.setTopic('topic-a')}>
          topic-a
        </button>
        <button type="button" onClick={() => void state.loadQueues()}>
          load
        </button>
        <QueueBrowserResults state={state} />
      </div>
    </App>
  );
}

describe('formatTimeMs', () => {
  it('preserves the Unix epoch timestamp', () => {
    expect(formatTimeMs(0)).not.toBe('-');
  });

  it.each(['not-a-date', Number.NaN, Number.POSITIVE_INFINITY])(
    'returns a placeholder for invalid timestamp %s',
    (value) => {
      expect(formatTimeMs(value)).toBe('-');
    },
  );
});

describe('getQueueBacklog', () => {
  it('returns the number of messages available in the queue', () => {
    expect(getQueueBacklog({ minOffset: 4, maxOffset: 10 })).toBe(6);
  });

  it('does not return a negative backlog for invalid offset ranges', () => {
    expect(getQueueBacklog({ minOffset: 10, maxOffset: 4 })).toBe(0);
    expect(getQueueBacklog({ minOffset: 5, maxOffset: 5 })).toBe(0);
  });
});

describe('QueueBrowser request ownership', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('keeps the newest topic queue load when an older request resolves later', async () => {
    const topicA = createDeferred<QueueOffset[]>();
    const topicB = createDeferred<QueueOffset[]>();
    vi.mocked(getQueueOffsets)
      .mockReturnValueOnce(topicA.promise)
      .mockReturnValueOnce(topicB.promise);
    const user = userEvent.setup();
    render(<QueueBrowserProbe />);

    await user.click(screen.getByRole('button', { name: 'topic-a' }));
    await waitFor(() => expect(screen.getByLabelText('topic')).toHaveTextContent('topic-a'));
    await user.click(screen.getByRole('button', { name: 'load' }));
    await waitFor(() =>
      expect(getQueueOffsets).toHaveBeenLastCalledWith({
        instanceId: 'instance-a',
        topic: 'topic-a',
      }),
    );

    await user.click(screen.getByRole('button', { name: 'topic-b' }));
    await waitFor(() => expect(screen.getByLabelText('topic')).toHaveTextContent('topic-b'));
    await user.click(screen.getByRole('button', { name: 'load' }));
    await waitFor(() =>
      expect(getQueueOffsets).toHaveBeenLastCalledWith({
        instanceId: 'instance-a',
        topic: 'topic-b',
      }),
    );

    await act(async () => {
      topicB.resolve([queue('broker-b')]);
    });
    expect(screen.getByLabelText('queues')).toHaveTextContent('broker-b');

    await act(async () => {
      topicA.resolve([queue('broker-a')]);
    });
    expect(screen.getByLabelText('queues')).toHaveTextContent('broker-b');
    expect(screen.getByLabelText('queues')).not.toHaveTextContent('broker-a');
  });

  it('ignores a pulled message after the topic changes', async () => {
    const pull = createDeferred<MessageRecord | null>();
    vi.mocked(getQueueOffsets).mockResolvedValue([queue('broker-a')]);
    vi.mocked(pullMessageAtOffset).mockReturnValue(pull.promise);
    const user = userEvent.setup();
    render(<QueueBrowserProbe />);

    await user.click(screen.getByRole('button', { name: 'topic-a' }));
    await waitFor(() => expect(screen.getByLabelText('topic')).toHaveTextContent('topic-a'));
    await user.click(screen.getByRole('button', { name: 'load' }));
    await waitFor(() => expect(screen.getByLabelText('queues')).toHaveTextContent('broker-a'));

    await user.click(screen.getByRole('button', { name: 'pull' }));
    await waitFor(() =>
      expect(pullMessageAtOffset).toHaveBeenCalledWith({
        instanceId: 'instance-a',
        topic: 'topic-a',
        brokerName: 'broker-a',
        queueId: 0,
        offset: 2,
      }),
    );

    await user.click(screen.getByRole('button', { name: 'topic-b' }));
    await waitFor(() => expect(screen.getByLabelText('topic')).toHaveTextContent('topic-b'));

    await act(async () => {
      pull.resolve(messageRecord('stale-message'));
    });
    expect(screen.getByLabelText('entries')).toHaveTextContent('');
    expect(screen.queryByText('stale-message')).not.toBeInTheDocument();
  });

  it('deduplicates pulls for the same queue before loading state renders', async () => {
    const pull = createDeferred<MessageRecord | null>();
    vi.mocked(getQueueOffsets).mockResolvedValue([queue('broker-a')]);
    vi.mocked(pullMessageAtOffset).mockReturnValue(pull.promise);
    const user = userEvent.setup();
    render(<QueueBrowserProbe />);

    await user.click(screen.getByRole('button', { name: 'topic-a' }));
    await user.click(screen.getByRole('button', { name: 'load' }));
    await waitFor(() => expect(screen.getByLabelText('queues')).toHaveTextContent('broker-a'));

    const pullButton = screen.getByRole('button', { name: 'pull' });
    fireEvent.click(pullButton);
    fireEvent.click(pullButton);

    expect(pullMessageAtOffset).toHaveBeenCalledTimes(1);
    await act(async () => pull.resolve(messageRecord('message-a')));
  });

  it('deduplicates queue loads before loading state renders', async () => {
    const queues = createDeferred<QueueOffset[]>();
    vi.mocked(getQueueOffsets).mockReturnValue(queues.promise);
    const user = userEvent.setup();
    render(<QueueBrowserProbe />);

    await user.click(screen.getByRole('button', { name: 'topic-a' }));
    const loadButton = screen.getByRole('button', { name: 'load' });
    fireEvent.click(loadButton);
    fireEvent.click(loadButton);

    expect(getQueueOffsets).toHaveBeenCalledTimes(1);
    await act(async () => queues.resolve([queue('broker-a')]));
  });
});

describe('QueueBrowserResults navigation', () => {
  const queues: QueueOffset[] = [
    { brokerName: 'broker-a', queueId: 0, minOffset: 0, maxOffset: 12 },
    { brokerName: 'broker-a', queueId: 1, minOffset: 4, maxOffset: 4 },
    { brokerName: 'broker-b', queueId: 2, minOffset: 1, maxOffset: 20 },
  ];

  beforeEach(() => {
    vi.clearAllMocks();
  });

  beforeAll(() => {
    Object.defineProperty(window, 'matchMedia', {
      writable: true,
      value: vi.fn().mockImplementation(() => ({
        matches: false,
        addListener: vi.fn(),
        removeListener: vi.fn(),
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
        dispatchEvent: vi.fn(),
      })),
    });
  });

  const renderLoaded = async () => {
    vi.mocked(getQueueOffsets).mockResolvedValue(queues);
    const user = userEvent.setup();
    render(<QueueBrowserViewProbe />);
    await user.click(screen.getByRole('button', { name: 'topic-a' }));
    await user.click(screen.getByRole('button', { name: 'load' }));
    await screen.findByText('broker-b');
    return user;
  };

  it('filters queues by broker or queue id while keeping topic-wide totals', async () => {
    const user = await renderLoaded();

    await user.type(screen.getByPlaceholderText('搜索 Broker 或 Queue'), 'broker-b');

    expect(screen.getByText('broker-b')).toBeInTheDocument();
    expect(screen.queryByText('broker-a')).not.toBeInTheDocument();
    expect(screen.getByTestId('queue-browser-summary')).toHaveTextContent(
      '显示 1 / 3 个队列，Topic 总消息量 31 条',
    );
  });

  it('hides empty queues and shows the displayed count separately', async () => {
    const user = await renderLoaded();

    await user.click(screen.getByRole('button', { name: '仅显示非空队列' }));

    expect(screen.getByText('broker-a')).toBeInTheDocument();
    expect(screen.getByText('broker-b')).toBeInTheDocument();
    expect(screen.queryByText('broker-a-1')).not.toBeInTheDocument();
    expect(screen.getByTestId('queue-browser-summary')).toHaveTextContent(
      '显示 2 / 3 个队列，Topic 总消息量 31 条',
    );
  });

  it('shows backlog and supports sorting by backlog', async () => {
    const user = await renderLoaded();

    expect(screen.getByText('12')).toBeInTheDocument();
    expect(screen.getAllByText('19').length).toBeGreaterThan(0);
    expect(screen.getAllByText('0').length).toBeGreaterThan(0);
    await user.click(screen.getByText('积压量'));

    const brokers = await screen.findAllByText(/broker-[ab]/);
    expect(brokers.map((item) => item.textContent)).toEqual(['broker-a', 'broker-a', 'broker-b']);
  });
});
