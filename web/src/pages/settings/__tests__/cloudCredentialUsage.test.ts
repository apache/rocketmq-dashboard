/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */

import { describe, expect, it } from 'vitest';
import type { CloudCredential } from '../../../api/cloudCredential';
import type { Instance } from '../../../api/instance';
import {
  buildCredentialUsageReport,
  credentialUsageCsvRows,
  filterCredentialUsageRows,
} from '../cloudCredentialUsage';

const credentials: CloudCredential[] = [
  {
    id: 1,
    name: 'aliyun-production',
    vendor: 'ALIYUN',
    accessKey: 'LTAI****0001',
    secretKey: 'must-not-leak',
    gmtCreate: '2026-09-01T00:00:00Z',
  },
  {
    id: 2,
    name: 'tencent-unused',
    vendor: 'TENCENT',
    accessKey: 'AKID****0002',
    gmtCreate: '2026-09-02T00:00:00Z',
  },
  {
    id: 3,
    name: 'aliyun-mismatch',
    vendor: 'ALIYUN',
    accessKey: 'LTAI****0003',
    gmtCreate: '2026-09-03T00:00:00Z',
  },
];

const instance = (overrides: Partial<Instance>): Instance => ({
  id: 10,
  name: 'production-a',
  remark: null,
  type: 'CLOUD',
  endpoint: 'endpoint',
  vendor: 'ALIYUN',
  credentialId: 1,
  regionId: 'cn-hangzhou',
  topicCount: 1,
  consumerGroupCount: 1,
  gmtCreate: '2026-09-01T00:00:00Z',
  gmtModified: '2026-09-01T00:00:00Z',
  ...overrides,
});

describe('cloud credential usage report', () => {
  it('classifies used and unused credentials', () => {
    const report = buildCredentialUsageReport(credentials.slice(0, 2), [instance({})]);

    expect(report.rows.find((row) => row.credentialId === 1)?.status).toBe('USED');
    expect(report.rows.find((row) => row.credentialId === 2)?.status).toBe('UNUSED');
    expect(report.summary).toMatchObject({ credentials: 2, used: 1, unused: 1 });
  });

  it('reports a vendor mismatch ahead of lower priority rows', () => {
    const report = buildCredentialUsageReport(credentials, [
      instance({ id: 30, name: 'wrong-cloud', vendor: 'TENCENT', credentialId: 3 }),
    ]);

    expect(report.rows[0]).toMatchObject({ credentialId: 3, status: 'VENDOR_MISMATCH' });
    expect(report.rows[0].mismatchedInstances).toEqual(['wrong-cloud']);
    expect(report.summary.mismatched).toBe(1);
  });

  it('treats an Apache instance using a cloud credential as a mismatch', () => {
    const report = buildCredentialUsageReport(credentials, [
      instance({ vendor: undefined, credentialId: 1 }),
    ]);

    expect(report.rows.find((row) => row.credentialId === 1)?.status).toBe('VENDOR_MISMATCH');
  });

  it('records orphan references without inflating linked instance totals', () => {
    const report = buildCredentialUsageReport(credentials, [
      instance({ id: 91, name: 'orphan', credentialId: 999, regionName: 'Shanghai' }),
    ]);

    expect(report.orphanReferences).toEqual([
      expect.objectContaining({ instanceId: 91, credentialId: 999, region: 'Shanghai' }),
    ]);
    expect(report.summary.orphanReferences).toBe(1);
    expect(report.summary.coveredInstances).toBe(0);
  });

  it('deduplicates and sorts regions and instance names', () => {
    const report = buildCredentialUsageReport(credentials.slice(0, 1), [
      instance({ id: 11, name: 'z-instance', regionName: 'Zhejiang' }),
      instance({ id: 12, name: 'a-instance', regionName: 'Zhejiang' }),
    ]);

    expect(report.rows[0].instanceNames).toEqual(['a-instance', 'z-instance']);
    expect(report.rows[0].regions).toEqual(['Zhejiang']);
  });

  it('filters by vendor, status, and related metadata search', () => {
    const report = buildCredentialUsageReport(credentials, [
      instance({ name: 'payment-cluster', credentialId: 1, regionName: 'Hangzhou' }),
    ]);

    expect(filterCredentialUsageRows(report.rows, { vendor: 'ALIYUN' })).toHaveLength(2);
    expect(filterCredentialUsageRows(report.rows, { status: 'USED' })).toHaveLength(1);
    expect(filterCredentialUsageRows(report.rows, { search: 'PAYMENT' })).toHaveLength(1);
    expect(filterCredentialUsageRows(report.rows, { search: 'hangzhou' })).toHaveLength(1);
  });

  it('never places access keys or secret keys in export rows', () => {
    const report = buildCredentialUsageReport(credentials, [instance({})]);
    const csvRows = credentialUsageCsvRows(report.rows);
    const serialized = JSON.stringify(csvRows);

    expect(serialized).not.toContain('LTAI****0001');
    expect(serialized).not.toContain('must-not-leak');
    expect(Object.keys(csvRows[0])).not.toContain('accessKey');
    expect(Object.keys(csvRows[0])).not.toContain('secretKey');
  });

  it('returns a stable empty report', () => {
    expect(buildCredentialUsageReport([], [])).toEqual({
      rows: [],
      orphanReferences: [],
      summary: {
        credentials: 0,
        used: 0,
        unused: 0,
        mismatched: 0,
        orphanReferences: 0,
        coveredInstances: 0,
      },
    });
  });
});
