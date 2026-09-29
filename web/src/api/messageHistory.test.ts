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
  type MessageResultSnapshot,
} from './messageHistory';

const mock = new MockAdapter(client);

describe('message query history API', () => {
  beforeEach(() => mock.reset());
  afterEach(() => mock.reset());

  it('preserves unknown and zero queue positions in saved message results', async () => {
    const unknown: MessageResultSnapshot = {
      msgId: 'cloud-message',
      topic: 'orders',
      tag: '',
      key: '',
      brokerName: '',
      queueId: null,
      queueOffset: null,
      storeTime: 1,
      bornHost: '',
      storeHost: '',
      size: 1,
    };
    const firstMessage: MessageResultSnapshot = {
      ...unknown,
      msgId: 'first-message',
      queueId: 0,
      queueOffset: 0,
    };
    mock.onGet('/query-history/messages/9/results').reply(200, {
      code: 200,
      data: [unknown, firstMessage],
    });

    await expect(getMessageQueryResults(9)).resolves.toEqual([unknown, firstMessage]);
  });

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
});
