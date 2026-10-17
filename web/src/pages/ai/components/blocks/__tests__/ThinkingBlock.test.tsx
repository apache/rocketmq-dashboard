/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 */
import { describe, expect, it, vi, beforeAll } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from 'antd';
import ThinkingBlock from '../ThinkingBlock';
import { LangProvider } from '../../../../../i18n/LangContext';
import type { ThinkingBlock as ThinkingBlockData } from '../../../render/blocks';

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

function wrap(ui: React.ReactElement) {
  return (
    <App>
      <LangProvider>{ui}</LangProvider>
    </App>
  );
}

const modelReasoning: ThinkingBlockData = {
  kind: 'thinking',
  text: '推理'.repeat(4),
  source: 'model',
};
const enhanceRewrite: ThinkingBlockData = { kind: 'thinking', text: '改写', source: 'enhance' };

function detailsOf(): HTMLDetailsElement {
  return screen.getByTestId('ai-thinking-block') as HTMLDetailsElement;
}

describe('ThinkingBlock', () => {
  it('labels model reasoning and the prompt-enhancement rewrite differently', () => {
    const { rerender } = render(wrap(<ThinkingBlock block={modelReasoning} />));
    expect(screen.getByTestId('ai-thinking-summary').textContent).toContain('思考过程');
    expect(screen.getByTestId('ai-thinking-block').getAttribute('data-source')).toBe('model');

    rerender(wrap(<ThinkingBlock block={enhanceRewrite} />));
    expect(screen.getByTestId('ai-thinking-summary').textContent).toContain('Prompt 增强改写');
    expect(screen.getByTestId('ai-thinking-block').getAttribute('data-source')).toBe('enhance');
  });

  it('shows the character count of the reasoning in the summary', () => {
    render(wrap(<ThinkingBlock block={modelReasoning} />));
    expect(screen.getByTestId('ai-thinking-summary').textContent).toContain(
      `（${'推理'.repeat(4).length} 字）`,
    );
  });

  it('is collapsed when nothing is streaming', () => {
    render(wrap(<ThinkingBlock block={modelReasoning} />));
    expect(detailsOf().open).toBe(false);
  });

  it('opens only the last thinking block of the streaming run', () => {
    render(wrap(<ThinkingBlock block={modelReasoning} streaming latest={false} />));
    expect(detailsOf().open).toBe(false);
  });

  it('auto-opens the last thinking block while its run is streaming', () => {
    render(wrap(<ThinkingBlock block={modelReasoning} streaming latest />));
    expect(detailsOf().open).toBe(true);
  });

  it('respects a manual toggle while the streaming context is unchanged', async () => {
    const user = userEvent.setup();
    render(wrap(<ThinkingBlock block={modelReasoning} streaming latest />));
    expect(detailsOf().open).toBe(true);

    await user.click(screen.getByTestId('ai-thinking-summary'));
    expect(detailsOf().open).toBe(false);
  });

  /**
   * The auto-open state is derived, not effect-synced: the transition out of streaming discards
   * any stored click, so a stale double-click (close, reopen) must not defeat collapse-on-done.
   */
  it('collapses on done even after the user closed and reopened it mid-stream', async () => {
    const user = userEvent.setup();
    const view = render(wrap(<ThinkingBlock block={modelReasoning} streaming latest />));
    expect(detailsOf().open).toBe(true);

    await user.click(screen.getByTestId('ai-thinking-summary'));
    expect(detailsOf().open).toBe(false);
    await user.click(screen.getByTestId('ai-thinking-summary'));
    expect(detailsOf().open).toBe(true);

    view.rerender(wrap(<ThinkingBlock block={modelReasoning} />));
    expect(detailsOf().open).toBe(false);
  });

  it('renders the reasoning text in the body', () => {
    render(wrap(<ThinkingBlock block={modelReasoning} streaming latest />));
    expect(screen.getByTestId('ai-thinking-body').textContent).toBe('推理'.repeat(4));
  });
});
