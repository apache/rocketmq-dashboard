/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
import MockAdapter from 'axios-mock-adapter';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import client from './client';
import { inspectConsumeQueue } from './consumeQueue';

const mock = new MockAdapter(client);
const query = {
  instanceId: 'instance-a',
  topic: 'orders',
  brokerName: 'broker-a',
  queueId: 2,
  index: '9007199254740993',
  count: 16,
};

describe('ConsumeQueue API contract', () => {
  beforeEach(() => {
    mock.reset();
    vi.stubGlobal('localStorage', { getItem: vi.fn().mockReturnValue(null) });
  });
  afterEach(() => vi.unstubAllGlobals());

  it('uses GET and transmits exact decimal strings with cancellation', async () => {
    const controller = new AbortController();
    mock.onGet('/messages/consume-queue').reply((config) => {
      expect(config.params).toEqual(query);
      expect(config.signal).toBe(controller.signal);
      return [200, { code: 200, data: { maxIndex: '9007199254740995', entries: [] } }];
    });
    await expect(inspectConsumeQueue(query, controller.signal)).resolves.toEqual({
      maxIndex: '9007199254740995',
      entries: [],
    });
    expect(mock.history.post).toHaveLength(0);
  });

  it('does not convert broker errors to an empty snapshot', async () => {
    mock.onGet('/messages/consume-queue').reply(200, { code: 502, message: 'Missing queue data' });
    await expect(inspectConsumeQueue(query)).rejects.toThrow('Missing queue data');
  });
});
