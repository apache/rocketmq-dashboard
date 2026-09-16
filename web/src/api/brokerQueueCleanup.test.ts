/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
import MockAdapter from 'axios-mock-adapter';
import { afterEach, describe, expect, it } from 'vitest';
import client from './client';
import { applyBrokerQueueCleanup, previewBrokerQueueCleanup } from './brokerQueueCleanup';
const mock = new MockAdapter(client);
const target = { instanceId: 'instance-a', brokerName: 'broker-a', address: 'master:10911' };
afterEach(() => mock.reset());
describe('Queue cleanup API', () => {
  it('reads a configured-topic snapshot without invoking deletion', async () => {
    mock.onGet('/brokers/queue-cleanup').reply((config) => {
      expect(config.params).toEqual(target);
      return [200, { code: 0, data: { configuredTopics: ['orders'] } }];
    });
    expect((await previewBrokerQueueCleanup(target)).configuredTopics).toEqual(['orders']);
    expect(mock.history.post).toHaveLength(0);
  });
  it('sends exact operation scope, retained-topic snapshot and typed confirmation', async () => {
    const signal = new AbortController().signal;
    mock.onPost('/brokers/queue-cleanup').reply((config) => {
      expect(JSON.parse(config.data)).toEqual({
        ...target,
        operation: 'EXPIRED_CONSUME_QUEUES',
        expectedConfiguredTopics: ['orders'],
        confirmation: target.address,
      });
      expect(config.signal).toBe(signal);
      return [200, { code: 0, data: { status: 'UNKNOWN' } }];
    });
    expect(
      (
        await applyBrokerQueueCleanup(
          target,
          'EXPIRED_CONSUME_QUEUES',
          ['orders'],
          target.address,
          signal,
        )
      ).status,
    ).toBe('UNKNOWN');
    expect(mock.history.post).toHaveLength(1);
  });
});
