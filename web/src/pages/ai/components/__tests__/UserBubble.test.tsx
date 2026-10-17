/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 */
import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { App } from 'antd';
import UserBubble from '../UserBubble';
import useAuthStore from '../../../../stores/authStore';
import { formatUtcDateTime } from '../../../../utils/format';

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

function renderBubble(props: { text: string; createdAt?: string }) {
  return render(
    <App>
      <UserBubble {...props} />
    </App>,
  );
}

describe('UserBubble', () => {
  beforeEach(() => {
    useAuthStore.setState({ user: null, userId: null, admin: null });
  });

  /**
   * Plain text on purpose: what was sent is what the run was admitted with, so a prompt containing
   * `#` or `*` must render literally — re-interpreting it as Markdown would show the user
   * something they never typed.
   */
  it('renders the prompt as plain text, never as markdown', () => {
    const { container } = renderBubble({ text: '# Heading\n**not bold**\n- not a list' });

    expect(container.querySelector('h1')).toBeNull();
    expect(container.querySelector('strong')).toBeNull();
    expect(container.querySelector('ul')).toBeNull();
    const bubble = container.querySelector('.ai-user-bubble') as HTMLElement;
    expect(bubble.textContent).toContain('# Heading');
    expect(bubble.textContent).toContain('**not bold**');
    expect(bubble.textContent).toContain('- not a list');
  });

  it('formats the persisted creation time through the shared UTC helper', () => {
    const createdAt = '2026-10-08T05:30:00';
    renderBubble({ text: 'hello', createdAt });

    expect(screen.getByText(formatUtcDateTime(createdAt))).toBeInTheDocument();
  });

  it('renders no timestamp when the entry carries none', () => {
    const { container } = renderBubble({ text: 'hello' });

    expect(container.textContent).not.toContain('-');
    expect(screen.getByText('hello')).toBeInTheDocument();
  });

  it('shows the operator initial from the session', () => {
    useAuthStore.setState({ user: 'alice', userId: 1, admin: false });
    renderBubble({ text: 'hello' });

    const avatar = screen.getByTestId('ai-bubble-user-avatar');
    expect(avatar.textContent).toBe('A');
    expect(avatar.getAttribute('title')).toBe('alice');
  });

  it('falls back to U and no title when nobody is logged in', () => {
    renderBubble({ text: 'hello' });

    const avatar = screen.getByTestId('ai-bubble-user-avatar');
    expect(avatar.textContent).toBe('U');
    expect(avatar.getAttribute('title')).toBeNull();
  });

  it('uppercases the initial of a lowercase username', () => {
    useAuthStore.setState({ user: 'bob', userId: 2, admin: false });
    renderBubble({ text: 'hello' });

    expect(screen.getByTestId('ai-bubble-user-avatar').textContent).toBe('B');
  });
});
