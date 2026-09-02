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

import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { LangProvider } from '../../../i18n/LangContext';
import { AboutTab } from '../AboutTab';

Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: (query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: () => {},
    removeListener: () => {},
    addEventListener: () => {},
    removeEventListener: () => {},
    dispatchEvent: () => false,
  }),
});

vi.mock('../../../i18n/languagePreference', () => ({
  getInitialLanguage: () => 'zh' as const,
  persistLanguage: vi.fn(),
}));

describe('AboutTab', () => {
  it('renders Chinese labels by default', () => {
    render(
      <LangProvider>
        <AboutTab />
      </LangProvider>,
    );
    expect(screen.getByText('版本')).toBeInTheDocument();
    expect(screen.getByText('构建时间')).toBeInTheDocument();
    expect(screen.getByText('相关链接')).toBeInTheDocument();
  });

  it('keeps proper nouns and version values intact', () => {
    render(
      <LangProvider>
        <AboutTab />
      </LangProvider>,
    );
    expect(screen.getByText('0.1.0')).toBeInTheDocument();
    expect(screen.getByText('React 18 + Ant Design 5')).toBeInTheDocument();
    expect(screen.getByText('Spring Boot 3 + RocketMQ MCP Server')).toBeInTheDocument();
    expect(screen.getByText('Apache 2.0')).toBeInTheDocument();
  });
});
