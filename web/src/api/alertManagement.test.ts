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
import {
  createAlertRule,
  deleteAlertRule,
  exportAlertRulesYaml,
  listAlertRules,
  queryAlertRules,
  toggleAlertRule,
  updateAlertRule,
} from './alertManagement';

const mock = new MockAdapter(client);

describe('AlertManagement API', () => {
  beforeEach(() => {
    mock.reset();
    vi.stubGlobal('localStorage', { getItem: vi.fn().mockReturnValue(null) });
  });

  afterEach(() => {
    mock.reset();
    vi.unstubAllGlobals();
  });

  it('queries alert rules data', async () => {
    const rulesYaml =
      'groups:\n  - name: test\n    rules:\n      - alert: HighCPU\n        expr: cpu > 80';
    mock.onGet('/alert-rules/export').reply(200, { code: 200, data: { rules: rulesYaml } });

    const result = await queryAlertRules();
    expect(result.rules).toBe(rulesYaml);
    expect(result.rules).toContain('HighCPU');
  });

  it('handles empty alert rules', async () => {
    mock.onGet('/alert-rules/export').reply(200, { code: 200, data: { rules: '' } });

    const result = await exportAlertRulesYaml();
    expect(result.rules).toBe('');
  });

  it('exports only the selected persisted alert rules', async () => {
    mock.onGet('/alert-rules/export').reply((config) => {
      expect(config.params).toEqual({ ids: 'rule-1,rule-2' });
      return [200, { code: 200, data: { rules: 'groups:\n' } }];
    });

    await expect(exportAlertRulesYaml(['rule-1', 'rule-2'])).resolves.toEqual({
      rules: 'groups:\n',
    });
  });

  it('lists persisted alert rules', async () => {
    mock.onGet('/alert-rules').reply(200, {
      code: 200,
      data: [{ id: 'rule-1', name: 'High Lag', metric: 'lag', enabled: true }],
    });

    await expect(listAlertRules()).resolves.toEqual([
      { id: 'rule-1', name: 'High Lag', metric: 'lag', enabled: true },
    ]);
  });

  it('creates persisted alert rules', async () => {
    const request = {
      name: 'High Lag',
      metric: 'rocketmq_consumer_lag_messages',
      operator: '>',
      threshold: 1000,
      duration: '5m',
      enabled: true,
    };
    mock.onPost('/alert-rules/create').reply((config) => {
      expect(JSON.parse(config.data as string)).toEqual(request);
      return [200, { code: 200, data: { ...request, id: 'rule-1' } }];
    });

    await expect(createAlertRule(request)).resolves.toMatchObject({ id: 'rule-1' });
  });

  it('updates persisted alert rules', async () => {
    const request = {
      id: 'rule-1',
      name: 'High Lag',
      metric: 'rocketmq_consumer_lag_messages',
      operator: '>',
      threshold: 2000,
      duration: '5m',
      enabled: true,
    };
    mock.onPost('/alert-rules/update').reply((config) => {
      expect(JSON.parse(config.data as string)).toEqual(request);
      return [200, { code: 200, data: request }];
    });

    await expect(updateAlertRule(request)).resolves.toEqual(request);
  });

  it('toggles persisted alert rules', async () => {
    mock.onPost('/alert-rules/toggle').reply((config) => {
      expect(JSON.parse(config.data as string)).toEqual({ id: 'rule-1', enabled: false });
      return [200, { code: 200, data: { id: 'rule-1', name: 'High Lag', enabled: false } }];
    });

    await expect(toggleAlertRule('rule-1', false)).resolves.toMatchObject({
      id: 'rule-1',
      enabled: false,
    });
  });

  it('deletes persisted alert rules', async () => {
    mock.onPost('/alert-rules/delete').reply((config) => {
      expect(JSON.parse(config.data as string)).toEqual({ id: 'rule-1' });
      return [200, { code: 200, data: null }];
    });

    await expect(deleteAlertRule('rule-1')).resolves.toBeUndefined();
  });
});
