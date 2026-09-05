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
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AiMessage } from '../index';

describe('AiMessage', () => {
  beforeEach(() => {
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

  it('renders markdown content in AI responses', () => {
    render(
      <AiMessage
        msg={{
          id: 'ai-1',
          role: 'ai',
          summary: [
            '# 扩缩容评估',
            '',
            '- **QPS/TPS**: 每秒查询或事务数',
            '',
            '| 指标 | 值 |',
            '| --- | --- |',
            '| CPU | 70% |',
            '',
            '```bash',
            'mqadmin clusterList',
            '```',
          ].join('\n'),
        }}
      />,
    );

    expect(screen.getByRole('heading', { name: '扩缩容评估', level: 1 })).toBeInTheDocument();
    expect(screen.getByText('QPS/TPS')).toBeInTheDocument();
    expect(screen.getByRole('table')).toBeInTheDocument();
    expect(screen.getByText('mqadmin clusterList')).toBeInTheDocument();
    expect(screen.getByText('mqadmin clusterList').closest('pre')).toBeInTheDocument();
  });

  it('normalizes common malformed Markdown markers from model responses', () => {
    render(
      <AiMessage
        msg={{
          id: 'ai-2',
          role: 'ai',
          summary: ['##结论', '-第一项', '', '```bashmqadmin clusterList', '```'].join('\n'),
        }}
      />,
    );

    expect(screen.getByRole('heading', { name: '结论', level: 2 })).toBeInTheDocument();
    expect(screen.getByRole('listitem')).toHaveTextContent('第一项');
    expect(screen.getByText('mqadmin clusterList').closest('pre')).toBeInTheDocument();
  });

  it('renders a tool call tag for tool-backed responses', () => {
    type AiMessageProps = Parameters<typeof AiMessage>[0];
    render(
      <AiMessage
        msg={
          {
            id: 'ai-3',
            role: 'ai',
            toolCall: { label: 'rmq.cluster.list' },
          } as AiMessageProps['msg']
        }
      />,
    );

    expect(screen.getByText('rmq.cluster.list')).toBeInTheDocument();
  });

  it('renders stat cards for numeric digests', () => {
    type AiMessageProps = Parameters<typeof AiMessage>[0];
    render(
      <AiMessage
        msg={
          {
            id: 'ai-4',
            role: 'ai',
            stats: [
              { title: 'Broker count', value: 3, suffix: '', color: '#1677ff' },
              { title: 'Topic count', value: 128, suffix: '', color: '#52c41a' },
            ],
          } as AiMessageProps['msg']
        }
      />,
    );

    expect(screen.getByText('Broker count')).toBeInTheDocument();
    expect(screen.getByText('Topic count')).toBeInTheDocument();
    expect(screen.getByText('128')).toBeInTheDocument();
  });

  it('renders the chain-of-thought details when thinking is present', () => {
    type AiMessageProps = Parameters<typeof AiMessage>[0];
    render(
      <AiMessage
        msg={
          {
            id: 'ai-5',
            role: 'ai',
            thinking: 'step one: evaluate the lag',
          } as AiMessageProps['msg']
        }
      />,
    );

    expect(screen.getByText('思维链：Prompt 增强改写')).toBeInTheDocument();
    expect(screen.getByText('step one: evaluate the lag')).toBeInTheDocument();
  });

  it('renders descriptions as a definition block', () => {
    type AiMessageProps = Parameters<typeof AiMessage>[0];
    render(
      <AiMessage
        msg={
          {
            id: 'ai-6',
            role: 'ai',
            descriptions: [
              { label: 'Cluster', value: 'production' },
              { label: 'Version', value: '5.2.0' },
            ],
          } as AiMessageProps['msg']
        }
      />,
    );

    expect(screen.getByText('Cluster')).toBeInTheDocument();
    expect(screen.getByText('production')).toBeInTheDocument();
    expect(screen.getByText('Version')).toBeInTheDocument();
  });
});
