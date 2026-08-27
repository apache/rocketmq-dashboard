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
  queryOpsHomePage,
  updateNameSvrAddr,
  addNameSvrAddr,
  deleteNameSvrAddr,
  updateIsVIPChannel,
  updateUseTLS,
  listAlertRules,
  listAlertRulesPage,
  listAlertRuleRuntime,
  exportAlertRulesTransfer,
  importAlertRulesTransfer,
  listNativeAlertMetrics,
  createAlertRule,
  updateAlertRule,
  toggleAlertRule,
  deleteAlertRule,
  bulkDeleteAlertRules,
  bulkToggleAlertRules,
  listSystemAlerts,
  listRelatedSystemAlerts,
  listSystemAlertsPage,
  acknowledgeAlert,
  clearAcknowledgedAlerts,
  listAlertDeliveries,
  listAlertSilences,
  createAlertSilence,
  deleteAlertSilence,
  listAuditRecords,
  cleanupAuditLogs,
} from './ops';

const mock = new MockAdapter(client);

describe('Ops API - NameServer operations', () => {
  beforeEach(() => {
    mock.reset();
    vi.stubGlobal('localStorage', { getItem: vi.fn().mockReturnValue(null) });
  });

  afterEach(() => {
    mock.reset();
    vi.unstubAllGlobals();
  });

  it('queries ops home page data', async () => {
    const data = {
      namesvrAddrList: ['127.0.0.1:9876', '127.0.0.1:9877'],
      useVIPChannel: true,
      useTLS: false,
      currentNamesrv: '127.0.0.1:9876',
    };
    mock.onGet('/ops/homePage').reply(200, { code: 200, data });

    const result = await queryOpsHomePage();
    expect(result.namesvrAddrList).toHaveLength(2);
    expect(result.useVIPChannel).toBe(true);
    expect(result.useTLS).toBe(false);
  });

  it('updates NameServer address', async () => {
    mock.onPost('/ops/updateNameSvrAddr').reply((config) => {
      const body = JSON.parse(config.data);
      expect(body.namesrvAddr).toBe('10.0.0.1:9876');
      return [200, { code: 200 }];
    });

    await updateNameSvrAddr('10.0.0.1:9876');
  });

  it('adds a NameServer address', async () => {
    mock.onPost('/ops/addNameSvrAddr').reply((config) => {
      const body = JSON.parse(config.data);
      expect(body.namesrvAddr).toBe('10.0.0.2:9876');
      return [200, { code: 200 }];
    });

    await addNameSvrAddr('10.0.0.2:9876');
  });

  it('deletes a NameServer address', async () => {
    mock.onPost('/ops/deleteNameSvrAddr').reply((config) => {
      const body = JSON.parse(config.data);
      expect(body.namesrvAddr).toBe('10.0.0.2:9876');
      return [200, { code: 200 }];
    });

    await deleteNameSvrAddr('10.0.0.2:9876');
  });

  it('updates VIP channel setting', async () => {
    mock.onPost('/ops/updateIsVIPChannel').reply((config) => {
      const body = JSON.parse(config.data);
      expect(body.useVIPChannel).toBe(false);
      return [200, { code: 200 }];
    });

    await updateIsVIPChannel(false);
  });

  it('updates TLS setting', async () => {
    mock.onPost('/ops/updateUseTLS').reply((config) => {
      const body = JSON.parse(config.data);
      expect(body.useTLS).toBe(true);
      return [200, { code: 200 }];
    });

    await updateUseTLS(true);
  });
});

