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
  parseCsvTable,
  RESOURCE_IMPORT_ROW_LIMIT,
  validateConsumerGroupCsvImport,
  validateResourceName,
  validateTopicCsvImport,
} from './resourceCsvImport';
import { buildCsv } from './download';

describe('resourceCsvImport', () => {
  it('parses RFC4180 CSV with BOM, CRLF, quoted commas, newlines, and escaped quotes', () => {
    const records = parseCsvTable(
      '\uFEFF"Name","Remark"\r\n"topic-a","line1\r\nline2, ""quoted"""\r\n\r\n',
    );

    expect(records).toEqual([
      {
        lineNumber: 2,
        values: {
          Name: 'topic-a',
          Remark: 'line1\r\nline2, "quoted"',
        },
      },
    ]);
  });

  it('rejects malformed headers, unclosed quotes, and row caps', () => {
    expect(() => parseCsvTable('"Name","Name"\n"a","b"')).toThrow('CSV 表头重复');
    expect(() => parseCsvTable('"Name","Remark"\n"a,"broken"')).toThrow('CSV 引号格式错误');
    expect(() => parseCsvTable('"Name","Remark"\n"a"broken,"x"')).toThrow('CSV 引号格式错误');
    expect(() => parseCsvTable('"Name"\n"unterminated')).toThrow('CSV 引号未闭合');

    const tooManyRows = [
      '"Name"',
      ...Array.from({ length: RESOURCE_IMPORT_ROW_LIMIT + 1 }, (_, index) => `"topic-${index}"`),
    ].join('\n');
    expect(() => parseCsvTable(tooManyRows)).toThrow(
      `一次最多导入 ${RESOURCE_IMPORT_ROW_LIMIT} 行`,
    );
  });

  it('round-trips formula-safe apostrophes from exported cells', () => {
    const csv = buildCsv(
      [
        { header: 'Name', value: (row: { name: string; remark: string }) => row.name },
        { header: 'Remark', value: (row: { name: string; remark: string }) => row.remark },
      ],
      [{ name: '-topic', remark: "'=keep-original" }],
    );
    const records = parseCsvTable(csv);
    const validation = validateTopicCsvImport(records, 'instance-1');

    expect(validation.errors).toEqual([]);
    expect(validation.rows[0].payload).toMatchObject({
      name: '-topic',
      remark: "'=keep-original",
      instanceId: 'instance-1',
    });
  });

  it.each(['\t', '\r', '\n'])('restores exported control-prefixed cells for %j', (prefix) => {
    const records = parseCsvTable(`"Name","Remark"\n"'${prefix}topic-a","ok"`);

    expect(records[0].values.Name).toBe('topic-a');
  });

  it('tracks lone carriage returns inside quoted fields when reporting row errors', () => {
    const content = [
      '"Name","Remark"',
      '"topic-a","line1\rline2"',
      '"topic-b","ok"',
      '"topic-c","too","many"',
    ].join('\r\n');

    expect(() => parseCsvTable(content)).toThrow('第 5 行字段数超过表头字段数');
  });

  it('validates topic fields and duplicate names before import calls', () => {
    const records = parseCsvTable(
      [
        '"Name","Type","Write Queues","Read Queues","Permission"',
        '"topic-a","NORMAL","8","8","RW"',
        '"topic-a","INVALID","0","257","BAD"',
      ].join('\n'),
    );
    const validation = validateTopicCsvImport(records);

    expect(validation.errors).toEqual([]);
    expect(validation.rows[0]).toMatchObject({ name: 'topic-a', status: 'pending' });
    expect(validation.rows[1]).toMatchObject({ name: 'topic-a', status: 'invalid' });
    expect(validation.rows[1].message).toContain('重复');
    expect(validation.rows[1].message).toContain('Type 不支持');
    expect(validation.rows[1].message).toContain('Write Queues 必须在 1..256 之间');
    expect(validation.rows[1].message).toContain('Read Queues 必须在 1..256 之间');
    expect(validation.rows[1].message).toContain('Permission 不支持');
  });

  it('maps consumer group CSV fields to create payloads', () => {
    const records = parseCsvTable(
      [
        '"Name","Subscription Mode","Consume Type","Retry Max Times","Subscription Data Type","Delivery Order Type","Cluster ID"',
        '"cg-orders","Push","CLUSTERING","16","FIFO","PARTITON_ORDER","ignored-cluster"',
      ].join('\n'),
    );
    const validation = validateConsumerGroupCsvImport(records, 'instance-2');

    expect(validation.errors).toEqual([]);
    expect(validation.rows[0].payload).toEqual({
      name: 'cg-orders',
      subscriptionMode: 'Push',
      consumeType: 'CLUSTERING',
      retryMaxTimes: 16,
      subscriptionDataType: 'FIFO',
      deliveryOrderType: 'PARTITON_ORDER',
      subscribedTopics: [],
      instanceId: 'instance-2',
    });
  });

  it('aligns topic and group names with the RocketMQ validators', () => {
    const topicStatus = (name: string) =>
      validateTopicCsvImport(parseCsvTable(['"Name"', `"${name}"`].join('\n'))).rows[0].status;
    const groupStatus = (name: string) =>
      validateConsumerGroupCsvImport(parseCsvTable(['"Name"', `"${name}"`].join('\n'))).rows[0]
        .status;

    // RocketMQ accepts % and | in both topic and group names
    expect(topicStatus('100%topic')).toBe('pending');
    expect(topicStatus('topic|pipe')).toBe('pending');
    expect(groupStatus('cg|pipe')).toBe('pending');
    // Groups may start with a digit (no leading-letter rule)
    expect(groupStatus('1cg')).toBe('pending');
    // / and * are not part of the RocketMQ name character set
    expect(topicStatus('topic/with-slash')).toBe('invalid');
    expect(topicStatus('topic*star')).toBe('invalid');
  });

  it('applies the RocketMQ length caps to imported names', () => {
    const topicStatus = (name: string) =>
      validateTopicCsvImport(parseCsvTable(['"Name"', `"${name}"`].join('\n'))).rows[0].status;
    const groupStatus = (name: string) =>
      validateConsumerGroupCsvImport(parseCsvTable(['"Name"', `"${name}"`].join('\n'))).rows[0]
        .status;

    expect(topicStatus('t'.repeat(127))).toBe('pending');
    expect(topicStatus('t'.repeat(128))).toBe('invalid');
    expect(groupStatus('g'.repeat(120))).toBe('pending');
    expect(groupStatus('g'.repeat(121))).toBe('invalid');
  });

  it('reports the RocketMQ-oriented name error messages', () => {
    expect(validateResourceName('', 'topic')).toBe('Name 不能为空');
    expect(validateResourceName('a'.repeat(128), 'topic')).toBe('Name 长度不能超过 127 个字符');
    expect(validateResourceName('a'.repeat(121), 'group')).toBe('Name 长度不能超过 120 个字符');
    expect(validateResourceName('bad/name', 'topic')).toBe(
      'Name 仅支持字母、数字、下划线、短横线、% 和 |',
    );
    expect(validateResourceName('ok-name|100%', 'group')).toBeNull();
  });

  it.each(['MESSAGES_ORDER', 'MESSAGES ORDER'])(
    'accepts and canonicalizes the global delivery order value %s from CSV',
    (deliveryOrderType) => {
      const records = parseCsvTable(
        [
          '"Name","Subscription Data Type","Delivery Order Type"',
          `"cg-global-orders","FIFO","${deliveryOrderType}"`,
        ].join('\n'),
      );
      const validation = validateConsumerGroupCsvImport(records, 'instance-3');

      expect(validation.errors).toEqual([]);
      expect(validation.rows[0]).toMatchObject({
        status: 'pending',
        payload: {
          name: 'cg-global-orders',
          subscriptionDataType: 'FIFO',
          deliveryOrderType: 'MESSAGES_ORDER',
          instanceId: 'instance-3',
        },
      });
    },
  );
});
