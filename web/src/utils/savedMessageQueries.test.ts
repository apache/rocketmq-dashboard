/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */

import { beforeEach, describe, expect, it } from 'vitest';
import MockAdapter from 'axios-mock-adapter';
import client from '../api/client';
import {
  addSavedMessageQuery,
  loadSavedMessageQueries,
  renameSavedMessageQuery,
  removeSavedMessageQuery,
} from './savedMessageQueries';

const mock = new MockAdapter(client);
const endpoint = '/query-history/named-messages';
const draft = { instanceId: 'alpha', mode: 'key' as const, topic: 'orders', key: 'key-1' };
describe('服务端命名查询 API', () => {
  beforeEach(() => mock.reset());
  it('使用实例参数读取共享列表', async () => {
    mock
      .onGet(endpoint, { params: { instanceId: 'alpha' } })
      .reply(200, { data: [{ ...draft, id: 'one', name: 'Orders' }] });
    expect(await loadSavedMessageQueries('alpha')).toEqual([
      expect.objectContaining({ name: 'Orders' }),
    ]);
  });
  it('保存条件时不执行消息查询', async () => {
    mock.onPost(endpoint).reply(200, { data: null });
    await addSavedMessageQuery(' Orders ', draft);
    expect(JSON.parse(mock.history.post[0].data)).toEqual({ ...draft, name: 'Orders' });
    expect(mock.history.get).toHaveLength(0);
  });
  it('重命名和删除均绑定实例与查询 ID', async () => {
    mock.onPut(`${endpoint}/one`).reply(200, {});
    mock.onDelete(`${endpoint}/one`).reply(200, {});
    await renameSavedMessageQuery('alpha', 'one', ' Updated ');
    await removeSavedMessageQuery('alpha', 'one');
    expect(mock.history.put[0].params).toEqual({ instanceId: 'alpha' });
    expect(JSON.parse(mock.history.put[0].data)).toEqual({ name: 'Updated' });
    expect(mock.history.delete[0].params).toEqual({ instanceId: 'alpha' });
  });
  it('保留服务端冲突与读取失败', async () => {
    mock.onPost(endpoint).reply(409, { message: 'duplicate' });
    mock.onGet(endpoint).reply(503, {});
    await expect(addSavedMessageQuery('Orders', draft)).rejects.toThrow();
    await expect(loadSavedMessageQueries('alpha')).rejects.toThrow();
  });
});
