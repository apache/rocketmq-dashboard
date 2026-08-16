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
  createAlertRule,
  updateAlertRule,
  toggleAlertRule,
  deleteAlertRule,
  bulkDeleteAlertRules,
  bulkToggleAlertRules,
  listSystemAlerts,
  acknowledgeAlert,
  clearAcknowledgedAlerts,
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
    mock.onGet('/alert-rules').reply(200, { code: 200, data: rules });

    const result = await listAlertRules();
    expect(result).toHaveLength(1);
    expect(result[0].name).toBe('HighCPU');
  });

  it('creates an alert rule', async () => {
    mock.onPost('/alert-rules/create').reply(200, { code: 200 });
    await createAlertRule({ name: 'TestAlert', metric: 'memory', operator: '>', threshold: 90 });
  });

  it('updates an alert rule', async () => {
    mock.onPost('/alert-rules/update').reply((config) => {
      const body = JSON.parse(config.data);
      expect(body.id).toBe(1);
      expect(body.threshold).toBe(95);
      return [200, { code: 200 }];
    });
    await updateAlertRule({ id: 1, threshold: 95 } as never);
  });

  it('toggles an alert rule', async () => {
    mock.onPost('/alert-rules/toggle').reply((config) => {
      const body = JSON.parse(config.data);
      expect(body.id).toBe(1);
      expect(body.enabled).toBe(false);
      return [200, { code: 200 }];
    });
    await toggleAlertRule(1, false);
  });

  it('deletes an alert rule', async () => {
    mock.onPost('/alert-rules/delete').reply((config) => {
      const body = JSON.parse(config.data);
      expect(body.id).toBe(1);
      return [200, { code: 200 }];
    });
    await deleteAlertRule(1);
  });

  it('submits bulk alert rule operations in one request', async () => {
    const result = { succeededIds: [1], failures: { '999': 'not found' }, updatedRules: [] };
    mock.onPost('/alert-rules/bulk-toggle').reply((config) => {
      expect(JSON.parse(config.data)).toEqual({ ids: [1, 999], enabled: false });
      return [200, { code: 200, data: result }];
    });
    mock.onPost('/alert-rules/bulk-delete').reply(200, { code: 200, data: result });

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
    const page = {
      items: [
        {
          id: 1,
          level: 'critical',
          title: 'Disk Full',
          description: 'Disk usage > 95%',
          time: '2026-01-01',
          acknowledged: false,
        },
      ],
      total: 1,
      page: 1,
      size: 20,
    };
    mock.onGet('/system-alerts').reply(200, {
      code: 200,
      data: page,
    });
    const result = await listSystemAlerts();
    expect(result.items[0].level).toBe('critical');
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
