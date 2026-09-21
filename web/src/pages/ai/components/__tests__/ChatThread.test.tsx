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

import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { LangProvider } from '../../../../i18n/LangContext';
import type { Bubble } from '../../render/blocks';
import { appendText } from '../../render/blocks';
import ChatThread from '../ChatThread';

/**
 * Conditional scrolling.
 *
 * The page this replaces scrolled to the bottom on EVERY messages change. That was harmless while a
 * stop actually stopped, because the transcript only grew while the operator was watching it. A run
 * now keeps generating while the reader scrolls up to re-read a tool result, so an unconditional
 * scroll would yank the viewport back on every token — the reason `atBottom` is tracked at all.
 *
 * jsdom reports every scroll metric as 0, which would make the thread permanently "at the bottom",
 * so the metrics are defined per test to place the reader where the case needs them.
 */

const scrollIntoView = vi.fn();

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
  Element.prototype.scrollIntoView = scrollIntoView;
});

function userBubble(text: string): Bubble {
  return { role: 'user', blocks: appendText([], text), createdAt: '2026-09-20T02:12:31' };
}

function assistantBubble(text: string): Bubble {
  return { role: 'assistant', blocks: appendText([], text) };
}

/** Place the reader inside the thread: `distance` is how far from the bottom they are. */
function placeReader(distance: number) {
  const container = screen.getByTestId('ai-thread-scroll');
  Object.defineProperty(container, 'scrollHeight', { configurable: true, value: 1000 });
  Object.defineProperty(container, 'clientHeight', { configurable: true, value: 200 });
  Object.defineProperty(container, 'scrollTop', {
    configurable: true,
    writable: true,
    value: 1000 - 200 - distance,
  });
  return container;
}

function renderThread(props: { bubbles?: Bubble[]; streaming?: boolean; resetKey?: number }) {
  return render(
    <LangProvider>
      <ChatThread
        bubbles={props.bubbles ?? []}
        streaming={props.streaming}
        resetKey={props.resetKey}
      />
    </LangProvider>,
  );
}

