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

import {
  act,
  fireEvent,
  render,
  renderHook,
  screen,
  waitFor,
  within,
} from '@testing-library/react';
import { message } from 'antd';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { StrictMode } from 'react';
import type { MessageRecord, QueueOffset } from '../../api/message';
import { getQueueOffsets, pullMessageAtOffset } from '../../api/message';
import { QueueBrowserControls, QueueBrowserResults, useQueueBrowser } from '../QueueBrowser';
import { LangProvider } from '../../i18n/LangContext';

vi.mock('../../api/message', () => ({
  getQueueOffsets: vi.fn(),
  pullMessageAtOffset: vi.fn(),
}));

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

const createDeferred = <T,>() => {
  let resolve!: (value: T) => void;
  let reject!: (reason: Error) => void;
  const promise = new Promise<T>((promiseResolve, promiseReject) => {
    resolve = promiseResolve;
    reject = promiseReject;
  });
  return { promise, resolve, reject };
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

const renderLoadedBrowser = async (queues = [queue('broker-a')]) => {
  vi.mocked(getQueueOffsets).mockResolvedValue(queues);
  const { result } = renderHook(() => useQueueBrowser('instance-a'));
  act(() => result.current.setTopic('topic-a'));
  await act(async () => {
    await result.current.loadQueues();
  });
  return result;
};

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

function QueueBrowserResultsProbe() {
  const state = useQueueBrowser('instance-a');
  const firstQueue = state.queues[0];
  return (
    <div>
      <button type="button" onClick={() => state.setTopic('topic-a')}>
        topic-a
      </button>
      <button type="button" onClick={() => void state.loadQueues()}>
        load
      </button>
      <button type="button" onClick={() => firstQueue && void state.handlePull(firstQueue)}>
        pull
      </button>
      <QueueBrowserResults state={state} onOpenMessage={() => undefined} />
    </div>
  );
}

describe('QueueBrowser request ownership', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(getQueueOffsets).mockReset();
    vi.mocked(pullMessageAtOffset).mockReset();
  });

  describe('page lifetime', () => {
    afterEach(() => vi.restoreAllMocks());
    it.each([
      { operation: 'load', abandoned: true },
      { operation: 'pull', abandoned: true },
      { operation: 'load', abandoned: false },
      { operation: 'pull', abandoned: false },
    ])(
      'onlyReportsFailuresOwnedByTheMountedPageTest ($operation/$abandoned)',
      async ({ operation, abandoned }) => {
        const errorToast = vi
          .spyOn(message, 'error')
          .mockImplementation(() => (() => {}) as ReturnType<typeof message.error>);
        let reject!: (error: Error) => void;
        const pending = new Promise<never>((_resolve, rejectPromise) => {
          reject = rejectPromise;
        });
        vi.mocked(getQueueOffsets).mockResolvedValue([queue('broker-a')]);
        vi.mocked(pullMessageAtOffset).mockResolvedValue(messageRecord('fresh-message'));
        if (operation === 'load') vi.mocked(getQueueOffsets).mockReturnValueOnce(pending);
        else vi.mocked(pullMessageAtOffset).mockReturnValueOnce(pending);
        const user = userEvent.setup();
        const view = render(
          <StrictMode>
            <QueueBrowserProbe />
          </StrictMode>,
        );
        await user.click(screen.getByRole('button', { name: 'topic-a' }));
        await user.click(screen.getByRole('button', { name: 'load' }));
        if (operation === 'pull') {
          await waitFor(() =>
            expect(screen.getByLabelText('queues')).toHaveTextContent('broker-a'),
          );
          await user.click(screen.getByRole('button', { name: 'pull' }));
        }
        if (abandoned) view.unmount();
        await act(async () => {
          reject(new Error('queue request failed'));
        });
        expect(errorToast).toHaveBeenCalledTimes(abandoned ? 0 : 1);
        if (!abandoned) view.unmount();
        render(
          <StrictMode>
            <QueueBrowserProbe />
          </StrictMode>,
        );
        await user.click(screen.getByRole('button', { name: 'topic-a' }));
        await user.click(screen.getByRole('button', { name: 'load' }));
        await waitFor(() => expect(screen.getByLabelText('queues')).toHaveTextContent('broker-a'));
        await user.click(screen.getByRole('button', { name: 'pull' }));
        await waitFor(() =>
          expect(screen.getByLabelText('entries')).toHaveTextContent('fresh-message'),
        );
        expect(screen.getByLabelText('pulling')).toHaveTextContent('false');
      },
    );
  });
  afterEach(() => vi.restoreAllMocks());

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

  it('does not reopen a closed message card when its replacement pull resolves', async () => {
    const replacement = createDeferred<MessageRecord | null>();
    vi.mocked(getQueueOffsets).mockResolvedValue([queue('broker-a')]);
    vi.mocked(pullMessageAtOffset)
      .mockResolvedValueOnce(messageRecord('initial-message'))
      .mockReturnValueOnce(replacement.promise);
    const user = userEvent.setup();
    render(
      <LangProvider>
        <QueueBrowserResultsProbe />
      </LangProvider>,
    );

    await user.click(screen.getByRole('button', { name: 'topic-a' }));
    await user.click(screen.getByRole('button', { name: 'load' }));
    await screen.findByText('broker-a');
    await user.click(screen.getByRole('button', { name: 'pull' }));
    await screen.findByText('initial-message');
    await user.click(screen.getByRole('button', { name: 'pull' }));
    await waitFor(() => expect(pullMessageAtOffset).toHaveBeenCalledTimes(2));
    await user.click(screen.getByRole('button', { name: '移除 broker-a-0 的消息记录' }));

    await act(async () => replacement.resolve(messageRecord('late-message')));

    expect(screen.queryByText('initial-message')).not.toBeInTheDocument();
    expect(screen.queryByText('late-message')).not.toBeInTheDocument();
  });

  it('closing one queue keeps another queue pull active', async () => {
    const closedPull = createDeferred<MessageRecord | null>();
    const otherPull = createDeferred<MessageRecord | null>();
    vi.mocked(pullMessageAtOffset)
      .mockResolvedValueOnce(messageRecord('initial-message'))
      .mockReturnValueOnce(closedPull.promise)
      .mockReturnValueOnce(otherPull.promise);
    const browser = await renderLoadedBrowser([queue('broker-a'), queue('broker-b')]);
    await act(async () => {
      await browser.current.handlePull(queue('broker-a'));
    });
    act(() => {
      void browser.current.handlePull(queue('broker-a'));
      void browser.current.handlePull(queue('broker-b'));
      browser.current.closeEntry('broker-a-0');
    });

    await act(async () => closedPull.resolve(messageRecord('late-message')));
    expect(browser.current.entries).toEqual([]);
    expect([...browser.current.pulling]).toEqual(['broker-b-0']);
    await act(async () => otherPull.resolve(messageRecord('other-message')));
    expect(browser.current.entries.map((entry) => entry.message?.msgId)).toEqual(['other-message']);
    expect(browser.current.pulling.size).toBe(0);
  });

  it('a dismissed pull cannot unlock or overwrite a newly requested message', async () => {
    const stalePull = createDeferred<MessageRecord | null>();
    const currentPull = createDeferred<MessageRecord | null>();
    vi.mocked(pullMessageAtOffset)
      .mockResolvedValueOnce(messageRecord('initial-message'))
      .mockReturnValueOnce(stalePull.promise)
      .mockReturnValueOnce(currentPull.promise);
    const browser = await renderLoadedBrowser();
    await act(async () => {
      await browser.current.handlePull(queue('broker-a'));
    });
    act(() => {
      void browser.current.handlePull(queue('broker-a'));
      browser.current.closeEntry('broker-a-0');
      void browser.current.handlePull(queue('broker-a'));
    });
    expect(pullMessageAtOffset).toHaveBeenCalledTimes(3);

    await act(async () => stalePull.resolve(messageRecord('stale-message')));
    expect(browser.current.entries).toEqual([]);
    expect(browser.current.pulling.has('broker-a-0')).toBe(true);
    act(() => {
      void browser.current.handlePull(queue('broker-a'));
    });
    expect(pullMessageAtOffset).toHaveBeenCalledTimes(3);
    await act(async () => currentPull.resolve(messageRecord('current-message')));
    expect(browser.current.entries.map((entry) => entry.message?.msgId)).toEqual([
      'current-message',
    ]);
    expect(browser.current.pulling.size).toBe(0);
  });

  it('ignores a dismissed pull failure', async () => {
    const dismissedPull = createDeferred<MessageRecord | null>();
    const errorToast = vi.spyOn(message, 'error').mockImplementation(vi.fn());
    vi.mocked(pullMessageAtOffset)
      .mockResolvedValueOnce(messageRecord('initial-message'))
      .mockReturnValueOnce(dismissedPull.promise);
    const browser = await renderLoadedBrowser();
    await act(async () => {
      await browser.current.handlePull(queue('broker-a'));
    });
    act(() => {
      void browser.current.handlePull(queue('broker-a'));
      browser.current.closeEntry('broker-a-0');
    });

    await act(async () => dismissedPull.reject(new Error('dismissed request failed')));
    expect(errorToast).not.toHaveBeenCalled();
    expect(browser.current.entries).toEqual([]);
    expect(browser.current.pulling.size).toBe(0);
    errorToast.mockRestore();
  });

  it('keeps a replacement pull locked when a stale pull settles', async () => {
    const stalePull = createDeferred<MessageRecord | null>();
    const currentPull = createDeferred<MessageRecord | null>();
    const unexpectedPull = createDeferred<MessageRecord | null>();
    vi.mocked(getQueueOffsets).mockResolvedValue([queue('broker-a')]);
    vi.mocked(pullMessageAtOffset)
      .mockReturnValueOnce(stalePull.promise)
      .mockReturnValueOnce(currentPull.promise)
      .mockReturnValue(unexpectedPull.promise);
    const user = userEvent.setup();
    render(<QueueBrowserProbe />);

    await user.click(screen.getByRole('button', { name: 'topic-a' }));
    await user.click(screen.getByRole('button', { name: 'load' }));
    await waitFor(() => expect(screen.getByLabelText('queues')).toHaveTextContent('broker-a'));

    await user.click(screen.getByRole('button', { name: 'pull' }));
    await waitFor(() => expect(pullMessageAtOffset).toHaveBeenCalledTimes(1));

    await user.click(screen.getByRole('button', { name: 'load' }));
    await waitFor(() => expect(screen.getByLabelText('queues')).toHaveTextContent('broker-a'));
    await user.click(screen.getByRole('button', { name: 'pull' }));
    await waitFor(() => expect(pullMessageAtOffset).toHaveBeenCalledTimes(2));

    await act(async () => stalePull.resolve(messageRecord('stale-message')));
    await user.click(screen.getByRole('button', { name: 'pull' }));

    expect(pullMessageAtOffset).toHaveBeenCalledTimes(2);
    await act(async () => currentPull.resolve(messageRecord('current-message')));
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

  it('clears the pulling indicator when the queue list reloads mid-pull', async () => {
    const pull = createDeferred<MessageRecord | null>();
    const reloadedQueues = createDeferred<QueueOffset[]>();
    vi.mocked(getQueueOffsets)
      .mockResolvedValueOnce([queue('broker-a')])
      .mockReturnValueOnce(reloadedQueues.promise);
    vi.mocked(pullMessageAtOffset).mockReturnValue(pull.promise);
    const user = userEvent.setup();
    render(<QueueBrowserProbe />);

    await user.click(screen.getByRole('button', { name: 'topic-a' }));
    await user.click(screen.getByRole('button', { name: 'load' }));
    await waitFor(() => expect(screen.getByLabelText('queues')).toHaveTextContent('broker-a'));

    await user.click(screen.getByRole('button', { name: 'pull' }));
    await waitFor(() => expect(screen.getByLabelText('pulling')).toHaveTextContent('true'));

    await user.click(screen.getByRole('button', { name: 'load' }));
    await act(async () => {
      pull.resolve(messageRecord('message-a'));
      reloadedQueues.resolve([queue('broker-a')]);
    });

    expect(screen.getByLabelText('pulling')).toHaveTextContent('false');
  });

  it('leavesMessagePropertiesToTheDetailModalInsteadOfInliningThemTest', async () => {
    vi.mocked(getQueueOffsets).mockResolvedValue([queue('broker-a')]);
    vi.mocked(pullMessageAtOffset).mockResolvedValue({
      ...messageRecord('message-with-properties'),
      properties: { traceId: 'queue-trace-123' },
      propertiesTruncated: true,
    });
    const user = userEvent.setup();
    render(
      <LangProvider>
        <QueueBrowserResultsProbe />
      </LangProvider>,
    );

    await user.click(screen.getByRole('button', { name: 'topic-a' }));
    await user.click(screen.getByRole('button', { name: 'load' }));
    await screen.findByText('broker-a');
    await user.click(screen.getByRole('button', { name: 'pull' }));

    // The pulled message is a row; its properties and body belong to the page-level detail modal
    // reached through onOpenMessage, so inlining them here again would duplicate that surface.
    await screen.findByText('message-with-properties');
    expect(screen.queryByRole('region', { name: '消息属性' })).not.toBeInTheDocument();
    expect(screen.queryByText('queue-trace-123')).not.toBeInTheDocument();
  });
});

