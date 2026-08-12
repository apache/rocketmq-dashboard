/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */

import { beforeAll, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { AboutTab } from '../index';

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

describe('AboutTab', () => {
  it('shows metadata for the current frontend build', () => {
    render(<AboutTab />);

    expect(screen.getByText(__STUDIO_VERSION__)).toBeInTheDocument();
    expect(screen.getByText(__STUDIO_BUILD_TIME__)).toBeInTheDocument();
    expect(screen.queryByText('2024-01-15 14:30:00')).not.toBeInTheDocument();
  });
});
