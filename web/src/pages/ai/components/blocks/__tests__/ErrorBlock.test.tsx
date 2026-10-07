/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 */
import { beforeAll, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { App } from 'antd';
import ErrorBlock from '../ErrorBlock';
import { LangProvider } from '../../../../../i18n/LangContext';
import type { ErrorBlock as ErrorBlockData } from '../../../render/blocks';

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

function renderBlock(block: ErrorBlockData) {
  return render(
    <App>
      <LangProvider>
        <ErrorBlock block={block} />
      </LangProvider>
    </App>,
  );
}

describe('ErrorBlock', () => {
  it('keeps the error code visible next to the message', () => {
    renderBlock({
      kind: 'error',
      code: 'llm.provider.error_max_turns',
      message: 'run hit the turn cap',
    });

    expect(screen.getByText('run hit the turn cap')).toBeInTheDocument();
    expect(screen.getByText('llm.provider.error_max_turns')).toBeInTheDocument();
  });

  it('turns the server hint into the alert description', () => {
    renderBlock({
      kind: 'error',
      code: 'llm.stream.unexpected_content_type',
      message: 'stream failed',
      hint: 'check the gateway content negotiation',
    });

    expect(screen.getByText('check the gateway content negotiation')).toBeInTheDocument();
  });

  it('renders no description when the server sent no hint', () => {
    const { container } = renderBlock({
      kind: 'error',
      code: 'ai.run.busy',
      message: 'run is busy',
    });

    expect(container.querySelector('.ant-alert-description')).toBeNull();
  });

  it('uses the semantic error treatment, not a neutral banner', () => {
    const { container } = renderBlock({
      kind: 'error',
      code: 'ai.run.busy',
      message: 'run is busy',
    });

    expect(container.querySelector('.ant-alert-error')).not.toBeNull();
  });
});