describe('QueueBrowser localization', () => {
  it.each([
    ['zh', '选择 Topic', 'broker-a 队列 0 的 Offset', '移除 broker-a-0 的消息记录'],
    ['en', 'Select a topic', 'Offset for broker-a queue 0', 'Remove message entry for broker-a-0'],
  ])(
    'namesQueueControlsAndKeepsKeyboardActionsWorkingTest (%s)',
    async (language, topicLabel, offsetLabel, closeLabel) => {
      window.localStorage.setItem('rocketmq-studio-language', language);
      vi.mocked(getQueueOffsets).mockResolvedValue([queue('broker-a'), queue('broker-b')]);
      vi.mocked(pullMessageAtOffset).mockResolvedValue(messageRecord('accessible-message'));
      const Inner = () => {
        const state = useQueueBrowser('instance-a');
        return (
          <>
            <QueueBrowserControls
              instanceId="instance-a"
              state={state}
              topicOptions={[{ label: 'topic-a', value: 'topic-a' }]}
            />
            <button onClick={() => state.setTopic('topic-a')}>select-test-topic</button>
            <button onClick={() => void state.loadQueues()}>load-test-queues</button>
            <QueueBrowserResults state={state} onOpenMessage={() => undefined} />
          </>
        );
      };
      const user = userEvent.setup();
      render(
        <LangProvider>
          <Inner />
        </LangProvider>,
      );
      expect.soft(screen.getByRole('combobox')).toHaveAccessibleName(topicLabel);
      await user.click(screen.getByRole('button', { name: 'select-test-topic' }));
      await user.click(screen.getByRole('button', { name: 'load-test-queues' }));
      const offset = (await screen.findAllByRole('slider'))[0];
      expect.soft(offset).toHaveAccessibleName(offsetLabel);
      expect(screen.getAllByRole('slider')).toHaveLength(2);
      expect(screen.getAllByRole('slider')[1]).toHaveAccessibleName(
        offsetLabel.replace('broker-a', 'broker-b'),
      );
      offset.focus();
      // rc-slider reads the legacy key code supplied by real browser keyboard events.
      fireEvent.keyDown(offset, { key: 'ArrowLeft', keyCode: 37 });
      expect(offset).toHaveAttribute('aria-valuenow', '1');
      const row = screen.getByText('broker-a').closest('tr')!;
      await user.click(within(row).getByRole('button'));
      expect(await screen.findByText('accessible-message')).toBeInTheDocument();
      expect(pullMessageAtOffset).toHaveBeenCalledWith(
        expect.objectContaining({ brokerName: 'broker-a', queueId: 0, offset: 1 }),
      );
      const close = screen.getByRole('button', { name: closeLabel });
      close.focus();
      await user.keyboard('{Enter}');
      expect(screen.queryByText('accessible-message')).not.toBeInTheDocument();
    },
  );

  // The queue browser hardcoded Chinese strings regardless of the display language (#4941 fixed
  // the composer font size; this panel regressed the other way). The strings now go through the
  // translation table like every other surface.
  it('renders the controls and empty states in the display language', () => {
    window.localStorage.setItem('rocketmq-studio-language', 'en');

    // The hook itself consumes the language context, so it must render inside the provider.
    const Inner = () => {
      const state = useQueueBrowser(undefined);
      return (
        <>
          <QueueBrowserControls
            instanceId={undefined}
            state={state}
            topicOptions={[]}
            topicLoading={false}
          />
          <QueueBrowserResults state={state} onOpenMessage={() => undefined} />
        </>
      );
    };
    render(
      <LangProvider>
        <Inner />
      </LangProvider>,
    );

    // The search icon contributes to the accessible name, so match on the label text.
    expect(screen.getByRole('button', { name: /Load Queues/ })).toBeInTheDocument();
    expect(
      screen.getByText('Select a topic and click "Load Queues" to browse messages by queue'),
    ).toBeInTheDocument();
    expect(screen.getByText('Select a topic')).toBeInTheDocument();
    // The empty queue list renders before the detail pane, so only its hint is visible here;
    // the point is that no hardcoded Chinese remains on the rendered surface.
    expect(screen.queryByText('加载队列')).not.toBeInTheDocument();
    expect(
      screen.queryByText('选择 Topic 并点击「加载队列」，按队列浏览消息'),
    ).not.toBeInTheDocument();
  });

  // The remaining queueBrowser.* keys only surface once a topic has actually been loaded, so they
  // need a probe that drives the hook (and therefore the language context) under the en locale.
  const renderEnglishResults = () => {
    window.localStorage.setItem('rocketmq-studio-language', 'en');
    const Inner = () => {
      const state = useQueueBrowser('instance-a');
      const firstQueue = state.queues[0];
      return (
        <div>
          <button type="button" onClick={() => state.setTopic('topic-a')}>
            topic-a
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
          <QueueBrowserResults state={state} onOpenMessage={() => undefined} />
        </div>
      );
    };
    render(
      <LangProvider>
        <Inner />
      </LangProvider>,
    );
    const user = userEvent.setup();
    const loadTopic = async () => {
      await user.click(screen.getByRole('button', { name: 'topic-a' }));
      await user.click(screen.getByRole('button', { name: 'load' }));
      await screen.findByText('broker-a');
    };
    return { user, loadTopic };
  };

  it('renders the loaded queue table and its summary in the display language', async () => {
    vi.mocked(getQueueOffsets).mockResolvedValue([
      { brokerName: 'broker-a', queueId: 0, minOffset: 0, maxOffset: 3 },
      { brokerName: 'broker-b', queueId: 1, minOffset: 5, maxOffset: 12 },
    ]);
    const { loadTopic } = renderEnglishResults();

    await loadTopic();

    // tableLayout fixed renders a hidden measure row, so header texts appear twice.
    expect(screen.getAllByText('Offset Range').length).toBeGreaterThan(0);
    // The summary interpolates both counters, so assert the substituted numbers: a key missing
    // from the dictionary would render the raw `{queues} queues, {messages} messages in total`.
    expect(screen.getByText('2 queues, 10 messages in total')).toBeInTheDocument();
    expect(
      screen.getByText('Click "View" on the left; message details appear here'),
    ).toBeInTheDocument();
    expect(screen.queryByText('Offset 范围')).not.toBeInTheDocument();
    expect(screen.queryByText(/共 \d+ 个队列/)).not.toBeInTheDocument();
    expect(screen.queryByText('点击左侧「查看」，消息详情将显示在这里')).not.toBeInTheDocument();
  });

  it('announces a queue load failure in the display language', async () => {
    const errorToast = vi.spyOn(message, 'error').mockImplementation(vi.fn());
    // The toast only falls back to the dictionary when the rejection carries no message of its
    // own, which is what a non-Error provider failure looks like.
    vi.mocked(getQueueOffsets).mockRejectedValue('provider rejected the request');
    const { user } = renderEnglishResults();

    await user.click(screen.getByRole('button', { name: 'topic-a' }));
    await user.click(screen.getByRole('button', { name: 'load' }));

    await waitFor(() =>
      expect(errorToast).toHaveBeenCalledWith('Failed to load queue information'),
    );
    expect(errorToast).not.toHaveBeenCalledWith('加载队列信息失败');
    errorToast.mockRestore();
  });

  it('announces a pull failure in the display language', async () => {
    const errorToast = vi.spyOn(message, 'error').mockImplementation(vi.fn());
    vi.mocked(getQueueOffsets).mockResolvedValue([queue('broker-a')]);
    vi.mocked(pullMessageAtOffset).mockRejectedValue('provider rejected the pull');
    const { loadTopic, user } = renderEnglishResults();

    await loadTopic();
    await user.click(screen.getByRole('button', { name: 'pull' }));

    await waitFor(() => expect(errorToast).toHaveBeenCalledWith('Failed to pull the message'));
    expect(errorToast).not.toHaveBeenCalledWith('拉取消息失败');
    errorToast.mockRestore();
  });

  it('renders the pulled message detail in the display language', async () => {
    vi.mocked(getQueueOffsets).mockResolvedValue([queue('broker-a')]);
    vi.mocked(pullMessageAtOffset)
      .mockResolvedValueOnce(null)
      .mockResolvedValueOnce({ ...messageRecord('sized-message'), size: 1024 });
    const { loadTopic, user } = renderEnglishResults();

    await loadTopic();

    await user.click(screen.getByRole('button', { name: 'pull' }));
    // An offset with nothing behind it renders the placeholder instead of an empty detail card.
    await screen.findByText('No message at this offset');
    expect(screen.queryByText('该 offset 处无消息')).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'pull' }));
    await screen.findByText('sized-message');
    // The size cell goes through the shared byte formatter, so it reads as a scaled unit rather
    // than a raw byte count.
    expect(screen.getByText('1.0 KB')).toBeInTheDocument();
    expect(screen.queryByText('1024 bytes')).not.toBeInTheDocument();
  });

  it('listsPulledMessagesAsSingleColumnRowsAndDelegatesDetailToTheCallbackTest', async () => {
    const onOpenMessage = vi.fn();
    vi.mocked(getQueueOffsets).mockResolvedValue([queue('broker-a')]);
    vi.mocked(pullMessageAtOffset).mockResolvedValue(messageRecord('row-message'));

    const Inner = () => {
      const state = useQueueBrowser('instance-a');
      const firstQueue = state.queues[0];
      return (
        <div>
          <button type="button" onClick={() => state.setTopic('topic-a')}>
            topic-a
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
          <QueueBrowserResults state={state} onOpenMessage={onOpenMessage} />
        </div>
      );
    };
    render(
      <LangProvider>
        <Inner />
      </LangProvider>,
    );
    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: 'topic-a' }));
    await user.click(screen.getByRole('button', { name: 'load' }));
    await screen.findByText('broker-a');
    await user.click(screen.getByRole('button', { name: 'pull' }));
    await screen.findByText('row-message');

    // One row per pulled message in ONE table — the old 2-up card grid is gone.
    const entries = screen.getByTestId('queue-browser-entries');
    expect(within(entries).getAllByRole('row')).toHaveLength(2); // header + the single entry
    expect(document.querySelector('[style*="1fr 1fr"]')).toBeNull();

    // Two 查看 buttons: the queue row's pull action and the entry row's detail action.
    await user.click(within(entries).getByRole('button', { name: '查看' }));
    expect(onOpenMessage).toHaveBeenCalledTimes(1);
    expect(onOpenMessage).toHaveBeenCalledWith(expect.objectContaining({ msgId: 'row-message' }));
  });
});
