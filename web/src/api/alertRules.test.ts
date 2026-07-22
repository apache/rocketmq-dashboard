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
  listAlertRules,
  toggleAlertRule,
  updateAlertRule,
} from './ops';
import type { AlertRule } from './ops';

const mock = new MockAdapter(client);
const rule: AlertRule = {
  id: 'rule-1',
  name: 'Disk usage',
  metric: '磁盘使用率',
  operator: '>',
  threshold: 80,
  thresholdUnit: '%',
  duration: '5分钟',
  channels: ['email'],
  enabled: true,
  lastTriggered: null,
  description: 'Warn before disk exhaustion',
};

describe('alert rules API', () => {
  beforeEach(() => {
    mock.reset();
    vi.stubGlobal('localStorage', { getItem: vi.fn().mockReturnValue(null) });
  });

  afterEach(() => {
    mock.reset();
    vi.unstubAllGlobals();
  });

  it('loads and unwraps alert rules', async () => {
    mock.onGet('/alert-rules').reply(200, { code: 200, data: [rule] });

    await expect(listAlertRules()).resolves.toEqual([rule]);
  });

  it('returns the backend records for create, update, and toggle', async () => {
    const updated = { ...rule, threshold: 90, enabled: false };
    mock.onPost('/alert-rules/create').reply((config) => {
      expect(JSON.parse(config.data)).toMatchObject({ name: rule.name });
      return [200, { code: 200, data: rule }];
    });
    mock.onPost('/alert-rules/update').reply((config) => {
      expect(JSON.parse(config.data)).toMatchObject({ id: rule.id, threshold: 90 });
      return [200, { code: 200, data: updated }];
    });
    mock.onPost('/alert-rules/toggle').reply((config) => {
      expect(JSON.parse(config.data)).toEqual({ id: rule.id, enabled: false });
      return [200, { code: 200, data: updated }];
    });

    await expect(createAlertRule({ name: rule.name })).resolves.toEqual(rule);
    await expect(updateAlertRule(updated)).resolves.toEqual(updated);
    await expect(toggleAlertRule(rule.id, false)).resolves.toEqual(updated);
  });

  it('sends the rule id when deleting', async () => {
    mock.onPost('/alert-rules/delete').reply((config) => {
      expect(JSON.parse(config.data)).toEqual({ id: rule.id });
      return [200, { code: 200, data: null }];
    });

    await expect(deleteAlertRule(rule.id)).resolves.toBeUndefined();
  });
});
