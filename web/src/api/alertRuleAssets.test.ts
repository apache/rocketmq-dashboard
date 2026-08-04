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
import { exportAlertRuleAsset, getAlertRuleAsset, listAlertRuleAssets } from './alertRuleAssets';

const mock = new MockAdapter(client);

describe('alertRuleAssets API', () => {
  beforeEach(() => {
    mock.reset();
    vi.stubGlobal('localStorage', { getItem: vi.fn().mockReturnValue(null) });
  });

  afterEach(() => {
    mock.reset();
    vi.unstubAllGlobals();
  });

  it('lists alert rule assets', async () => {
    const assets = [
      {
        name: 'rocketmq-broker-down',
        group: 'rocketmq-broker.rules',
        ruleCount: 1,
        severities: ['critical'],
      },
      {
        name: 'rocketmq-consumer-lag-high',
        group: 'rocketmq-consumer.rules',
        ruleCount: 1,
        severities: ['warning'],
      },
    ];
    mock.onGet('/alert-rules/assets').reply(200, { code: 200, data: assets });

    await expect(listAlertRuleAssets()).resolves.toEqual(assets);
  });

  it('gets a single asset yaml by name', async () => {
    const yaml = 'groups:\n  - name: rocketmq-broker.rules\n';
    mock.onGet('/alert-rules/assets/rocketmq-broker-down').reply(200, { code: 200, data: yaml });

    await expect(getAlertRuleAsset('rocketmq-broker-down')).resolves.toBe(yaml);
  });

  it('exports an asset as a blob', async () => {
    const blob = new Blob(['groups:\n  - name: rocketmq-broker.rules\n'], { type: 'text/yaml' });
    mock.onGet('/alert-rules/assets/rocketmq-broker-down/export').reply(200, blob);

    const result = await exportAlertRuleAsset('rocketmq-broker-down');
    expect(result).toBeInstanceOf(Blob);
    expect(result.type).toBe('text/yaml');
  });
});
