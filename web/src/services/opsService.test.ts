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

import { afterEach, describe, expect, it, vi } from 'vitest';
import type { AuditRecord } from '../api/ops';
import { mockAuditRecords } from '../mock/audit';
import {
  createAlertRule,
  exportAuditLogs,
  listAlertRules,
  listAuditRecords,
  listSystemAlerts,
  toggleAlertRule,
  updateAlertRule,
} from './opsService';

vi.mock('./dataMode', () => ({ isMockMode: () => true }));
vi.mock('../config', () => ({
  API_BASE_URL: '/api',
}));

describe('ops service mock data', () => {
  const auditRecords = mockAuditRecords as unknown as AuditRecord[];
  const insertedRecords: AuditRecord[] = [];

  afterEach(() => {
    for (const record of insertedRecords.splice(0)) {
      const index = auditRecords.findIndex((item) => item.id === record.id);
      if (index >= 0) auditRecords.splice(index, 1);
    }
  });

  it('returns copied alert rule rows', async () => {
    const first = await listAlertRules();
    const originalName = first[0].name;
    first[0].name = 'mutated-rule';
    first[0].channels.push('mutated-channel');

    const second = await listAlertRules();
    expect(second[0].name).toBe(originalName);
    expect(second[0].channels).not.toContain('mutated-channel');
    expect(second[0]).not.toBe(first[0]);
  });

  it('copies alert rule channels on create, update, and toggle', async () => {
    const channels = ['email'];
    const created = await createAlertRule({
      name: 'created-copy-test',
      channels,
    });
    channels.push('sms');
    created.channels.push('mutated-return');

    const afterCreate = (await listAlertRules()).find((rule) => rule.id === created.id);
    expect(afterCreate?.channels).toEqual(['email']);

    const updated = await updateAlertRule({
      ...created,
      channels: ['webhook'],
    });
    updated.channels.push('mutated-return');

    const toggled = await toggleAlertRule(created.id, false);
    toggled.channels.push('mutated-toggle');

    const afterUpdate = (await listAlertRules()).find((rule) => rule.id === created.id);
    expect(afterUpdate?.enabled).toBe(false);
    expect(afterUpdate?.channels).toEqual(['webhook']);
  });

  it('returns copied system alert rows', async () => {
    const first = await listSystemAlerts();
    const originalTitle = first[0].title;
    first[0].title = 'mutated-alert';

    const second = await listSystemAlerts();
    expect(second[0].title).toBe(originalTitle);
    expect(second[0]).not.toBe(first[0]);
  });

  it('returns copied audit records', async () => {
    const first = await listAuditRecords({ page: 1, pageSize: 1 });
    const originalOperator = first.items[0].operator;
    first.items[0].operator = 'mutated-operator';

    const second = await listAuditRecords({ page: 1, pageSize: 1 });
    expect(second.items[0].operator).toBe(originalOperator);
    expect(second.items[0]).not.toBe(first.items[0]);
  });

  it('searches records safely when optional text fields are missing', async () => {
    const record = {
      id: 'audit-null-safe',
      timestamp: '2026-07-26 10:00:00',
      operator: null,
      operationType: 'DIAGNOSE',
      target: null,
      detail: 'Describe gRPC client connection',
      ipAddress: '127.0.0.1',
      result: 'success',
    } as unknown as AuditRecord;
    insertedRecords.push(record);
    auditRecords.push(record);

    const result = await listAuditRecords({ search: 'grpc client', pageSize: 100 });

    expect(result.items.map((item) => item.id)).toContain('audit-null-safe');
  });

  it('exports filtered audit records as escaped CSV', async () => {
    const record = {
      id: 'audit-csv-export',
      timestamp: '2026-08-01 10:00:00',
      operator: '=admin',
      operationType: 'DELETE',
      target: 'csv-export-target',
      detail: 'removed "topic", safely',
      ipAddress: '\n=127.0.0.1',
      result: 'SUCCESS',
    } as AuditRecord;
    insertedRecords.push(record);
    auditRecords.push(record);

    const csv = await exportAuditLogs({ search: 'csv-export-target' });

    expect(csv).toContain('timestamp,operator,operationType,target,detail,ipAddress,result');
    expect(csv).toContain(
      '"2026-08-01 10:00:00","\'=admin","DELETE","csv-export-target",' +
        '"removed ""topic"", safely","\'\n=127.0.0.1","SUCCESS"',
    );
  });
});
