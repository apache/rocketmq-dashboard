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
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { MessageRecord, QueueOffset } from '../../api/message';
import { getQueueOffsets, pullMessageAtOffset } from '../../api/message';
import {
  formatTimeMs,
  QueueBrowserResults,
  useQueueBrowser,
  type QueueBrowserState,
} from '../QueueBrowser';

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
describe('QueueBrowser results filtering', () => {
  beforeAll(() => {
    Object.defineProperty(window, 'matchMedia', {
      writable: true,
      value: vi.fn().mockImplementation((query: string) => ({
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
  });

  const browserState = (queues: QueueOffset[]): QueueBrowserState => ({
    topic: 'topic-a',
    setTopic: vi.fn(),
    queues,
    loading: false,
    offsets: {},
    setOffsets: vi.fn(),
    pulling: new Set<string>(),
    entries: [],
    loadQueues: vi.fn(),
    handlePull: vi.fn(),
    closeEntry: vi.fn(),
  });

  const rows = (container: HTMLElement) =>
    Array.from(container.querySelectorAll('tbody tr')).map((row) => row.textContent ?? '');

  it('filters queues by broker name as the operator types', async () => {
    const user = userEvent.setup();
    const { container } = render(
      <QueueBrowserResults state={browserState([queue('broker-a'), queue('broker-b')])} />,
    );
    expect(rows(container)).toHaveLength(2);

    await user.type(screen.getByPlaceholderText('搜索 Broker / Queue ID'), 'broker-b');

    expect(rows(container)).toHaveLength(1);
    expect(rows(container)[0]).toContain('broker-b');
  });

  it('filters queues by exact queue id', async () => {
    const user = userEvent.setup();
    const q0 = { ...queue('broker-a'), queueId: 0, minOffset: 1, maxOffset: 5 };
    const q1 = { ...queue('broker-a'), queueId: 1, minOffset: 1, maxOffset: 5 };
    const { container } = render(<QueueBrowserResults state={browserState([q0, q1])} />);

    await user.type(screen.getByPlaceholderText('搜索 Broker / Queue ID'), '1');

    expect(rows(container)).toHaveLength(1);
    expect(rows(container)[0]).toContain('1');
  });

  it('hides empty queues when the non-empty toggle is enabled', async () => {
    const user = userEvent.setup();
    const empty = { ...queue('broker-a'), minOffset: 2, maxOffset: 2 };
    const busy = { ...queue('broker-b'), minOffset: 0, maxOffset: 10 };
    const { container } = render(<QueueBrowserResults state={browserState([empty, busy])} />);
    expect(rows(container)).toHaveLength(2);

    await user.click(screen.getByRole('switch', { name: '仅显示非空队列' }));

    expect(rows(container)).toHaveLength(1);
    expect(rows(container)[0]).toContain('broker-b');
  });

  it('sorts queues by message backlog in descending order', async () => {
    const user = userEvent.setup();
    const small = { ...queue('broker-a'), minOffset: 0, maxOffset: 3 };
    const large = { ...queue('broker-b'), minOffset: 0, maxOffset: 12 };
    const { container } = render(<QueueBrowserResults state={browserState([small, large])} />);

    await user.click(screen.getByRole('combobox', { name: '排序' }));
    await user.click(
      await screen.findByText('排序: 积压 ↓', { selector: '.ant-select-item-option-content' }),
    );

    expect(rows(container)[0]).toContain('broker-b');
    expect(rows(container)[1]).toContain('broker-a');
  });

  it('reports displayed count separately from the unfiltered totals', async () => {
    const user = userEvent.setup();
    const empty = { ...queue('broker-a'), minOffset: 2, maxOffset: 2 };
    const busy = { ...queue('broker-b'), minOffset: 0, maxOffset: 10 };
    render(<QueueBrowserResults state={browserState([empty, busy])} />);

    await user.click(screen.getByRole('switch', { name: '仅显示非空队列' }));

    expect(screen.getByText(/显示 1 \/ 共 2 个队列，总消息量/)).toBeInTheDocument();
  });
});
