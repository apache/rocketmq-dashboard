/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
import { beforeEach, expect, it, vi } from 'vitest';
import client from './client';
import { previewRocksdbCheck, submitRocksdbCheck } from './brokerRocksdbCheck';
vi.mock('./client', () => ({ default: { get: vi.fn(), post: vi.fn() } }));
beforeEach(() => vi.clearAllMocks());
const target = { instanceId: 'a', brokerName: 'broker-a', address: 'master:10911' };
it('uses GET only for scope inspection', async () => {
  vi.mocked(client.get).mockResolvedValue({ data: { data: { eligible: true } } });
  const signal = new AbortController().signal;
  expect(await previewRocksdbCheck(target, signal)).toEqual({ eligible: true });
  expect(client.get).toHaveBeenCalledWith('/brokers/rocksdb-check', { params: target, signal });
  expect(client.post).not.toHaveBeenCalled();
});
it('posts an explicit single-topic request with the original checkpoint text', async () => {
  vi.mocked(client.post).mockResolvedValue({ data: { data: { status: 'ACCEPTED' } } });
  const settings = { doubleWriteEnabled: true, loadingStores: ['default', 'defaultRocksDB'] };
  const signal = new AbortController().signal;
  expect(await submitRocksdbCheck(target, 'orders', '1000', settings, signal)).toEqual({
    status: 'ACCEPTED',
  });
  expect(client.post).toHaveBeenCalledWith(
    '/brokers/rocksdb-check',
    {
      ...target,
      topic: 'orders',
      checkFromMillis: '1000',
      expectedSettings: settings,
      confirmed: true,
    },
    { signal, timeout: 0 },
  );
});
