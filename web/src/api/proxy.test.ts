/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import MockAdapter from 'axios-mock-adapter';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import client from './client';
import { queryProxyHomePage, addProxyAddr } from './proxy';

const mock = new MockAdapter(client);

describe('Proxy API', () => {
  beforeEach(() => {
    mock.reset();
    vi.stubGlobal('localStorage', { getItem: vi.fn().mockReturnValue(null) });
  });

  afterEach(() => {
    mock.reset();
    vi.unstubAllGlobals();
  });

  it('queries proxy home page data', async () => {
    const data = {
      proxyAddrList: ['192.168.1.1:8081', '192.168.1.2:8081'],
      currentProxyAddr: '192.168.1.1:8081',
    };
    mock.onGet('/proxy/homePage.query').reply(200, { code: 200, data });

    const result = await queryProxyHomePage();
    expect(result.proxyAddrList).toHaveLength(2);
    expect(result.currentProxyAddr).toBe('192.168.1.1:8081');
  });

  it('handles empty proxy address list', async () => {
    mock
      .onGet('/proxy/homePage.query')
      .reply(200, { code: 200, data: { proxyAddrList: [], currentProxyAddr: '' } });

    const result = await queryProxyHomePage();
    expect(result.proxyAddrList).toHaveLength(0);
    expect(result.currentProxyAddr).toBe('');
  });

  it('adds a proxy address with form-urlencoded content type', async () => {
    mock.onPost('/proxy/addProxyAddr.do').reply((config) => {
      expect(config.headers?.['Content-Type']).toBe('application/x-www-form-urlencoded');
      expect(config.data).toBe('newProxyAddr=192.168.1.3%3A8081');
      return [200, { code: 200 }];
    });

    await addProxyAddr('192.168.1.3:8081');
  });

  it('adds a proxy address with localhost', async () => {
    mock.onPost('/proxy/addProxyAddr.do').reply((config) => {
      expect(config.data).toBe('newProxyAddr=localhost%3A8081');
      return [200, { code: 200 }];
    });

    await addProxyAddr('localhost:8081');
  });
});
