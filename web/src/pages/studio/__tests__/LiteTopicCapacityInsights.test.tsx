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

import { render, screen, within } from '@testing-library/react';
import { beforeAll, describe, expect, it, vi } from 'vitest';
import { App } from 'antd';
import type { LiteTopicItem, LiteTopicQuota } from '../../../api/liteTopic';
import { LangProvider } from '../../../i18n/LangContext';
import LiteTopicCapacityInsights from '../LiteTopicCapacityInsights';

const quota: LiteTopicQuota = {
  currentTopicCount: 92,
  maxTopicCount: 100,
  currentSessionCount: 81,
  maxSessionCount: 100,
  currentCreationRate: 20,
  maxCreationRate: 100,
};

const topics: LiteTopicItem[] = [
  {
    namespace: 'default',
    topicPattern: 'expired-*',
    topicCount: 30,
    consumerCount: 8,
    totalBacklog: 120_000,
    averageTTL: 3_600_000,
    ttlStatus: 'EXPIRED',
    lastActiveTime: Date.now() - 10_000,
    sessionIds: ['session-a', 'session-b'],
  },
  {
    namespace: 'payments',
    topicPattern: 'soon-*',
    topicCount: 4,
    consumerCount: 1,
    totalBacklog: 20,
    averageTTL: 600_000,
    ttlStatus: 'EXPIRING_SOON',
    lastActiveTime: Date.now() - 5_000,
    sessionIds: ['session-c'],
  },
];

const renderPanel = (props = {}) =>
  render(
    <App>
      <LangProvider>
        <LiteTopicCapacityInsights quota={quota} topics={topics} loading={false} {...props} />
      </LangProvider>
    </App>,
  );

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

describe('LiteTopicCapacityInsights', () => {
  it('renders quota pressure, TTL findings, recommendations, and hotspot tables', () => {
    renderPanel();

    expect(screen.getByText('容量风险洞察')).toBeInTheDocument();
    expect(screen.getAllByText('高风险')).not.toHaveLength(0);
    expect(screen.getByText('Topic 配额')).toBeInTheDocument();
    expect(screen.getByText('92%')).toBeInTheDocument();
    expect(screen.getByText('剩余 8 个 Topic 名额')).toBeInTheDocument();
    expect(screen.getByText('需要处理的容量信号')).toBeInTheDocument();
    expect(screen.getByText(/Topic 配额已使用 92%/u)).toBeInTheDocument();
    expect(screen.getByText(/expired-\* 当前积压 120,000 条/u)).toBeInTheDocument();
    expect(screen.getByText('评估扩容 Topic 配额或清理过期模式')).toBeInTheDocument();
    expect(screen.getByText('优先排查积压热点的消费能力')).toBeInTheDocument();

    const hotspotCard = screen.getByText('积压热点').closest('.ant-card');
    expect(hotspotCard).not.toBeNull();
    expect(within(hotspotCard as HTMLElement).getByText('模式：expired-*')).toBeInTheDocument();
    expect(within(hotspotCard as HTMLElement).getByText('120,000')).toBeInTheDocument();

    const namespaceCard = screen.getByText('命名空间压力').closest('.ant-card');
    expect(namespaceCard).not.toBeNull();
    expect(within(namespaceCard as HTMLElement).getByText('命名空间：default')).toBeInTheDocument();
    expect(
      within(namespaceCard as HTMLElement).getByText('1 个模式 / 30 个 Topic'),
    ).toBeInTheDocument();
  });

  it('renders a healthy empty state when usage is below thresholds', () => {
    renderPanel({
      quota: {
        currentTopicCount: 10,
        maxTopicCount: 100,
        currentSessionCount: 5,
        maxSessionCount: 100,
        currentCreationRate: 1,
        maxCreationRate: 100,
      },
      topics: [
        {
          namespace: 'default',
          topicPattern: 'active-*',
          topicCount: 1,
          consumerCount: 1,
          totalBacklog: 0,
          ttlStatus: 'ACTIVE',
          lastActiveTime: Date.now(),
        },
      ],
    });

    expect(screen.getAllByText('健康')).not.toHaveLength(0);
    expect(screen.getByText('当前筛选范围内未发现 LiteTopic 容量风险')).toBeInTheDocument();
    expect(screen.getByText('当前没有积压热点')).toBeInTheDocument();
    expect(screen.getByText('当前没有 TTL 风险')).toBeInTheDocument();
  });

  it('shows a skeleton while LiteTopic data is refreshing', () => {
    const { container } = renderPanel({ loading: true });

    expect(screen.getByText('容量风险洞察')).toBeInTheDocument();
    expect(container.querySelector('.ant-skeleton')).toBeInTheDocument();
    expect(screen.queryByText('需要处理的容量信号')).not.toBeInTheDocument();
  });

  it('does not render when both quota and topics are unavailable', () => {
    renderPanel({ quota: null, topics: [] });

    expect(screen.queryByText('容量风险洞察')).not.toBeInTheDocument();
  });
});
