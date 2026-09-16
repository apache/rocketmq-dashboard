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
import {
  appendMessagePropertyTemplateText,
  applyMessagePropertyTemplate,
  findMessagePropertyTemplate,
  messagePropertyTemplateOptions,
  messagePropertyTemplates,
  templateToPropertyText,
} from './messagePropertyTemplates';

const fixedNow = new Date('2026-09-16T12:34:56.789Z');

describe('messagePropertyTemplates', () => {
  it('exposes every template as a selectable option', () => {
    expect(messagePropertyTemplateOptions).toEqual(
      messagePropertyTemplates.map((template) => ({
        label: template.label,
        value: template.id,
      })),
    );
    expect(messagePropertyTemplateOptions.length).toBeGreaterThanOrEqual(4);
  });

  it('finds a template by id and returns undefined for empty input', () => {
    expect(findMessagePropertyTemplate('trace-context')?.label).toBe('Trace context');
    expect(findMessagePropertyTemplate()).toBeUndefined();
  });

  it('appends missing form rows without overwriting existing operator input', () => {
    const result = applyMessagePropertyTemplate(
      [
        { key: 'traceId', value: 'operator-trace' },
        { key: 'businessKey', value: 'order-1' },
        { key: ' ', value: '' },
      ],
      'trace-context',
      fixedNow,
    );

    expect(result.rows).toEqual([
      { key: 'traceId', value: 'operator-trace' },
      { key: 'businessKey', value: 'order-1' },
      { key: 'spanId', value: 'studio-send' },
      { key: 'source', value: 'rocketmq-studio' },
    ]);
    expect(result.appliedKeys).toEqual(['spanId', 'source']);
    expect(result.skippedKeys).toEqual(['traceId']);
  });

  it('expands millisecond and ISO timestamp tokens in form rows', () => {
    const result = applyMessagePropertyTemplate([], 'retry-audit', fixedNow);

    expect(result.rows).toEqual([
      { key: 'retryReason', value: 'manual-replay' },
      { key: 'operator', value: 'rocketmq-studio' },
      { key: 'requestedAt', value: '2026-09-16T12:34:56.789Z' },
    ]);
    expect(result.appliedKeys).toEqual(['retryReason', 'operator', 'requestedAt']);
    expect(result.skippedKeys).toEqual([]);
  });

  it('builds deterministic key=value text for a template', () => {
    expect(templateToPropertyText('tenant-routing', fixedNow)).toBe(
      ['tenantId=tenant-demo', 'region=cn-hangzhou', 'environment=staging'].join('\n'),
    );
  });

  it('appends text template lines after existing input', () => {
    const result = appendMessagePropertyTemplateText(
      'businessKey=order-1',
      'order-diagnostics',
      fixedNow,
    );

    expect(result.text).toBe(
      [
        'businessKey=order-1',
        'orderId=order-1789562096789',
        'businessType=order-created',
        'shardHint=order',
      ].join('\n'),
    );
    expect(result.appliedKeys).toEqual(['orderId', 'businessType', 'shardHint']);
    expect(result.skippedKeys).toEqual([]);
  });

  it('does not duplicate keys that already exist in batch text', () => {
    const result = appendMessagePropertyTemplateText(
      'tenantId=tenant-a\nregion=cn-shanghai',
      'tenant-routing',
      fixedNow,
    );

    expect(result.text).toBe(
      ['tenantId=tenant-a', 'region=cn-shanghai', 'environment=staging'].join('\n'),
    );
    expect(result.appliedKeys).toEqual(['environment']);
    expect(result.skippedKeys).toEqual(['tenantId', 'region']);
  });

  it('keeps malformed existing text but still appends safe missing template lines', () => {
    const result = appendMessagePropertyTemplateText(
      'traceId=abc\nbroken-line',
      'trace-context',
      fixedNow,
    );

    expect(result.text).toContain('broken-line');
    expect(result.text).toContain('spanId=studio-send');
    expect(result.text).toContain('source=rocketmq-studio');
    expect(result.skippedKeys).toEqual(['traceId']);
  });

  it('returns existing values unchanged for unknown template ids', () => {
    const result = applyMessagePropertyTemplate(
      [{ key: 'traceId', value: 'trace-1' }],
      'unknown-template' as never,
      fixedNow,
    );

    expect(result).toEqual({
      rows: [{ key: 'traceId', value: 'trace-1' }],
      appliedKeys: [],
      skippedKeys: [],
    });
    expect(templateToPropertyText('unknown-template' as never, fixedNow)).toBe('');
    expect(
      appendMessagePropertyTemplateText('traceId=trace-1', 'unknown-template' as never, fixedNow),
    ).toEqual({
      text: 'traceId=trace-1',
      appliedKeys: [],
      skippedKeys: [],
    });
  });
});
