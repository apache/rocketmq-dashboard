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
  deleteAlertRule,
  exportAuditLogs,
  getAuditFilterOptions,
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

  it('rejects updates for unknown alert rule IDs', async () => {
    const before = await listAlertRules();
    const missingRule = {
      ...before[0],
      id: 'missing-alert-rule',
      name: 'missing rule',
    };

    await expect(updateAlertRule(missingRule)).rejects.toThrow(
      'Alert rule not found: missing-alert-rule',
    );

    const after = await listAlertRules();
    expect(after.map((rule) => rule.id)).toEqual(before.map((rule) => rule.id));
    expect(after.find((rule) => rule.id === 'missing-alert-rule')).toBeUndefined();
  });

  it('rejects deletion of an unknown alert rule without changing mock state', async () => {
    const before = await listAlertRules();

    await expect(deleteAlertRule('missing-alert-rule')).rejects.toThrow(
      'Alert rule not found: missing-alert-rule',
    );
    await expect(listAlertRules()).resolves.toEqual(before);
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

  it('derives complete filter options from the default audit records', async () => {
    const options = await getAuditFilterOptions();

    expect(options.operationTypes).toEqual(
      expect.arrayContaining(['CREATE_TOPIC', 'DELETE_GROUP', 'RESET_OFFSET']),
    );
    expect(options.resourceTypes).toEqual(expect.arrayContaining(['CLUSTER', 'GROUP', 'TOPIC']));
    expect(options.clusterIds).toEqual(expect.arrayContaining(['prod-cn', 'prod-sh']));
    expect(options.results).toEqual(expect.arrayContaining(['FAILED', 'SUCCESS']));
  });

  it('searches records safely when optional text fields are missing', async () => {
    const record = {
      id: 'audit-null-safe',
      timestamp: '2026-07-26 10:00:00',
      operator: null,
      operationType: 'DIAGNOSE',
      resourceType: 'CLIENT',
      target: null,
      clusterId: null,
      detail: 'Describe gRPC client connection',
      result: 'success',
      errorMessage: null,
    } as unknown as AuditRecord;
    insertedRecords.push(record);
    auditRecords.push(record);

    const result = await listAuditRecords({ search: 'grpc client', pageSize: 100 });

    expect(result.items.map((item) => item.id)).toContain('audit-null-safe');
  });

  it('derives filter options and applies resource and cluster filters', async () => {
    const matching = {
      id: 'audit-filter-match',
      timestamp: '2026-08-01 10:00:00',
      operator: 'admin',
      operationType: 'RESET_OFFSET',
      resourceType: 'CONSUMER_GROUP',
      target: 'consumer-a',
      clusterId: 'prod-filter',
      detail: 'reset offset',
      result: 'PARTIAL',
      errorMessage: '',
    } as AuditRecord;
    const otherCluster = {
      ...matching,
      id: 'audit-filter-other-cluster',
      clusterId: 'prod-other',
    };
    insertedRecords.push(matching, otherCluster);
    auditRecords.push(matching, otherCluster);

    const options = await getAuditFilterOptions();
    const result = await listAuditRecords({
      resourceType: 'CONSUMER_GROUP',
      clusterId: 'prod-filter',
      pageSize: 100,
    });

    expect(options.operationTypes).toContain('RESET_OFFSET');
    expect(options.resourceTypes).toContain('CONSUMER_GROUP');
    expect(options.clusterIds).toEqual(expect.arrayContaining(['prod-filter', 'prod-other']));
    expect(options.results).toContain('PARTIAL');
    expect(result.items.map((record) => record.id)).toEqual(['audit-filter-match']);
  });

  it('exports filtered audit records as escaped CSV', async () => {
    const record = {
      id: 'audit-csv-export',
      timestamp: '2026-08-01 10:00:00',
      operator: '=admin',
      operationType: 'DELETE',
      resourceType: 'TOPIC',
      target: 'csv-export-target',
      clusterId: 'prod-cn',
      detail: 'removed "topic", safely',
      result: 'SUCCESS',
      errorMessage: '=denied',
    } as AuditRecord;
    insertedRecords.push(record);
    auditRecords.push(record);

    const csv = await exportAuditLogs({ search: 'csv-export-target' });

    expect(csv).toContain(
      'timestamp,operator,operationType,resourceType,target,clusterId,detail,result,errorMessage',
    );
    expect(csv).toContain(
      '"2026-08-01 10:00:00","\'=admin","DELETE","TOPIC","csv-export-target",' +
        '"prod-cn","removed ""topic"", safely","SUCCESS","\'=denied"',
    );
  });
});
