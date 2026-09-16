/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
import MockAdapter from 'axios-mock-adapter';
import { afterEach, describe, expect, it } from 'vitest';
import client from './client';
import { inspectBrokerReadAhead, updateBrokerReadAhead } from './brokerReadAhead';
const mock = new MockAdapter(client);
const target = { instanceId: 'instance-a', brokerName: 'broker-a', address: 'master:10911' };
afterEach(() => mock.reset());
describe('Read-ahead API', () => {
  it('binds inspection to the selected instance and exact broker node', async () => {
    mock.onGet('/brokers/read-ahead').reply((config) => {
      expect(config.params).toEqual(target);
      return [200, { code: 0, data: { enabled: true } }];
    });
    expect((await inspectBrokerReadAhead(target)).enabled).toBe(true);
    expect(mock.history.post).toHaveLength(0);
  });
  it('posts reviewed and desired booleans and preserves uncertain outcomes', async () => {
    const signal = new AbortController().signal;
    mock.onPost('/brokers/read-ahead').reply((config) => {
      expect(JSON.parse(config.data)).toEqual({ ...target, expectedEnabled: true, enabled: false });
      expect(config.signal).toBe(signal);
      return [200, { code: 0, data: { status: 'UNKNOWN', acknowledged: true, observed: null } }];
    });
    expect((await updateBrokerReadAhead(target, true, false, signal)).status).toBe('UNKNOWN');
  });
});
