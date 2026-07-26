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

vi.mock('../config', () => ({ API_BASE_URL: '/api', USE_MOCK: true }));

describe('ops service mock audit records', () => {
  const auditRecords = mockAuditRecords as unknown as AuditRecord[];
  const insertedRecords: AuditRecord[] = [];

  afterEach(() => {
    for (const record of insertedRecords.splice(0)) {
      const index = auditRecords.findIndex((item) => item.id === record.id);
      if (index >= 0) auditRecords.splice(index, 1);
    }
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

    const { listAuditRecords } = await import('./opsService');
    const result = await listAuditRecords({ search: 'grpc client', pageSize: 100 });

    expect(result.items.map((item) => item.id)).toContain('audit-null-safe');
  });
});
