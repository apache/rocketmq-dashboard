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

import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { MessageRecord, QueueOffset } from '../../api/message';
import { getQueueOffsets, pullMessageAtOffset } from '../../api/message';
import { formatTimeMs, useQueueBrowser } from '../QueueBrowser';

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
});
