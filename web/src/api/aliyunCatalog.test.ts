/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
import MockAdapter from 'axios-mock-adapter';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import client from './client';
import { listAliyunInstances, listAliyunRegions } from './aliyunCatalog';

const mock = new MockAdapter(client);

describe('aliyunCatalog API', () => {
  beforeEach(() => {
    mock.reset();
    vi.stubGlobal('localStorage', { getItem: vi.fn().mockReturnValue(null) });
  });

  afterEach(() => {
    mock.reset();
    vi.unstubAllGlobals();
  });

  it('lists regions for a credential', async () => {
    mock.onGet('/cloud/aliyun/regions').reply((config) => {
      expect(config.params).toEqual({ credentialId: 9 });
      return [200, {
        code: 200,
        data: [{ regionId: 'cn-hangzhou', regionName: '华东1（杭州）' }],
      }];
    });

    const regions = await listAliyunRegions(9);

    expect(regions[0].regionId).toBe('cn-hangzhou');
  });

  it('lists cloud instances with credential and region params', async () => {
    mock.onGet('/cloud/aliyun/instances').reply((config) => {
      expect(config.params).toMatchObject({ credentialId: 9, regionId: 'cn-hangzhou' });
      return [
        200,
        {
          code: 200,
          data: [
            {
              instanceId: 'rmq-cn-xxx',
              instanceName: 'prod-mq',
              status: 'RUNNING',
              regionId: 'cn-hangzhou',
            },
          ],
        },
      ];
    });

    const instances = await listAliyunInstances(9, 'cn-hangzhou');

    expect(instances[0].instanceId).toBe('rmq-cn-xxx');
  });

  it('omits the search param when none is given', async () => {
    mock.onGet('/cloud/aliyun/instances').reply((config) => {
      expect(config.params).toEqual({ credentialId: 9, regionId: 'cn-hangzhou' });
      expect(config.params).not.toHaveProperty('search');
      return [200, { code: 200, data: [] }];
    });

    await expect(listAliyunInstances(9, 'cn-hangzhou')).resolves.toEqual([]);
  });

  it('forwards the optional search term for instances', async () => {
    mock.onGet('/cloud/aliyun/instances').reply((config) => {
      expect(config.params).toEqual({
        credentialId: 9,
        regionId: 'cn-hangzhou',
        search: 'prod',
      });
      return [200, { code: 200, data: [] }];
    });

    await expect(listAliyunInstances(9, 'cn-hangzhou', 'prod')).resolves.toEqual([]);
  });

  it('returns an empty region list when no regions are reachable', async () => {
    mock.onGet('/cloud/aliyun/regions').reply(200, { code: 200, data: [] });

    await expect(listAliyunRegions(3)).resolves.toEqual([]);
  });
});
