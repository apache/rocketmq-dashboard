/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 */
import { beforeAll, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { App } from 'antd';
import NoticeBlock from '../NoticeBlock';
import { LangProvider } from '../../../../../i18n/LangContext';
import type { NoticeBlock as NoticeBlockData } from '../../../render/blocks';

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

function renderBlock(block: NoticeBlockData) {
  return render(
    <App>
      <LangProvider>
        <NoticeBlock block={block} />
      </LangProvider>
    </App>,
  );
}

describe('NoticeBlock', () => {
  it('renders an info notice as the message alone, without a title', () => {
    const { container } = renderBlock({
      kind: 'notice',
      level: 'info',
      message: 'unhandled agent message type: tool_progress',
    });

    expect(screen.getByText('unhandled agent message type: tool_progress')).toBeInTheDocument();
    expect(screen.queryByText('注意')).toBeNull();
    expect(container.querySelector('.ant-alert')).toBeNull();
  });

  it('gives a warn notice a scannable title so it can be spotted while reading', () => {
    renderBlock({
      kind: 'notice',
      level: 'warn',
      message: 'rmqctl 不可用，本次会话已禁用 RocketMQ 工具',
    });

    expect(screen.getByText('注意')).toBeInTheDocument();
    expect(screen.getByText('rmqctl 不可用，本次会话已禁用 RocketMQ 工具')).toBeInTheDocument();
  });

  /**
   * A notice is transcript furniture, not a semantic state: it must keep the neutral InfoBanner
   * treatment even at warn level — the project reserves coloured Alerts for real states, and a
   * genuine failure arrives as an error block rendered by ErrorBlock.
   */
  it('keeps the neutral banner treatment even for warn notices', () => {
    const { container } = renderBlock({
      kind: 'notice',
      level: 'warn',
      message: 'rmqctl 不可用，本次会话已禁用 RocketMQ 工具',
    });

    expect(container.querySelector('.ant-alert-warning')).toBeNull();
    expect(container.querySelector('.ant-alert')).toBeNull();
    expect(screen.getByTestId('ai-notice-block')).toBeInTheDocument();
  });
});
