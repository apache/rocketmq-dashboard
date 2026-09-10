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

  it('forwards query type and pagination for message history', async () => {
    mock.onGet('/query-history/messages').reply((config) => {
      expect(config.params).toEqual({
        queryType: 'MSG_ID',
        search: '7F0000010000000000000001',
        page: 1,
        pageSize: 50,
      });
      return [200, { code: 200, data: { items: [], total: 0, page: 1, size: 50 } }];
    });

    await expect(
      listMessageQueryHistory({
        queryType: 'MSG_ID',
        search: '7F0000010000000000000001',
        page: 1,
        pageSize: 50,
      }),
    ).resolves.toMatchObject({ page: 1 });
  });

  it('omits query params entirely when the caller provides none', async () => {
    mock.onGet('/query-history/messages').reply((config) => {
      expect(config.params).toBeUndefined();
      return [200, { code: 200, data: { items: [], total: 0, page: 1, size: 20 } }];
    });
    mock.onGet('/query-history/summary').reply((config) => {
      expect(config.params).toBeUndefined();
      return [200, { code: 200, data: { messageQueries: 0, traceQueries: 0 } }];
    });

    await expect(listMessageQueryHistory()).resolves.toMatchObject({ total: 0 });
    await expect(getQueryHistorySummary()).resolves.toEqual({
      messageQueries: 0,
      traceQueries: 0,
    });
  });

  it('forwards the trace search term when provided', async () => {
    mock.onGet('/query-history/traces').reply((config) => {
      expect(config.params).toEqual({ clusterId: 'instance-b', search: 'msg-42' });
      return [200, { code: 200, data: { items: [], total: 0, page: 1, size: 20 } }];
    });

    await expect(
      listTraceQueryHistory({ clusterId: 'instance-b', search: 'msg-42' }),
    ).resolves.toMatchObject({ total: 0 });
  });

  it('loads the result snapshot page for a saved message query', async () => {
    mock.onGet('/query-history/messages/42/results').reply(200, {
      code: 200,
      data: [
        {
          msgId: 'msg-42',
          topic: 'orders',
          tag: '',
          key: '',
          brokerName: 'broker-0',
          queueId: 0,
          queueOffset: 10,
          storeTime: 1785000000000,
          bornHost: '10.0.0.5:1000',
          storeHost: '10.0.0.9:10911',
          size: 128,
        },
      ],
    });

    const results = await getMessageQueryResults(42);
    expect(results).toHaveLength(1);
    expect(results[0]).toMatchObject({ msgId: 'msg-42', brokerName: 'broker-0', queueId: 0 });
  });

  it('returns an empty snapshot list when a query produced no hits', async () => {
    mock.onGet('/query-history/messages/7/results').reply(200, {
      code: 200,
      data: [],
    });

    await expect(getMessageQueryResults(7)).resolves.toEqual([]);
  });
});
