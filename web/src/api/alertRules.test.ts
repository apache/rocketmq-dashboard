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
  bulkDeleteAlertRules,
  bulkToggleAlertRules,
  createAlertRule,
  deleteAlertRule,
  exportAlertRulesTransfer,
  importAlertRulesTransfer,
  listAlertRules,
  listAlertRuleRuntime,
  listAlertRulesPage,
  testAlertRule,
  toggleAlertRule,
  updateAlertRule,
} from './ops';
import type { AlertRule } from './ops';

const mock = new MockAdapter(client);
const rule: AlertRule = {
  id: 7,
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
    mock.onGet('/cluster-alert-rules').reply(200, { code: 200, data: [rule] });

    await expect(listAlertRules()).resolves.toEqual([rule]);
  });

  it('returns the backend records for create, update, and toggle', async () => {
    const updated = { ...rule, threshold: 90, enabled: false };
    mock.onPost('/cluster-alert-rules/create').reply((config) => {
      expect(JSON.parse(config.data)).toMatchObject({ name: rule.name });
      return [200, { code: 200, data: rule }];
    });
    mock.onPost('/cluster-alert-rules/update').reply((config) => {
      expect(JSON.parse(config.data)).toMatchObject({ id: rule.id, threshold: 90 });
      return [200, { code: 200, data: updated }];
    });
    mock.onPost('/cluster-alert-rules/toggle').reply((config) => {
      expect(JSON.parse(config.data)).toEqual({ id: rule.id, enabled: false });
      return [200, { code: 200, data: updated }];
    });

    await expect(createAlertRule({ name: rule.name })).resolves.toEqual(rule);
    await expect(updateAlertRule(updated)).resolves.toEqual(updated);
    await expect(toggleAlertRule(rule.id, false)).resolves.toEqual(updated);
  });

  it('sends the rule id when deleting', async () => {
    mock.onPost('/cluster-alert-rules/delete').reply((config) => {
      expect(JSON.parse(config.data)).toEqual({ id: rule.id });
      return [200, { code: 200, data: null }];
    });

    await expect(deleteAlertRule(rule.id)).resolves.toBeUndefined();
  });

  it('pages rules and loads their runtime state', async () => {
    mock.onGet('/cluster-alert-rules/page').reply((config) => {
      expect(config.params).toEqual({ search: 'disk', page: 2, pageSize: 20 });
      return [
        200,
        { code: 200, data: { items: [rule], total: 1, page: 2, size: 20 } },
      ];
    });
    mock.onGet('/cluster-alert-rules/runtime').reply(200, {
      code: 200,
      data: [{ id: rule.id, status: 'FIRING' }],
    });

    await expect(
      listAlertRulesPage('CLUSTER', { search: 'disk', page: 2, pageSize: 20 }),
    ).resolves.toMatchObject({ total: 1 });
    await expect(listAlertRuleRuntime()).resolves.toEqual([{ id: rule.id, status: 'FIRING' }]);
  });

  it('exports and imports the transfer document', async () => {
    const transfer = { version: 1, domain: 'CLUSTER', rules: [] };
    mock.onGet('/cluster-alert-rules/transfer').reply(200, { code: 200, data: transfer });
    mock.onPost('/cluster-alert-rules/import').reply((config) => {
      expect(JSON.parse(config.data)).toEqual(transfer);
      return [200, { code: 200, data: [] }];
    });

    await expect(exportAlertRulesTransfer()).resolves.toEqual(transfer);
    await expect(importAlertRulesTransfer(transfer)).resolves.toEqual([]);
  });

  it('bulk-toggles, bulk-deletes and natively tests rules', async () => {
    const bulk = { succeeded: [1], failed: [] };
    mock.onPost('/cluster-alert-rules/bulk-toggle').reply((config) => {
      expect(JSON.parse(config.data)).toEqual({ ids: [1, 2], enabled: true });
      return [200, { code: 200, data: bulk }];
    });
    mock.onPost('/cluster-alert-rules/bulk-delete').reply((config) => {
      expect(JSON.parse(config.data)).toEqual({ ids: [1, 2] });
      return [200, { code: 200, data: bulk }];
    });
    mock.onPost('/cluster-alert-rules/test').reply((config) => {
      expect(JSON.parse(config.data)).toMatchObject({ id: rule.id });
      return [200, { code: 200, data: { samples: [] } }];
    });

    await expect(bulkToggleAlertRules([1, 2], true)).resolves.toEqual(bulk);
    await expect(bulkDeleteAlertRules([1, 2])).resolves.toEqual(bulk);
    await expect(testAlertRule({ id: rule.id })).resolves.toEqual({ samples: [] });
  });
});
