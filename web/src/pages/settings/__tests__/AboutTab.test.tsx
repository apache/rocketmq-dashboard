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

import { render, screen } from '@testing-library/react';
import { beforeAll, describe, expect, it, vi } from 'vitest';
import { AboutTab } from '../AboutTab';

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
  it('renders the injected build metadata and build-year copyright', () => {
    render(<AboutTab />);

    expect(screen.getByText(__BUILD_COMMIT__)).toBeInTheDocument();
    expect(screen.getByText(__BUILD_TIME__)).toBeInTheDocument();
    expect(
      screen.getByText(
        `Copyright © ${__BUILD_TIME__.slice(0, 4)} Apache Software Foundation. Licensed under the Apache License, Version 2.0.`,
      ),
    ).toBeInTheDocument();
    expect(screen.queryByText('2024-01-15 14:30:00')).not.toBeInTheDocument();
  });
});
