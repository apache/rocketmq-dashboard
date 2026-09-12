/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
import MockAdapter from 'axios-mock-adapter';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import client from './client';
import { inspectConsumerTimeSpan } from './consumerTimeSpan';

const mock = new MockAdapter(client);
const params = { instanceId: 'instance-a', topic: 'orders', group: 'group-a' };

describe('Consumer time span API', () => {
  beforeEach(() => {
    mock.reset();
    vi.stubGlobal('localStorage', { getItem: vi.fn().mockReturnValue(null) });
  });
  afterEach(() => vi.unstubAllGlobals());

  it('uses scoped GET and retains exact strings and the cancellation signal', async () => {
    const controller = new AbortController();
    mock.onGet('/consumer-time-spans').reply((config) => {
      expect(config.params).toEqual(params);
      expect(config.signal).toBe(controller.signal);
      return [200, { code: 200, data: { queues: [{ consumerOffset: '9007199254740993' }] } }];
    });
    await expect(inspectConsumerTimeSpan(params, controller.signal)).resolves.toEqual({
      queues: [{ consumerOffset: '9007199254740993' }],
    });
    expect(mock.history.post).toHaveLength(0);
  });

  it('propagates broker failure without manufacturing an empty timeline', async () => {
    mock.onGet('/consumer-time-spans').reply(200, { code: 502, message: 'Broker offline' });
    await expect(inspectConsumerTimeSpan(params)).rejects.toThrow('Broker offline');
  });
});
