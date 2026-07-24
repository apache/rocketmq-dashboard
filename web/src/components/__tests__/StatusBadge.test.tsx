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

import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import StatusBadge from '../StatusBadge';
import { LangProvider } from '../../i18n/LangContext';

const renderWithLang = (ui: React.ReactElement) => render(<LangProvider>{ui}</LangProvider>);

describe('StatusBadge', () => {
  it('renders the translated label for a known status in Chinese', () => {
    renderWithLang(<StatusBadge status="healthy" />);
    // theme.healthy in zh is '运行中'
    expect(screen.getByText('运行中')).toBeInTheDocument();
  });

  it('renders the translated label for warning status', () => {
    renderWithLang(<StatusBadge status="warning" />);
    // theme.warning in zh is '告警'
    expect(screen.getByText('告警')).toBeInTheDocument();
  });

  it('renders the translated label for error status', () => {
    renderWithLang(<StatusBadge status="error" />);
    // theme.error in zh is '异常'
    expect(screen.getByText('异常')).toBeInTheDocument();
  });

  it('renders the translated label for offline status', () => {
    renderWithLang(<StatusBadge status="offline" />);
    expect(screen.getByText('离线')).toBeInTheDocument();
  });

  it('renders the translated label for connecting status', () => {
    renderWithLang(<StatusBadge status="connecting" />);
    expect(screen.getByText('连接中')).toBeInTheDocument();
  });

  it('falls back to offline config for unknown status', () => {
    renderWithLang(<StatusBadge status="unknown_status" />);
    // Should render offline label
    expect(screen.getByText('离线')).toBeInTheDocument();
  });

  it('uses custom text when provided', () => {
    renderWithLang(<StatusBadge status="healthy" text="自定义" />);
    expect(screen.getByText('自定义')).toBeInTheDocument();
  });

  it('hides the dot when showDot is false', () => {
    const { container } = renderWithLang(<StatusBadge status="healthy" showDot={false} />);
    // No Badge component should be rendered
    const badges = container.querySelectorAll('.ant-badge');
    expect(badges.length).toBe(0);
  });

  it('has role=status for accessibility', () => {
    const { container } = renderWithLang(<StatusBadge status="healthy" />);
    const statusEl = container.querySelector('[role="status"]');
    expect(statusEl).toBeInTheDocument();
  });
});