describe('Ops API - Alert Rules', () => {
  beforeEach(() => {
    mock.reset();
    vi.stubGlobal('localStorage', { getItem: vi.fn().mockReturnValue(null) });
  });
  afterEach(() => {
    mock.reset();
    vi.unstubAllGlobals();
  });

  it('lists alert rules', async () => {
    const rules = [
      {
        id: 1,
        name: 'HighCPU',
        metric: 'cpu',
        operator: '>',
        threshold: 80,
        thresholdUnit: '%',
        duration: '5m',
        channels: ['email'],
        enabled: true,
        lastTriggered: null,
        description: 'CPU alert',
      },
    ];
    mock.onGet('/cluster-alert-rules').reply(200, { code: 200, data: rules });

    const result = await listAlertRules();
    expect(result).toHaveLength(1);
    expect(result[0].name).toBe('HighCPU');
  });

  it('lists alert rules through the paged domain endpoint', async () => {
    const page = { items: [], total: 42, page: 2, size: 10 };
    mock
      .onGet('/business-alert-rules/page', { params: { page: 2, pageSize: 10, search: 'lag' } })
      .reply(200, { code: 200, data: page });

    await expect(
      listAlertRulesPage('BUSINESS', { page: 2, pageSize: 10, search: 'lag' }),
    ).resolves.toEqual(page);
  });

  it('loads runtime state through the selected alert domain', async () => {
    const runtime = [{ ruleId: 1, fingerprint: 'broker-a', status: 'FIRING', consecutiveHits: 3 }];
    mock.onGet('/cluster-alert-rules/runtime').reply(200, { code: 200, data: runtime });

    await expect(listAlertRuleRuntime()).resolves.toEqual(runtime);
  });

  it('creates an alert rule', async () => {
    mock.onPost('/cluster-alert-rules/create').reply(200, { code: 200 });
    await createAlertRule({ name: 'TestAlert', metric: 'memory', operator: '>', threshold: 90 });
  });

  it('routes business alert rules to the business rule API', async () => {
    mock.onGet('/business-alert-rules').reply(200, { code: 200, data: [] });
    mock.onPost('/business-alert-rules/create').reply(200, { code: 200, data: { id: 2 } });

    await expect(listAlertRules('BUSINESS')).resolves.toEqual([]);
    await expect(createAlertRule({ name: 'Lag' }, 'BUSINESS')).resolves.toEqual({ id: 2 });
  });

  it('transfers native alert rules through the selected alert domain', async () => {
    const transfer = {
      version: 1,
      domain: 'CLUSTER' as const,
      rules: [
        {
          name: 'Disk usage',
          metric: 'broker.disk.usage',
          operator: '>',
          threshold: 85,
          duration: '5m',
          channels: ['dingtalk'],
          enabled: true,
          description: 'disk',
        },
      ],
    };
    mock.onGet('/cluster-alert-rules/transfer').reply(200, { code: 200, data: transfer });
    mock.onPost('/cluster-alert-rules/import').reply((config) => {
      expect(JSON.parse(config.data)).toEqual(transfer);
      return [200, { code: 200, data: [{ ...transfer.rules[0], id: 1, lastTriggered: null }] }];
    });

    await expect(exportAlertRulesTransfer()).resolves.toEqual(transfer);
    await expect(importAlertRulesTransfer(transfer)).resolves.toHaveLength(1);
  });

  it('loads native metric capabilities for an instance and domain', async () => {
    mock
      .onGet('/native-alert-metrics', { params: { instanceId: 'local', domain: 'BUSINESS' } })
      .reply(200, {
        code: 200,
        data: [
          {
            key: 'consumer.lag.total',
            label: 'Consumer lag total',
            thresholdUnit: 'messages',
            supportsConsumerGroup: true,
          },
        ],
      });

    await expect(listNativeAlertMetrics('local', 'BUSINESS')).resolves.toMatchObject([
      { key: 'consumer.lag.total' },
    ]);
  });

  it('updates an alert rule', async () => {
    mock.onPost('/cluster-alert-rules/update').reply((config) => {
      const body = JSON.parse(config.data);
      expect(body.id).toBe(1);
      expect(body.threshold).toBe(95);
      return [200, { code: 200 }];
    });
    await updateAlertRule({ id: 1, threshold: 95 } as never);
  });

  it('toggles an alert rule', async () => {
    mock.onPost('/cluster-alert-rules/toggle').reply((config) => {
      const body = JSON.parse(config.data);
      expect(body.id).toBe(1);
      expect(body.enabled).toBe(false);
      return [200, { code: 200 }];
    });
    await toggleAlertRule(1, false);
  });

  it('deletes an alert rule', async () => {
    mock.onPost('/cluster-alert-rules/delete').reply((config) => {
      const body = JSON.parse(config.data);
      expect(body.id).toBe(1);
      return [200, { code: 200 }];
    });
    await deleteAlertRule(1);
  });

  it('submits bulk alert rule operations in one request', async () => {
    const result = { succeededIds: [1], failures: { '999': 'not found' }, updatedRules: [] };
    mock.onPost('/cluster-alert-rules/bulk-toggle').reply((config) => {
      expect(JSON.parse(config.data)).toEqual({ ids: [1, 999], enabled: false });
      return [200, { code: 200, data: result }];
    });
    mock.onPost('/cluster-alert-rules/bulk-delete').reply(200, { code: 200, data: result });

    await expect(bulkToggleAlertRules([1, 999], false)).resolves.toEqual(result);
    await expect(bulkDeleteAlertRules([1, 999])).resolves.toEqual(result);
  });
});

