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
import { describe, expect, it } from 'vitest';
import { AiMessage } from '../index';

describe('AiMessage', () => {
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
});
