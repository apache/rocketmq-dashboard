/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 */
import { beforeAll, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { App } from 'antd';
import RuntimeNotices from '../RuntimeNotices';
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

function renderNotices(props: Partial<Parameters<typeof RuntimeNotices>[0]> = {}) {
  return render(
    <App>
      <LangProvider>
        <RuntimeNotices mock={false} rmqctlAvailable runError="" {...props} />
      </LangProvider>
    </App>,
  );
}

describe('RuntimeNotices', () => {
  it('shows the mock-mode info alert and suppresses the rmqctl banner even when rmqctl is missing', () => {
    const { container } = renderNotices({ mock: true, rmqctlAvailable: false });

    expect(screen.getByTestId('ai-mock-disabled')).toBeInTheDocument();
    expect(container.querySelector('.ant-alert-info')).not.toBeNull();
    expect(screen.queryByTestId('ai-rmqctl-unavailable-banner')).toBeNull();
  });

  it('shows the missing-rmqctl notice as a neutral banner in real mode', () => {
    const { container } = renderNotices({ rmqctlAvailable: false });

    expect(screen.getByTestId('ai-rmqctl-unavailable-banner')).toBeInTheDocument();
    // Nothing is wrong, a capability is simply absent: never a semantic coloured alert.
    expect(container.querySelector('.ant-alert')).toBeNull();
  });

  it('shows neither runtime notice when rmqctl is available in real mode', () => {
    renderNotices();

    expect(screen.queryByTestId('ai-mock-disabled')).toBeNull();
    expect(screen.queryByTestId('ai-rmqctl-unavailable-banner')).toBeNull();
  });

  it('renders a run error with the semantic error treatment', () => {
    const { container } = renderNotices({ runError: 'agent crashed' });

    expect(screen.getByTestId('ai-run-error').textContent).toContain('agent crashed');
    expect(container.querySelector('.ant-alert-error')).not.toBeNull();
  });

  it('renders no error alert while nothing failed', () => {
    const { container } = renderNotices({ runError: '' });

    expect(screen.queryByTestId('ai-run-error')).toBeNull();
    expect(container.querySelector('.ant-alert-error')).toBeNull();
  });

  it('renders nothing at all when the runtime is healthy and no run failed', () => {
    const { container } = renderNotices();

    expect(container.querySelector('[data-testid]')).toBeNull();
    expect(container.querySelector('.ant-alert')).toBeNull();
    expect(container.textContent).toBe('');
  });
});
