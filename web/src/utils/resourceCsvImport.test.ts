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
  validateTopicCsvImport,
} from './resourceCsvImport';

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
    const records = parseCsvTable('"Name","Remark"\n"\'-topic","\'=keep-original"');
    const validation = validateTopicCsvImport(records, 'instance-a');

    expect(validation.errors).toEqual([]);
    expect(validation.rows[0].payload).toMatchObject({
      name: '-topic',
      remark: '=keep-original',
      instanceId: 'instance-a',
    });
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
    const validation = validateConsumerGroupCsvImport(records, 'instance-b');

    expect(validation.errors).toEqual([]);
    expect(validation.rows[0].payload).toEqual({
      name: 'cg-orders',
      subscriptionMode: 'Push',
      consumeType: 'CLUSTERING',
      retryMaxTimes: 16,
      subscriptionDataType: 'FIFO',
      deliveryOrderType: 'PARTITON_ORDER',
      subscribedTopics: [],
      instanceId: 'instance-b',
    });
  });
});
