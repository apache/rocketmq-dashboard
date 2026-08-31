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

import { describe, expect, it } from 'vitest';
import { buildToolInputTemplate } from '../index';
import type { McpTool } from '../../../api/ai';

const tool = (overrides: Partial<McpTool>): McpTool => ({
  name: 'rmq.tool',
  description: 'test tool',
  parameters: {},
  ...overrides,
});

describe('buildToolInputTemplate', () => {
  it('fills required fields from schema defaults, enums, and type fallbacks', () => {
    const template = buildToolInputTemplate(
      tool({
        parameters: {
          type: 'object',
          required: ['cluster', 'level', 'retries', 'enabled', 'extra'],
          properties: {
            cluster: { type: 'string' },
            level: { type: 'string', enum: ['P1', 'P2'] },
            retries: { type: 'integer', default: 3 },
            enabled: { type: 'boolean' },
            extra: { type: 'string' },
          },
        },
      }),
      'cluster-a',
    );

    expect(JSON.parse(template)).toEqual({
      cluster: 'cluster-a',
      level: 'P1',
      retries: 3,
      enabled: false,
      extra: '',
    });
  });

  it('does not crash when the tool omits its parameter schema', () => {
    expect(buildToolInputTemplate(tool({ parameters: undefined as unknown as Record<string, unknown> }))).toBe('{}');
    expect(
      buildToolInputTemplate(tool({ parameters: 'broken' as unknown as Record<string, unknown> })),
    ).toBe('{}');
  });

  it('ignores required fields that are missing from the properties map', () => {
    const template = buildToolInputTemplate(
      tool({
        parameters: {
          type: 'object',
          required: ['cluster', 'orphan'],
          properties: { cluster: { type: 'string' } },
        },
      }),
    );

    expect(JSON.parse(template)).toEqual({ cluster: '', orphan: '' });
  });
});
