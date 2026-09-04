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
  analyzeMessagePayloadPreview,
  buildMessagePropertiesFromRows,
  isReservedMessageProperty,
} from './messagePayloadPreview';

describe('messagePayloadPreview', () => {
  it('summarizes a ready JSON object payload with normalized tag and key', () => {
    const preview = analyzeMessagePayloadPreview({
      topic: ' orders-topic ',
      tag: ' paid ',
      key: ' order-1 ',
      body: '{"orderId":"order-1","amount":128}',
      propsMode: 'form',
      properties: [
        { key: 'traceId', value: 'trace-1' },
        { key: 'tenant', value: 'retail' },
      ],
    });

    expect(preview.status).toBe('ready');
    expect(preview.blockingIssues).toEqual([]);
    expect(preview.normalized).toEqual({
      topic: 'orders-topic',
      tag: 'paid',
      key: 'order-1',
      body: '{"orderId":"order-1","amount":128}',
    });
    expect(preview.properties).toEqual({ traceId: 'trace-1', tenant: 'retail' });
    expect(preview.summary.bodyFormat).toBe('json-object');
    expect(preview.summary.propertyCount).toBe(2);
    expect(preview.issues.map((issue) => issue.code)).toEqual(['TRIMMED_TAG', 'TRIMMED_KEY']);
  });

  it('reports empty bodies as blocking issues', () => {
    const preview = analyzeMessagePayloadPreview({
      topic: 'orders-topic',
      body: '   ',
      propsMode: 'form',
      properties: [],
    });

    expect(preview.status).toBe('error');
    expect(preview.summary.bodyFormat).toBe('empty');
    expect(preview.blockingIssues).toHaveLength(1);
    expect(preview.blockingIssues[0]).toMatchObject({
      code: 'EMPTY_BODY',
      field: 'body',
      severity: 'error',
    });
  });

  it('flags default RocketMQ body size overflow as a blocking issue', () => {
    const preview = analyzeMessagePayloadPreview(
      {
        topic: 'orders-topic',
        body: 'rocketmq',
        propsMode: 'form',
        properties: [],
      },
      { maxBodyBytes: 4 },
    );

    expect(preview.status).toBe('error');
    expect(preview.blockingIssues.map((issue) => issue.code)).toEqual(['BODY_SIZE_LIMIT']);
    expect(preview.summary.bodyBytes).toBe(8);
  });

  it('keeps plain text sendable while describing the body format', () => {
    const preview = analyzeMessagePayloadPreview({
      topic: 'orders-topic',
      body: 'plain event body',
      propsMode: 'form',
      properties: [],
    });

    expect(preview.status).toBe('ready');
    expect(preview.summary.bodyFormat).toBe('plain-text');
    expect(preview.issues.map((issue) => issue.code)).toEqual(['PLAIN_TEXT_BODY']);
  });

  it('detects JSON arrays and scalar JSON bodies separately', () => {
    const arrayPreview = analyzeMessagePayloadPreview({
      topic: 'orders-topic',
      body: '[{"id":1}]',
      propsMode: 'form',
      properties: [],
    });
    const scalarPreview = analyzeMessagePayloadPreview({
      topic: 'orders-topic',
      body: '"ready"',
      propsMode: 'form',
      properties: [],
    });

    expect(arrayPreview.summary.bodyFormat).toBe('json-array');
    expect(arrayPreview.status).toBe('ready');
    expect(scalarPreview.summary.bodyFormat).toBe('json-scalar');
    expect(scalarPreview.issues.map((issue) => issue.code)).toEqual(['SCALAR_JSON_BODY']);
  });

  it('reports duplicate form properties before they can overwrite earlier values', () => {
    const result = buildMessagePropertiesFromRows([
      { key: 'traceId', value: 'first' },
      { key: 'tenant', value: 'demo' },
      { key: 'traceId', value: 'second' },
    ]);

    expect(result.entries.map((entry) => [entry.key, entry.value])).toEqual([
      ['traceId', 'first'],
      ['tenant', 'demo'],
    ]);
    expect(result.issues).toHaveLength(1);
    expect(result.issues[0]).toMatchObject({
      code: 'DUPLICATE_PROPERTY_KEY',
      severity: 'error',
      names: ['traceId'],
    });
  });

  it('reports form property values that do not have a key', () => {
    const result = buildMessagePropertiesFromRows([{ key: ' ', value: 'orphan-value' }]);

    expect(result.entries).toEqual([]);
    expect(result.issues[0]).toMatchObject({
      code: 'EMPTY_PROPERTY_KEY',
      severity: 'error',
    });
  });

  it('ignores sparse form property rows emitted by dynamic form lists', () => {
    const result = buildMessagePropertiesFromRows([
      undefined as unknown as { key?: string; value?: string },
      { key: 'traceId', value: 'trace-1' },
    ]);

    expect(result.entries.map((entry) => [entry.key, entry.value])).toEqual([
      ['traceId', 'trace-1'],
    ]);
    expect(result.issues).toEqual([]);
  });

  it('uses the batch text parser and preserves values containing equals signs', () => {
    const preview = analyzeMessagePayloadPreview({
      topic: 'orders-topic',
      body: '{}',
      propsMode: 'text',
      propsText: 'traceId=abc\nsignature=part-a=part-b',
    });

    expect(preview.status).toBe('ready');
    expect(preview.properties).toEqual({
      traceId: 'abc',
      signature: 'part-a=part-b',
    });
    expect(preview.blockingIssues).toEqual([]);
  });

  it('maps malformed batch property lines to blocking preview issues', () => {
    const preview = analyzeMessagePayloadPreview({
      topic: 'orders-topic',
      body: '{}',
      propsMode: 'text',
      propsText: 'traceId=abc\ntenant\ntraceId=duplicate',
    });

    expect(preview.status).toBe('error');
    expect(preview.blockingIssues.map((issue) => issue.code)).toEqual([
      'INVALID_PROPERTY_FORMAT',
      'INVALID_PROPERTY_FORMAT',
    ]);
    expect(preview.properties).toEqual({ traceId: 'abc' });
  });

  it('warns when user properties use names reserved by RocketMQ message metadata', () => {
    const preview = analyzeMessagePayloadPreview({
      topic: 'orders-topic',
      body: '{}',
      propsMode: 'form',
      properties: [
        { key: 'TAGS', value: 'tag-a' },
        { key: 'businessKey', value: 'b-1' },
      ],
    });

    expect(isReservedMessageProperty('tags')).toBe(true);
    expect(isReservedMessageProperty('businessKey')).toBe(false);
    expect(preview.status).toBe('warning');
    expect(preview.issues.find((issue) => issue.code === 'RESERVED_PROPERTY_KEY')).toMatchObject({
      severity: 'warning',
      names: ['TAGS'],
    });
  });

  it('summarizes empty property values and large property sets without blocking send', () => {
    const properties = Array.from({ length: 4 }, (_, index) => ({
      key: `k${index}`,
      value: index === 0 ? '' : 'v',
    }));
    const preview = analyzeMessagePayloadPreview(
      {
        topic: 'orders-topic',
        body: '{}',
        propsMode: 'form',
        properties,
      },
      { maxProperties: 2, maxPropertyBytes: 6 },
    );

    expect(preview.status).toBe('warning');
    expect(preview.blockingIssues).toEqual([]);
    expect(preview.summary.propertyCount).toBe(4);
    expect(preview.issues.map((issue) => issue.code)).toEqual([
      'EMPTY_PROPERTY_VALUE',
      'PROPERTY_COUNT_LIMIT',
      'PROPERTY_SIZE_LIMIT',
    ]);
  });

  it('preserves prototype-like property names in the normalized property object', () => {
    const preview = analyzeMessagePayloadPreview({
      topic: 'orders-topic',
      body: '{}',
      propsMode: 'text',
      propsText: '__proto__=trace-prototype\nconstructor=trace-constructor',
    });

    expect(Object.keys(preview.properties)).toEqual(['__proto__', 'constructor']);
    expect(preview.properties['__proto__']).toBe('trace-prototype');
    expect(preview.properties['constructor']).toBe('trace-constructor');
    expect(JSON.parse(JSON.stringify(preview.properties))).toEqual(
      Object.fromEntries([
        ['__proto__', 'trace-prototype'],
        ['constructor', 'trace-constructor'],
      ]),
    );
  });
});
