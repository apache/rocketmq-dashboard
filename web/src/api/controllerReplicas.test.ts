/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
import MockAdapter from 'axios-mock-adapter';
import { afterEach, describe, expect, it } from 'vitest';
import client from './client';
import { inspectControllerReplicas } from './controllerReplicas';

const mock = new MockAdapter(client);
afterEach(() => mock.reset());
describe('Controller replica API', () => {
  it('uses a selected-instance GET and preserves partial observations', async () => {
    const signal = new AbortController().signal;
    mock.onGet('/brokers/controller-replicas').reply((config) => {
      expect(config.params).toEqual({ instanceId: 'instance-a', brokerName: 'broker-a' });
      expect(config.signal).toBe(signal);
      return [
        200,
        {
          code: 0,
          data: { agreement: 'PARTIAL', membership: null, membershipError: 'Unavailable' },
        },
      ];
    });
    expect((await inspectControllerReplicas('instance-a', 'broker-a', signal)).agreement).toBe(
      'PARTIAL',
    );
    expect(mock.history.post).toHaveLength(0);
  });
  it('rejects a failed inspection without treating it as an empty healthy cluster', async () => {
    mock
      .onGet('/brokers/controller-replicas')
      .reply(200, { code: 502, message: 'Broker configuration is unavailable' });
    await expect(inspectControllerReplicas('instance-a', 'broker-a')).rejects.toThrow(
      'Broker configuration is unavailable',
    );
  });
});
