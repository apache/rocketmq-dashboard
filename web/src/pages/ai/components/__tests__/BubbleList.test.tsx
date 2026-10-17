/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 */
import { beforeAll, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { App } from 'antd';
import BubbleList from '../BubbleList';
import type { Bubble, TextBlock, ThinkingBlock } from '../../render/blocks';

// BubbleList is deliberately dumb: it forwards to UserBubble/AssistantBubble, so their stand-ins
// record exactly what they were called with.
vi.mock('../UserBubble', () => ({
  default: ({ text, createdAt }: { text: string; createdAt?: string }) => (
    <div data-testid="bubble-user" data-text={text} data-created={createdAt ?? 'none'} />
  ),
}));

vi.mock('../AssistantBubble', () => ({
  default: ({
    bubble,
    streaming,
    tokensPerSecond,
  }: {
    bubble: Bubble;
    streaming?: boolean;
    tokensPerSecond?: number | null;
  }) => (
    <div
      data-testid="bubble-assistant"
      data-streaming={streaming ? 'true' : 'false'}
      data-tps={tokensPerSecond ?? 'none'}
      data-kinds={bubble.blocks.map((block) => block.kind).join(',')}
    />
  ),
}));

beforeAll(() => {
  Object.defineProperty(window, 'matchMedia', {
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

const text = (value: string): TextBlock => ({ kind: 'text', text: value });
const thinking = (value: string): ThinkingBlock => ({
  kind: 'thinking',
  text: value,
  source: 'model',
});

function renderList(props: Partial<Parameters<typeof BubbleList>[0]> = {}) {
  return render(
    <App>
      <BubbleList bubbles={[]} {...props} />
    </App>,
  );
}

describe('BubbleList', () => {
  it('renders the empty state when the transcript, the live run and the pending question are all quiet', () => {
    renderList({ empty: <p data-testid="transcript-empty">nothing here</p> });

    expect(screen.getByTestId('transcript-empty')).toBeInTheDocument();
    expect(screen.queryByTestId('bubble-user')).toBeNull();
    expect(screen.queryByTestId('bubble-assistant')).toBeNull();
  });

  it('renders persisted bubbles, then the pending question, then the live answer', () => {
    renderList({
      bubbles: [
        { role: 'user', blocks: [text('first question')] },
        { role: 'assistant', blocks: [text('first answer')] },
      ],
      pendingUserText: 'second question',
      liveBlocks: [text('partial answer')],
      streaming: true,
    });

    const users = screen.getAllByTestId('bubble-user');
    const assistants = screen.getAllByTestId('bubble-assistant');
    expect(users).toHaveLength(2);
    expect(assistants).toHaveLength(2);
    expect(users[1].getAttribute('data-text')).toBe('second question');
    // The live bubble is the trailing one, after everything persisted.
    expect(assistants[1].getAttribute('data-kinds')).toBe('text');
    expect(assistants[1].getAttribute('data-streaming')).toBe('true');
  });

  it('joins only the text blocks of a persisted user bubble and forwards its timestamp', () => {
    renderList({
      bubbles: [
        {
          role: 'user',
          blocks: [text('line one'), thinking('reasoning'), text('line two')],
          createdAt: '2026-10-08T05:30:00',
        },
      ],
    });

    const user = screen.getByTestId('bubble-user');
    expect(user.getAttribute('data-text')).toBe('line one\nline two');
    expect(user.getAttribute('data-created')).toBe('2026-10-08T05:30:00');
  });

  it('draws the optimistic question without a timestamp', () => {
    renderList({ pendingUserText: 'why?' });

    const user = screen.getByTestId('bubble-user');
    expect(user.getAttribute('data-text')).toBe('why?');
    expect(user.getAttribute('data-created')).toBe('none');
  });

  it('ignores a blank pending question', () => {
    renderList({ pendingUserText: '   ' });

    expect(screen.queryByTestId('bubble-user')).toBeNull();
  });

  it('shows the live bubble as soon as the run streams, even before its first block', () => {
    renderList({ liveBlocks: [], streaming: true });

    const live = screen.getByTestId('bubble-assistant');
    expect(live.getAttribute('data-streaming')).toBe('true');
    expect(live.getAttribute('data-kinds')).toBe('');
  });

  it('shows no live bubble once the run finished with nothing live to show', () => {
    renderList({ liveBlocks: [], streaming: false });

    expect(screen.queryByTestId('bubble-assistant')).toBeNull();
  });

  it('gives the last run speed to the newest persisted assistant bubble only while nothing streams', () => {
    const bubbles: Bubble[] = [
      { role: 'assistant', blocks: [text('old answer')] },
      { role: 'user', blocks: [text('question')] },
      { role: 'assistant', blocks: [text('new answer')] },
    ];

    const { rerender } = render(
      <App>
        <BubbleList bubbles={bubbles} lastRunTokensPerSecond={42} />
      </App>,
    );
    const finished = screen.getAllByTestId('bubble-assistant');
    expect(finished[0].getAttribute('data-tps')).toBe('none');
    expect(finished[1].getAttribute('data-tps')).toBe('42');

    rerender(
      <App>
        <BubbleList bubbles={bubbles} lastRunTokensPerSecond={42} streaming liveBlocks={[]} />
      </App>,
    );
    const streaming = screen.getAllByTestId('bubble-assistant');
    // While a newer run streams, the persisted bubbles show no stale speed.
    expect(streaming[0].getAttribute('data-tps')).toBe('none');
    expect(streaming[1].getAttribute('data-tps')).toBe('none');
  });

  it('keeps a bubble speed the run itself reported over the last-run estimate', () => {
    renderList({
      bubbles: [{ role: 'assistant', blocks: [text('answer')], tokensPerSecond: 7 }],
      lastRunTokensPerSecond: 42,
    });

    expect(screen.getByTestId('bubble-assistant').getAttribute('data-tps')).toBe('7');
  });

  it('renders nothing at all when everything is quiet and no empty state is given', () => {
    const { container } = renderList();

    expect(container.querySelector('[data-testid]')).toBeNull();
    expect(container.textContent).toBe('');
  });
});
