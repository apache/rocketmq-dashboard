/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
import { beforeEach, expect, it, vi } from 'vitest';
import client from './client';
import { inspectStaticTopicMapping } from './staticTopicMapping';
vi.mock('./client', () => ({ default: { get: vi.fn(), post: vi.fn() } }));
beforeEach(() => vi.clearAllMocks());
it('reads mappings with the exact topic, instance and cancellation signal', async () => {
  const payload = { nodes: [{ local: { epoch: '9007199254740993' } }] };
  vi.mocked(client.get).mockResolvedValue({ data: { data: payload } });
  const params = { instanceId: 'instance-a', topic: 'orders' };
  const signal = new AbortController().signal;
  expect(await inspectStaticTopicMapping(params, signal)).toEqual(payload);
  expect(client.get).toHaveBeenCalledWith('/static-topic-mappings', { params, signal });
  expect(client.post).not.toHaveBeenCalled();
});
it('propagates an unavailable route without replacing it with an empty mapping', async () => {
  vi.mocked(client.get).mockRejectedValue(new Error('route unavailable'));
  await expect(inspectStaticTopicMapping({ instanceId: 'a', topic: 'orders' })).rejects.toThrow(
    'route unavailable',
  );
});