describe('ChatThread', () => {
  beforeEach(() => {
    scrollIntoView.mockClear();
    localStorage.clear();
  });

  it('followsTheTranscriptWhileTheReaderIsAtTheBottomTest', () => {
    const { rerender } = renderThread({ bubbles: [userBubble('检查集群状态')] });
    scrollIntoView.mockClear();

    placeReader(0);
    rerender(
      <LangProvider>
        <ChatThread bubbles={[userBubble('检查集群状态'), assistantBubble('集群正常')]} />
      </LangProvider>,
    );

    expect(scrollIntoView).toHaveBeenCalled();
    expect(screen.queryByTestId('ai-thread-jump-to-latest')).not.toBeInTheDocument();
  });

  it('stopsFollowingAndOffersAJumpOnceTheReaderScrollsUpTest', () => {
    const { rerender } = renderThread({ bubbles: [userBubble('检查集群状态')] });
    scrollIntoView.mockClear();

    placeReader(600);
    fireEvent.scroll(screen.getByTestId('ai-thread-scroll'));
    expect(screen.getByTestId('ai-thread-jump-to-latest')).toHaveTextContent('回到最新');

    // New content arrives while the reader is away: the viewport must not move.
    rerender(
      <LangProvider>
        <ChatThread bubbles={[userBubble('检查集群状态'), assistantBubble('集群正常')]} />
      </LangProvider>,
    );
    expect(scrollIntoView).not.toHaveBeenCalled();
    expect(screen.getByTestId('ai-thread-unread')).toHaveTextContent('1');
  });

  it('countsMessagesNotTokensWhileTheReaderIsAwayTest', () => {
    const { rerender } = renderThread({ bubbles: [userBubble('检查集群状态')] });
    placeReader(600);
    fireEvent.scroll(screen.getByTestId('ai-thread-scroll'));
    expect(screen.queryByTestId('ai-thread-unread')).not.toBeInTheDocument();

    // The answer starts: one new message.
    rerender(
      <LangProvider>
        <ChatThread
          bubbles={[userBubble('检查集群状态')]}
          liveBlocks={appendText([], '部分')}
          streaming
        />
      </LangProvider>,
    );
    expect(screen.getByTestId('ai-thread-unread')).toHaveTextContent('1');

    // More tokens of the SAME answer: the badge must not inflate, or a long answer would claim
    // hundreds of unread messages.
    rerender(
      <LangProvider>
        <ChatThread
          bubbles={[userBubble('检查集群状态')]}
          liveBlocks={appendText([], '部分回答，还在继续')}
          streaming
        />
      </LangProvider>,
    );
    expect(screen.getByTestId('ai-thread-unread')).toHaveTextContent('1');

    // Another turn is persisted: a genuinely new message.
    rerender(
      <LangProvider>
        <ChatThread
          bubbles={[userBubble('检查集群状态'), assistantBubble('集群正常')]}
          liveBlocks={appendText([], '部分回答，还在继续')}
          streaming
        />
      </LangProvider>,
    );
    expect(screen.getByTestId('ai-thread-unread')).toHaveTextContent('2');
  });

  it('jumpsBackToTheLatestAndClearsTheBadgeTest', async () => {
    const user = userEvent.setup();
    renderThread({ bubbles: [userBubble('检查集群状态')] });
    placeReader(600);
    fireEvent.scroll(screen.getByTestId('ai-thread-scroll'));
    scrollIntoView.mockClear();

    await user.click(screen.getByTestId('ai-thread-jump-to-latest'));

    expect(scrollIntoView).toHaveBeenCalled();
    expect(screen.queryByTestId('ai-thread-jump-to-latest')).not.toBeInTheDocument();
  });

  it('hidesTheJumpPillWhenTheReaderScrollsBackDownTest', () => {
    renderThread({ bubbles: [userBubble('检查集群状态')] });
    placeReader(600);
    fireEvent.scroll(screen.getByTestId('ai-thread-scroll'));
    expect(screen.getByTestId('ai-thread-jump-to-latest')).toBeInTheDocument();

    placeReader(10);
    fireEvent.scroll(screen.getByTestId('ai-thread-scroll'));

    expect(screen.queryByTestId('ai-thread-jump-to-latest')).not.toBeInTheDocument();
  });

  it('resetsTheUnreadCountWhenTheConversationChangesTest', () => {
    const { rerender } = renderThread({
      bubbles: [userBubble('检查集群状态')],
      resetKey: 7,
    });
    placeReader(600);
    fireEvent.scroll(screen.getByTestId('ai-thread-scroll'));

    rerender(
      <LangProvider>
        <ChatThread
          bubbles={[userBubble('检查集群状态'), assistantBubble('集群正常')]}
          resetKey={7}
        />
      </LangProvider>,
    );
    expect(screen.getByTestId('ai-thread-unread')).toHaveTextContent('1');

    // Another conversation: its badge must not inherit the previous one's count.
    rerender(
      <LangProvider>
        <ChatThread bubbles={[userBubble('另一个会话')]} resetKey={8} />
      </LangProvider>,
    );
    expect(screen.queryByTestId('ai-thread-jump-to-latest')).not.toBeInTheDocument();
  });

  it('doesNotInflateUnreadWhenStreamingFailsAndLiveBlocksDisappearTest', () => {
    const { rerender } = renderThread({ bubbles: [userBubble('检查集群状态')] });
    placeReader(600);
    fireEvent.scroll(screen.getByTestId('ai-thread-scroll'));

    // The answer starts streaming: one new message arrives.
    rerender(
      <LangProvider>
        <ChatThread
          bubbles={[userBubble('检查集群状态')]}
          liveBlocks={appendText([], '部分')}
          streaming
        />
      </LangProvider>,
    );
    expect(screen.getByTestId('ai-thread-unread')).toHaveTextContent('1');

    // Streaming fails / is aborted and the live bubble disappears without being persisted.
    // The unread count must not increase when a bubble is removed.
    rerender(
      <LangProvider>
        <ChatThread bubbles={[userBubble('检查集群状态')]} />
      </LangProvider>,
    );
    expect(screen.getByTestId('ai-thread-unread')).toHaveTextContent('1');
  });

  it('rendersTheEmptySlotWhenThereIsNoTranscriptTest', () => {
    render(
      <LangProvider>
        <ChatThread bubbles={[]} empty={<span>开始新的对话</span>} />
      </LangProvider>,
    );

    expect(screen.getByText('开始新的对话')).toBeInTheDocument();
  });
});
