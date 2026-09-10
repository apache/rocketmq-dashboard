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
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import client from './client';
import {
  addNameSvrAddr,
  deleteNameSvrAddr,
  queryOpsHomePage,
  updateIsVIPChannel,
  updateNameSvrAddr,
  updateUseTLS,
} from './ops';

const mock = new MockAdapter(client);

describe('ops home API', () => {
  beforeEach(() => mock.reset());
  afterEach(() => mock.reset());

  it('loads the ops home page state', async () => {
    mock.onGet('/ops/homePage').reply(200, {
      code: 200,
      data: {
        configurationAvailable: false,
        unavailableReason: 'not connected',
        namesvrAddrList: [],
        useVIPChannel: false,
        useTLS: false,
        currentNamesrv: '',
      },
    });

    await expect(queryOpsHomePage()).resolves.toMatchObject({ configurationAvailable: false });
  });

  it('updates the nameserver address set', async () => {
    mock.onPost('/ops/updateNameSvrAddr').reply((config) => {
      expect(JSON.parse(config.data)).toEqual({ namesrvAddr: '10.0.0.1:9876' });
      return [200, { code: 200, data: null }];
    });
    mock.onPost('/ops/addNameSvrAddr').reply((config) => {
      expect(JSON.parse(config.data)).toEqual({ namesrvAddr: '10.0.0.2:9876' });
      return [200, { code: 200, data: null }];
    });
    mock.onPost('/ops/deleteNameSvrAddr').reply((config) => {
      expect(JSON.parse(config.data)).toEqual({ namesrvAddr: '10.0.0.3:9876' });
      return [200, { code: 200, data: null }];
    });

    await expect(updateNameSvrAddr('10.0.0.1:9876')).resolves.toBeUndefined();
    await expect(addNameSvrAddr('10.0.0.2:9876')).resolves.toBeUndefined();
    await expect(deleteNameSvrAddr('10.0.0.3:9876')).resolves.toBeUndefined();
  });

  it('toggles the vip channel and TLS flags', async () => {
    mock.onPost('/ops/updateIsVIPChannel').reply((config) => {
      expect(JSON.parse(config.data)).toEqual({ useVIPChannel: true });
      return [200, { code: 200, data: null }];
    });
    mock.onPost('/ops/updateUseTLS').reply((config) => {
      expect(JSON.parse(config.data)).toEqual({ useTLS: true });
      return [200, { code: 200, data: null }];
    });

    await expect(updateIsVIPChannel(true)).resolves.toBeUndefined();
    await expect(updateUseTLS(true)).resolves.toBeUndefined();
  });
});
