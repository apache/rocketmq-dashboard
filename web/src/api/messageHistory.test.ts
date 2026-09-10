/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
import MockAdapter from 'axios-mock-adapter';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import client from './client';
import {
  getMessageQueryResults,
  getQueryHistorySummary,
  listMessageQueryHistory,
  listTraceQueryHistory,
} from './messageHistory';

const mock = new MockAdapter(client);

describe('message query history API', () => {
  beforeEach(() => mock.reset());
  afterEach(() => mock.reset());

  it('forwards filters and pagination for message history', async () => {
    mock.onGet('/query-history/messages').reply((config) => {
      expect(config.params).toEqual({
        clusterId: 'instance-a',
        search: 'orders',
        page: 2,
        pageSize: 20,
      });
      return [200, { code: 200, data: { items: [], total: 0, page: 2, size: 20 } }];
    });

    const result = await listMessageQueryHistory({
      clusterId: 'instance-a',
      search: 'orders',
      page: 2,
      pageSize: 20,
    });
    expect(result.page).toBe(2);
  });

  it('loads trace history and summary for an instance', async () => {
    mock.onGet('/query-history/traces').reply(200, {
      code: 200,
      data: { items: [{ id: 1, msgId: 'msg-1' }], total: 1, page: 1, size: 20 },
    });
    mock.onGet('/query-history/summary').reply((config) => {
      expect(config.params).toEqual({ clusterId: 'instance-a' });
      return [200, { code: 200, data: { messageQueries: 3, traceQueries: 1 } }];
    });

    await expect(listTraceQueryHistory({ clusterId: 'instance-a' })).resolves.toMatchObject({
      total: 1,
    });
    await expect(getQueryHistorySummary('instance-a')).resolves.toMatchObject({ traceQueries: 1 });
  });

  it('fetches the result snapshots for a history entry', async () => {
    mock.onGet('/query-history/messages/7/results').reply(200, {
      code: 200,
      data: [{ msgId: 'msg-1', topic: 'orders', size: 128 }],
    });

    const snapshots = await getMessageQueryResults(7);

    expect(snapshots[0].msgId).toBe('msg-1');
    expect(snapshots[0].size).toBe(128);
  });

  it('omits query parameters for a cluster-less summary', async () => {
    mock.onGet('/query-history/summary').reply((config) => {
      expect(config.params).toBeUndefined();
      return [200, { code: 200, data: { messageQueries: 0, traceQueries: 0 } }];
    });

    await expect(getQueryHistorySummary()).resolves.toMatchObject({ messageQueries: 0 });
  });
});
