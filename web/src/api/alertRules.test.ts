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
  listAlertRuleRuntime,
  listAlertRules,
  listAlertRulesPage,
  testAlertRule,
  toggleAlertRule,
  updateAlertRule,
} from './ops';
import type { AlertRule, AlertRuleTransfer } from './ops';

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

  it('routes cluster alert rules to the business endpoint when requested', async () => {
    mock.onGet('/business-alert-rules').reply(200, { code: 200, data: [rule] });
    mock.onGet('/business-alert-rules/runtime').reply(200, {
      code: 200,
      data: [
        {
          ruleId: 7,
          fingerprint: 'fp-7',
          status: 'FIRING',
          consecutiveHits: 3,
          currentValue: 85,
        },
      ],
    });

    await expect(listAlertRules('BUSINESS')).resolves.toEqual([rule]);
    await expect(listAlertRuleRuntime('BUSINESS')).resolves.toEqual([
      expect.objectContaining({ ruleId: 7, status: 'FIRING', consecutiveHits: 3 }),
    ]);
  });

  it('pages alert rules with the supplied query', async () => {
    mock.onGet('/cluster-alert-rules/page').reply((config) => {
      expect(config.params).toEqual({ page: 2, pageSize: 25, enabled: true });
      return [200, { code: 200, data: { items: [rule], total: 1, page: 2, size: 25 } }];
    });

    const page = await listAlertRulesPage('CLUSTER', { page: 2, pageSize: 25, enabled: true });
    expect(page.items).toEqual([rule]);
    expect(page.total).toBe(1);
  });

  it('bulk toggles and bulk deletes with the id lists', async () => {
    const bulkResult = { succeededIds: [7, 8], failures: {}, updatedRules: [rule] };
    mock.onPost('/cluster-alert-rules/bulk-toggle').reply((config) => {
      expect(JSON.parse(config.data)).toEqual({ ids: [7, 8], enabled: false });
      return [200, { code: 200, data: bulkResult }];
    });
    mock.onPost('/cluster-alert-rules/bulk-delete').reply((config) => {
      expect(JSON.parse(config.data)).toEqual({ ids: [7, 8] });
      return [200, { code: 200, data: bulkResult }];
    });

    await expect(bulkToggleAlertRules([7, 8], false)).resolves.toEqual(bulkResult);
    await expect(bulkDeleteAlertRules([7, 8])).resolves.toEqual(bulkResult);
  });

  it('posts rule payloads to the test endpoint', async () => {
    mock.onPost('/cluster-alert-rules/test').reply((config) => {
      expect(JSON.parse(config.data)).toEqual({ id: 7, threshold: 90 });
      return [
        200,
        {
          code: 200,
          data: {
            samples: [
              { labels: { broker: 'broker-0' }, availability: 'ok', currentValue: 90, conditionMet: true },
            ],
          },
        },
      ];
    });

    const result = await testAlertRule({ id: 7, threshold: 90 });
    expect(result.samples).toHaveLength(1);
    expect(result.samples[0].conditionMet).toBe(true);
  });

  it('imports and exports transfer payloads on the transfer endpoints', async () => {
    const transfer: AlertRuleTransfer = {
      version: 1,
      domain: 'CLUSTER',
      rules: [
        {
          name: rule.name,
          metric: rule.metric,
          operator: rule.operator,
          threshold: rule.threshold,
          thresholdUnit: rule.thresholdUnit,
          duration: rule.duration,
          channels: rule.channels,
          enabled: rule.enabled,
          description: rule.description,
        },
      ],
    };
    mock.onGet('/cluster-alert-rules/transfer').reply(200, { code: 200, data: transfer });
    mock.onPost('/cluster-alert-rules/import').reply((config) => {
      expect(JSON.parse(config.data)).toEqual(transfer);
      return [200, { code: 200, data: [rule] }];
    });

    await expect(exportAlertRulesTransfer('CLUSTER')).resolves.toEqual(transfer);
    await expect(importAlertRulesTransfer(transfer, 'CLUSTER')).resolves.toEqual([rule]);
  });
});
