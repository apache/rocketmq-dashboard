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

import { beforeAll, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { App } from 'antd';
import type { ComponentProps } from 'react';
import type {
  MessageQueryHistory,
  QueryHistorySummary,
  TraceQueryHistory,
} from '../../api/messageHistory';
import { LangProvider } from '../../i18n/LangContext';
import { LANGUAGE_STORAGE_KEY } from '../../i18n/languagePreference';
import MessageHistoryInsightsPanel from '../MessageHistoryInsightsPanel';

const NOW = Date.UTC(2026, 8, 10, 10, 0, 0);

const summary = (overrides: Partial<QueryHistorySummary> = {}): QueryHistorySummary => ({
  messageQueries: 6,
  traceQueries: 4,
  latestQueryAt: new Date(NOW - 20 * 60_000).toISOString(),
  ...overrides,
});

const messageRow = (overrides: Partial<MessageQueryHistory> = {}): MessageQueryHistory => ({
  id: 1,
  queryType: 'KEY',
  topic: 'orders',
  messageKey: 'order-1',
  resultCount: 3,
  queriedBy: 'alice',
  queriedAt: new Date(NOW - 10 * 60_000).toISOString(),
  ...overrides,
});

const traceRow = (overrides: Partial<TraceQueryHistory> = {}): TraceQueryHistory => ({
  id: 11,
  msgId: 'msg-1',
  topic: 'orders',
  traceTopic: 'RMQ_SYS_TRACE_TOPIC',
  nodeCount: 4,
  consumerCount: 2,
  queriedBy: 'alice',
  queriedAt: new Date(NOW - 5 * 60_000).toISOString(),
  ...overrides,
});

const renderPanel = (props: Partial<ComponentProps<typeof MessageHistoryInsightsPanel>> = {}) => {
  localStorage.setItem(LANGUAGE_STORAGE_KEY, 'en');
  return render(
    <App>
      <LangProvider>
        <MessageHistoryInsightsPanel
          summary={summary()}
          messageRows={[messageRow()]}
          traceRows={[traceRow()]}
          now={NOW}
          {...props}
        />
      </LangProvider>
    </App>,
  );
};

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

describe('MessageHistoryInsightsPanel', () => {
  it('shows a healthy score when the loaded history has no risky rows', () => {
    renderPanel();

    expect(screen.getByText('Query History Insights')).toBeInTheDocument();
    expect(screen.getByText('Healthy')).toBeInTheDocument();
    expect(
      screen.getByText(
        'Recent query history looks healthy, with no broad searches, zero-result lookups, or trace collection gaps detected.',
      ),
    ).toBeInTheDocument();
    expect(screen.getByText('No notable query rows')).toBeInTheDocument();
  });

  it('surfaces broad, empty, and oversized message search signals', () => {
    renderPanel({
      summary: summary({ messageQueries: 20, traceQueries: 0 }),
      messageRows: [
        messageRow({
          id: 2,
          queryType: 'TOPIC',
          topic: 'orders',
          messageKey: '',
          resultCount: 0,
          startTime: NOW - 48 * 3_600_000,
          endTime: NOW,
        }),
        messageRow({
          id: 3,
          queryType: 'TOPIC',
          topic: 'audit',
          messageKey: '',
          resultCount: 2_500,
          startTime: NOW - 72 * 3_600_000,
          endTime: NOW,
          queriedBy: '',
        }),
      ],
      traceRows: [],
    });

    expect(screen.getByText('Critical')).toBeInTheDocument();
    expect(screen.getByText('Zero-result message searches: 1')).toBeInTheDocument();
    expect(screen.getByText('Broad topic searches: 2')).toBeInTheDocument();
    expect(screen.getByText('Large-result message searches: 1')).toBeInTheDocument();
    expect(
      screen.getByText('Add Key, Message ID, Tag, or a shorter time range'),
    ).toBeInTheDocument();
    expect(screen.getByText('audit')).toBeInTheDocument();
    expect(screen.getByText('Large result')).toBeInTheDocument();
  });

  it('surfaces trace collection gaps and trace topic fragmentation', () => {
    renderPanel({
      summary: summary({ messageQueries: 2, traceQueries: 8 }),
      messageRows: [],
      traceRows: [
        traceRow({
          id: 4,
          msgId: 'msg-a',
          traceTopic: 'TRACE_A',
          nodeCount: 0,
          consumerCount: 0,
        }),
        traceRow({
          id: 5,
          msgId: 'msg-b',
          traceTopic: 'TRACE_B',
          nodeCount: 1,
          consumerCount: 0,
        }),
      ],
    });

    expect(screen.getByText('Warning')).toBeInTheDocument();
    expect(screen.getByText('Trace lookups without node data: 1')).toBeInTheDocument();
    expect(screen.getByText('Trace lookups without consumer data: 2')).toBeInTheDocument();
    expect(screen.getByText('Trace topics in use: 2')).toBeInTheDocument();
    expect(
      screen.getByText('Check trace collection and client reporting configuration'),
    ).toBeInTheDocument();
    expect(screen.getByText('TRACE_A')).toBeInTheDocument();
  });
});
