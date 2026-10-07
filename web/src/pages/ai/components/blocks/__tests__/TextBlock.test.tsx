/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 */
import { beforeAll, describe, expect, it, vi } from 'vitest';
import { render } from '@testing-library/react';
import { App } from 'antd';
import TextBlock from '../TextBlock';
import { LangProvider } from '../../../../../i18n/LangContext';
import type { TextBlock as TextBlockData } from '../../../render/blocks';

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

function renderBlock(text: string) {
  const block: TextBlockData = { kind: 'text', text };
  const { container } = render(
    <App>
      <LangProvider>
        <TextBlock block={block} />
      </LangProvider>
    </App>,
  );
  return container;
}

describe('TextBlock', () => {
  it('renders assistant prose as markdown', () => {
    const container = renderBlock('the answer is **42**');

    expect(container.querySelector('strong')?.textContent).toBe('42');
  });

  /**
   * CommonMark requires a space after an ATX heading marker and after a list bullet, and models -
   * especially when answering in Chinese - skip both. Without the repair the render is not
   * slightly-worse but absent: '##结论' stays a literal paragraph and '-第一项' stays a literal line.
   */
  it('repairs model markdown before rendering it', () => {
    const container = renderBlock('##结论\n\n-第一项\n-第二项');

    expect(container.querySelector('h2')?.textContent).toBe('结论');
    expect(container.querySelectorAll('li')).toHaveLength(2);
    expect(container.querySelectorAll('li')[0].textContent).toBe('第一项');
  });

  it('renders GitHub-flavoured markdown tables', () => {
    const container = renderBlock('| a | b |\n| --- | --- |\n| 1 | 2 |');

    expect(container.querySelector('table')).not.toBeNull();
    expect(container.querySelectorAll('td')).toHaveLength(2);
  });

  it('opts into the ai-markdown typographic contract', () => {
    const container = renderBlock('plain prose');

    const host = container.querySelector('[data-testid="ai-text-block"]');
    expect(host).not.toBeNull();
    expect(host?.classList.contains('ai-markdown')).toBe(true);
  });
});
