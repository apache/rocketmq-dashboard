/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 */
import { beforeAll, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from 'antd';
import WelcomeStarters from '../WelcomeStarters';
import { LangProvider } from '../../../../i18n/LangContext';

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

function renderStarters(onPick: ReturnType<typeof vi.fn>) {
  return render(
    <App>
      <LangProvider>
        <WelcomeStarters onPick={onPick} />
      </LangProvider>
    </App>,
  );
}

describe('WelcomeStarters', () => {
  it('renders the four scenario cards, each bound to its chat mode', () => {
    renderStarters(vi.fn());

    expect(
      screen.getByTestId('ai-welcome-starter-diagnose-ai.welcome.starter.health.title'),
    ).toBeInTheDocument();
    expect(
      screen.getByTestId('ai-welcome-starter-query-ai.welcome.starter.inventory.title'),
    ).toBeInTheDocument();
    expect(
      screen.getByTestId('ai-welcome-starter-diagnose-ai.welcome.starter.lag.title'),
    ).toBeInTheDocument();
    expect(
      screen.getByTestId('ai-welcome-starter-chat-ai.welcome.starter.learn.title'),
    ).toBeInTheDocument();
  });

  it('hands the caller the translated prompt and the mode, not a send', async () => {
    const user = userEvent.setup();
    const onPick = vi.fn();
    renderStarters(onPick);

    await user.click(
      screen.getByTestId('ai-welcome-starter-diagnose-ai.welcome.starter.health.title'),
    );

    expect(onPick).toHaveBeenCalledTimes(1);
    const [prompt, mode] = onPick.mock.calls[0];
    expect(prompt).toBe(
      '请对当前绑定的 RocketMQ 实例做一次健康巡检：Broker 状态、磁盘水位、TPS、Topic 数量与告警情况，最后给出结论。',
    );
    expect(mode).toBe('diagnose');
  });

  it('hands the inventory card the query mode', async () => {
    const user = userEvent.setup();
    const onPick = vi.fn();
    renderStarters(onPick);

    await user.click(
      screen.getByTestId('ai-welcome-starter-query-ai.welcome.starter.inventory.title'),
    );

    expect(onPick.mock.calls[0][1]).toBe('query');
  });

  it('renders each card with its title and one-line description', () => {
    renderStarters(vi.fn());

    expect(screen.getByText('资源盘点')).toBeInTheDocument();
    // The description is ellipsised, not wrapped, so the two-column grid stays even.
    const desc = screen.getByTitle('列出 Topic 与订阅组，找出无流量或异常资源');
    expect(desc.style.whiteSpace).toBe('nowrap');
    expect(desc.style.textOverflow).toBe('ellipsis');
  });

  it('renders the welcome title and subtitle above the cards', () => {
    renderStarters(vi.fn());

    expect(screen.getByText('RocketMQ Studio AI 助手')).toBeInTheDocument();
  });
});
