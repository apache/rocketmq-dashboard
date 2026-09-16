/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
import MockAdapter from 'axios-mock-adapter';
import { afterEach, describe, expect, it } from 'vitest';
import client from './client';
import { applyConsumerRequestMode, previewConsumerRequestMode } from './consumerRequestMode';

const mock = new MockAdapter(client);
const selection = { instanceId: 'instance-a', topic: 'orders', group: 'group-a' };
const before = {
  brokerName: 'broker-a',
  address: 'master:10911',
  mode: 'PULL' as const,
  popShareQueueNum: 0,
  explicit: false,
  serverLoadBalancerEnable: 'true',
};
afterEach(() => mock.reset());
describe('Consumer request mode API', () => {
  it('previews through GET without changing broker configuration', async () => {
    mock.onGet('/consumer-request-mode').reply((config) => {
      expect(config.params).toEqual(selection);
      return [200, { code: 0, data: { topic: 'orders', group: 'group-a', brokers: [before] } }];
    });
    expect((await previewConsumerRequestMode(selection)).brokers[0].explicit).toBe(false);
    expect(mock.history.post).toHaveLength(0);
  });
  it('posts the exact preview and preserves uncertain outcomes', async () => {
    const signal = new AbortController().signal;
    mock.onPost('/consumer-request-mode').reply((config) => {
      expect(JSON.parse(config.data)).toEqual({
        ...selection,
        mode: 'POP',
        popShareQueueNum: -1,
        expected: [before],
      });
      expect(config.signal).toBe(signal);
      return [200, { code: 0, data: { brokers: [{ before, status: 'UNKNOWN', observed: null }] } }];
    });
    expect(
      (await applyConsumerRequestMode(selection, 'POP', -1, [before], signal)).brokers[0].status,
    ).toBe('UNKNOWN');
    expect(mock.history.post).toHaveLength(1);
  });
});
