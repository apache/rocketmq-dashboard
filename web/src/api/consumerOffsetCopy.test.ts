/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
import MockAdapter from 'axios-mock-adapter';
import { afterEach, describe, expect, it } from 'vitest';
import client from './client';
import { applyConsumerOffsetCopy, previewConsumerOffsetCopy } from './consumerOffsetCopy';

const mock = new MockAdapter(client);
const selection = {
  instanceId: 'instance-a',
  topic: 'orders',
  sourceGroup: 'source',
  targetGroup: 'target',
};
const expected = {
  brokerName: 'broker-a',
  brokerAddr: 'master:10911',
  queueId: 0,
  sourceOffset: '9007199254740993',
  targetOffset: null,
};
afterEach(() => mock.reset());

describe('Consumer offset copy API', () => {
  it('previews through a selected-instance GET without sending writes', async () => {
    mock.onGet('/consumer-offset-copy').reply((config) => {
      expect(config.params).toEqual(selection);
      return [200, { code: 0, data: { ...selection, queues: [] } }];
    });
    expect((await previewConsumerOffsetCopy(selection)).queues).toEqual([]);
    expect(mock.history.post).toHaveLength(0);
  });

  it('posts the reviewed offsets without numeric conversion and returns partial outcomes', async () => {
    const controller = new AbortController();
    mock.onPost('/consumer-offset-copy').reply((config) => {
      expect(JSON.parse(config.data)).toEqual({ ...selection, expected: [expected] });
      expect(config.signal).toBe(controller.signal);
      expect(config.timeout).toBe(0);
      return [
        200,
        {
          code: 0,
          data: { queues: [{ queue: expected, status: 'UNKNOWN', observedOffset: null }] },
        },
      ];
    });
    const receipt = await applyConsumerOffsetCopy(selection, [expected], controller.signal);
    expect(receipt.queues[0].status).toBe('UNKNOWN');
    expect(mock.history.post).toHaveLength(1);
  });
});
