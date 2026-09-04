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

import { describe, expect, it } from 'vitest';
import type { Instance } from '../api/instance';
import {
  buildInstanceCapabilityMatrix,
  describeCapabilityGaps,
  filterInstanceCapabilityRows,
  summarizeVisibleCapabilityRows,
} from './instanceCapabilityMatrix';

const instance = (
  name: string,
  vendor: Instance['vendor'] = 'APACHE',
  type: Instance['type'] = 'DIRECT',
): Instance => ({
  id: name.length,
  name,
  vendor,
  type,
  endpoint: `${name}:9876`,
  remark: null,
  topicCount: 0,
  consumerGroupCount: 0,
  gmtCreate: '',
  gmtModified: '',
});

const matrix = buildInstanceCapabilityMatrix([
  {
    instance: instance('apache-prod'),
    value: {
      instanceId: 'apache-prod',
      vendor: 'APACHE',
      accessType: 'DIRECT',
      capabilities: [
        'TOPIC_MANAGEMENT',
        'CONSUMER_GROUP_MANAGEMENT',
        'MESSAGE_QUERY',
        'MESSAGE_TRACE',
        'ACL_MANAGEMENT',
        'DLQ_MANAGEMENT',
      ],
    },
  },
  {
    instance: instance('cloud-prod', 'ALIYUN', 'CLOUD'),
    value: {
      instanceId: 'cloud-prod',
      vendor: 'ALIYUN',
      accessType: 'CLOUD',
      capabilities: ['TOPIC_MANAGEMENT', 'MESSAGE_QUERY', 'MESSAGE_QUERY'],
    },
  },
  { instance: instance('offline'), error: 'HTTP 503' },
]);

describe('instanceCapabilityMatrix', () => {
  it('builds stable rows, removes duplicate capabilities, and preserves failures', () => {
    expect(matrix.rows.map((row) => row.instanceId)).toEqual([
      'cloud-prod',
      'apache-prod',
      'offline',
    ]);
    expect(matrix.rows[0].capabilities).toEqual(['TOPIC_MANAGEMENT', 'MESSAGE_QUERY']);
    expect(matrix.rows[0].supportedCount).toBe(2);
    expect(matrix.rows[2]).toMatchObject({ status: 'FAILED', error: 'HTTP 503' });
  });

  it('calculates coverage from successfully loaded instances only', () => {
    expect(matrix.summary).toMatchObject({
      requested: 3,
      loaded: 2,
      failed: 1,
      fullCoverage: 1,
      limited: 1,
    });
    expect(matrix.summary.coverage.find((item) => item.capability === 'MESSAGE_QUERY')).toEqual({
      capability: 'MESSAGE_QUERY',
      supported: 2,
      loaded: 2,
      percent: 100,
    });
    expect(matrix.summary.coverage.find((item) => item.capability === 'DLQ_MANAGEMENT')).toEqual({
      capability: 'DLQ_MANAGEMENT',
      supported: 1,
      loaded: 2,
      percent: 50,
    });
  });

  it('filters by vendor, access type, status, and text', () => {
    const base = {
      search: '',
      vendor: 'ALL' as const,
      accessType: 'ALL' as const,
      capability: 'ALL' as const,
      support: 'ALL' as const,
      status: 'ALL' as const,
    };
    expect(filterInstanceCapabilityRows(matrix.rows, { ...base, vendor: 'ALIYUN' })).toHaveLength(
      1,
    );
    expect(
      filterInstanceCapabilityRows(matrix.rows, { ...base, accessType: 'DIRECT' }),
    ).toHaveLength(2);
    expect(
      filterInstanceCapabilityRows(matrix.rows, { ...base, status: 'FAILED' })[0].instanceId,
    ).toBe('offline');
    expect(
      filterInstanceCapabilityRows(matrix.rows, { ...base, search: '503' })[0].instanceId,
    ).toBe('offline');
  });

  it('filters supported and missing instances for a selected capability', () => {
    const base = {
      search: '',
      vendor: 'ALL' as const,
      accessType: 'ALL' as const,
      capability: 'DLQ_MANAGEMENT' as const,
      status: 'ALL' as const,
    };
    expect(
      filterInstanceCapabilityRows(matrix.rows, { ...base, support: 'SUPPORTED' }).map(
        (row) => row.instanceId,
      ),
    ).toEqual(['apache-prod']);
    expect(
      filterInstanceCapabilityRows(matrix.rows, { ...base, support: 'MISSING' }).map(
        (row) => row.instanceId,
      ),
    ).toEqual(['cloud-prod']);
  });

  it('does not treat failed discovery as a confirmed capability gap', () => {
    const failed = matrix.rows.find((row) => row.status === 'FAILED')!;
    expect(describeCapabilityGaps(failed)).toBe('HTTP 503');
    expect(
      matrix.rows.find((row) => row.instanceId === 'cloud-prod')?.missingCapabilities,
    ).toContain('DLQ_MANAGEMENT');
  });

  it('summarizes the currently visible subset', () => {
    expect(summarizeVisibleCapabilityRows(matrix.rows)).toEqual({
      instances: 3,
      loaded: 2,
      failed: 1,
      limited: 1,
    });
  });
});
