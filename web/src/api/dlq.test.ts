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

import MockAdapter from 'axios-mock-adapter';
import type { InternalAxiosRequestConfig } from 'axios';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import client from './client';
import { exportDLQExcel, exportDLQMessages, listDLQGroups, resendDLQ } from './message';
import type { DLQGroup } from './message';

const mock = new MockAdapter(client);
const group: DLQGroup = {
  groupName: 'order-consumer',
  dlqTopic: '%DLQ%order-consumer',
  messageCount: 3,
  lastEnqueueTime: '2026-07-17T00:00:00Z',
  retryCount: 16,
  status: 'active',
};

describe('DLQ API', () => {
  beforeEach(() => {
    mock.reset();
    vi.stubGlobal('localStorage', { getItem: vi.fn().mockReturnValue(null) });
  });

  afterEach(() => {
    mock.reset();
    vi.unstubAllGlobals();
  });

  it('loads and unwraps DLQ groups', async () => {
    const pageData = { items: [group], total: 1, page: 1, size: 20 };
    mock
      .onGet('/dlq')
      .reply((config) =>
        config.params?.instanceId === 'instance-1' &&
        config.params?.page === 1 &&
        config.params?.pageSize === 20
          ? [200, { code: 200, data: pageData }]
          : [404, {}],
      );

    await expect(listDLQGroups('instance-1')).resolves.toEqual(pageData);
  });

  it('passes paged search parameters through to the backend contract', async () => {
    const pageData = { items: [group], total: 1, page: 2, size: 50 };
    mock
      .onGet('/dlq')
      .reply((config) =>
        config.params?.instanceId === 'instance-1' &&
        config.params?.search === 'order' &&
        config.params?.page === 2 &&
        config.params?.pageSize === 50
          ? [200, { code: 200, data: pageData }]
          : [404, {}],
      );

    await expect(listDLQGroups('instance-1', 'order', 2, 50)).resolves.toEqual(pageData);
  });

  it('sends epoch milliseconds for the resend time range', async () => {
    const payload = {
      instanceId: 'instance-1',
      groupName: group.groupName,
      startTime: 1784246400000,
      endTime: 1784332800000,
      targetTopic: 'orders-retry',
    };
    const result = { matched: 2, resent: 2, failed: 0, outcome: 'SUCCESS' };
    mock.onPost('/dlq/resend').reply((config) => {
      expect(JSON.parse(config.data)).toEqual(payload);
      return [200, { code: 200, data: result }];
    });

    await expect(resendDLQ(payload)).resolves.toEqual(result);
  });

  it('returns the export blob with completeness metadata from the response headers', async () => {
    const payload = new Blob(['[]'], { type: 'application/json' });
    mock.onGet('/dlq/export').reply(200, payload, {
      'content-type': 'application/json',
      'content-disposition': 'attachment; filename="dlq-order-consumer.json"',
      'x-dlq-export-truncated': 'true',
      'x-dlq-export-failedqueues': '1',
      'x-dlq-export-limit': '5000',
    });

    const { blob, meta } = await exportDLQMessages({
      instanceId: 'instance-1',
      groupName: group.groupName,
    });

    await expect(blob.text()).resolves.toBe('[]');
    expect(meta).toEqual({ truncated: true, failedQueueCount: 1, limit: 5000 });
  });

  it('defaults the export metadata when the backend omits the headers', async () => {
    const payload = new Blob(['[]'], { type: 'application/json' });
    mock.onGet('/dlq/export').reply(200, payload, {
      'content-type': 'application/json',
    });

    const { meta } = await exportDLQMessages({
      instanceId: 'instance-1',
      groupName: group.groupName,
    });

    expect(meta).toEqual({ truncated: false, failedQueueCount: 0, limit: 0 });
  });

  it('serializes selected msgIds as repeated params so Spring binds them', async () => {
    // axios's default serializer emits `msgIds[]=a&msgIds[]=b`, which Spring's
    // @RequestParam List<String> does not bind — the backend would silently fall
    // back to exporting the whole time window. Capture the request config that
    // exportDLQExcel actually sends and lock its repeated-param serialization.
    let capturedConfig: InternalAxiosRequestConfig | null = null;
    const originalAdapter = client.defaults.adapter;
    client.defaults.adapter = (config) => {
      capturedConfig = config;
      return Promise.resolve({
        data: new Blob(['xlsx']),
        status: 200,
        statusText: 'OK',
        headers: {},
        config,
      } as never);
    };
    try {
      await exportDLQExcel({
        instanceId: 'instance-1',
        groupName: group.groupName,
        msgIds: ['msg-1', 'msg-2'],
      });
    } finally {
      client.defaults.adapter = originalAdapter;
    }
    expect(capturedConfig).not.toBeNull();
    const serialized = client.getUri(capturedConfig as never);
    expect(serialized).toContain('msgIds=msg-1&msgIds=msg-2');
    expect(serialized).not.toContain('msgIds[]');
  });

  it('returns the Excel export blob with completeness metadata from the response headers', async () => {
    const payload = new Blob(['xlsx'], {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    });
    mock.onGet('/dlq/export-excel').reply(200, payload, {
      'content-type': 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
      'content-disposition': 'attachment; filename="dlq-order-consumer.xlsx"',
      'x-dlq-export-truncated': 'true',
      'x-dlq-export-failedqueues': '2',
      'x-dlq-export-limit': '5000',
    });

    const { blob, meta } = await exportDLQExcel({
      instanceId: 'instance-1',
      groupName: group.groupName,
    });

    await expect(blob.text()).resolves.toBe('xlsx');
    expect(meta).toEqual({ truncated: true, failedQueueCount: 2, limit: 5000 });
  });
});