describe('Ops API - System Alerts & Audit', () => {
  beforeEach(() => {
    mock.reset();
    vi.stubGlobal('localStorage', { getItem: vi.fn().mockReturnValue(null) });
  });
  afterEach(() => {
    mock.reset();
    vi.unstubAllGlobals();
  });

  it('lists system alerts', async () => {
    mock.onGet('/system-alerts').reply(200, {
      code: 200,
      data: [
        {
          id: 1,
          level: 'critical',
          title: 'Disk Full',
          description: 'Disk usage > 95%',
          time: '2026-01-01',
          acknowledged: false,
        },
      ],
    });
    const result = await listSystemAlerts();
    expect(result[0].level).toBe('critical');
  });

  it('loads related system alerts for an event', async () => {
    mock.onGet('/system-alerts/9/related').reply(200, { code: 200, data: [{ id: 10 }] });

    await expect(listRelatedSystemAlerts(9)).resolves.toEqual([{ id: 10 }]);
  });

  it('lists paged system alerts with server-side filters', async () => {
    mock.onGet('/system-alerts/page').reply((config) => {
      expect(config.params).toEqual({
        domain: 'BUSINESS',
        transition: 'FIRING',
        page: 2,
        pageSize: 10,
      });
      return [200, { code: 200, data: { items: [], total: 11, page: 2, size: 10 } }];
    });

    const result = await listSystemAlertsPage({
      domain: 'BUSINESS',
      transition: 'FIRING',
      page: 2,
      pageSize: 10,
    });
    expect(result.total).toBe(11);
  });

  it('acknowledges an alert', async () => {
    mock.onPost('/system-alerts/acknowledge').reply((config) => {
      expect(JSON.parse(config.data).id).toBe(1);
      return [200, { code: 200 }];
    });
    await acknowledgeAlert(1);
  });

  it('clears acknowledged alerts', async () => {
    mock.onPost('/system-alerts/clear-acknowledged').reply(200, { code: 200 });
    await clearAcknowledgedAlerts();
  });

  it('lists delivery state for an alert event', async () => {
    mock.onGet('/system-alerts/1/deliveries').reply(200, {
      code: 200,
      data: [{ id: 1, channel: 'dingtalk', status: 'DELIVERED', attemptCount: 1 }],
    });
    await expect(listAlertDeliveries(1)).resolves.toEqual([
      { id: 1, channel: 'dingtalk', status: 'DELIVERED', attemptCount: 1 },
    ]);
  });

  it('manages alert silences', async () => {
    const silence = {
      id: 2,
      startsAt: '2026-08-22T10:00',
      endsAt: '2026-08-22T11:00',
      createdBy: 'admin',
    };
    mock.onGet('/alert-silences').reply(200, { code: 200, data: [silence] });
    mock.onPost('/alert-silences').reply(200, { code: 200, data: silence });
    mock.onDelete('/alert-silences/2').reply(200, { code: 200 });
    await expect(listAlertSilences()).resolves.toEqual([silence]);
    await expect(createAlertSilence(silence)).resolves.toEqual(silence);
    await expect(deleteAlertSilence(2)).resolves.toBeUndefined();
  });

  it('lists audit records with params', async () => {
    mock.onGet('/audit-logs').reply(200, {
      code: 200,
      data: {
        items: [
          {
            id: 1,
            timestamp: '2026-01-01',
            operator: 'admin',
            operationType: 'CREATE',
            resourceType: 'TOPIC',
            target: 'topic',
            clusterId: 'prod-cn',
            detail: 'Created topic',
            result: 'SUCCESS',
            errorMessage: '',
          },
        ],
        total: 1,
        page: 1,
        size: 20,
      },
    });
    const result = await listAuditRecords({ page: 1 });
    expect(result.items).toHaveLength(1);
    expect(result.total).toBe(1);
    expect(result.page).toBe(1);
    expect(result.size).toBe(20);
  });

  it('cleans up audit logs', async () => {
    mock.onPost('/audit-logs/cleanup').reply((config) => {
      expect(JSON.parse(config.data).beforeDays).toBe(30);
      return [200, { code: 200 }];
    });
    await cleanupAuditLogs(30);
  });
});
