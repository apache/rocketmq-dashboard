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
import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest';
import type { AuditSummary } from '../../../api/audit';
import { LangProvider } from '../../../i18n/LangContext';
import { LANGUAGE_STORAGE_KEY } from '../../../i18n/languagePreference';
import AuditSummaryCards from '../AuditSummaryCards';

const summary: AuditSummary = {
  total: 10,
  successful: 5,
  failed: 4,
  partial: 1,
  uniqueOperators: 3,
  latestAt: '2026-08-01 10:00:00',
  byOperation: [{ name: 'DELETE_TOPIC', count: 4 }],
  byResourceType: [{ name: 'TOPIC', count: 4 }],
};

const renderCards = (props: Partial<Parameters<typeof AuditSummaryCards>[0]> = {}) =>
  render(
    <LangProvider>
      <AuditSummaryCards summary={summary} loading={false} {...props} />
    </LangProvider>,
  );

beforeAll(() => {
  // antd's responsive Row/Col subscribes to matchMedia, which jsdom does not implement.
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

afterEach(() => {
  localStorage.clear();
});

describe('AuditSummaryCards', () => {
  it('renders every card label in Chinese by default', () => {
    renderCards();

    expect(screen.getByText('匹配记录')).toBeInTheDocument();
    expect(screen.getByText('成功率')).toBeInTheDocument();
    expect(screen.getByText('失败 / 部分成功')).toBeInTheDocument();
    expect(screen.getByText('操作人数')).toBeInTheDocument();
    expect(screen.getByText('高频操作')).toBeInTheDocument();
    expect(screen.getByText('资源类型分布')).toBeInTheDocument();
  });

  it('renders every card label in English when the console language is English', () => {
    localStorage.setItem(LANGUAGE_STORAGE_KEY, 'en');
    renderCards();

    expect(screen.getByText('Matched Records')).toBeInTheDocument();
    expect(screen.getByText('Success Rate')).toBeInTheDocument();
    expect(screen.getByText('Failed / Partial')).toBeInTheDocument();
    expect(screen.getByText('Operators')).toBeInTheDocument();
    expect(screen.getByText('Top Operations')).toBeInTheDocument();
    expect(screen.getByText('Resource Type Distribution')).toBeInTheDocument();
    expect(screen.queryByText('匹配记录')).not.toBeInTheDocument();
  });

  it('localizes the empty bucket placeholder in both buckets', () => {
    localStorage.setItem(LANGUAGE_STORAGE_KEY, 'en');
    renderCards({ summary: { ...summary, byOperation: [], byResourceType: [] } });

    expect(screen.getAllByText('No Data')).toHaveLength(2);
  });
});
