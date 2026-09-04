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
import type { DataSource } from '../api/settings';
import { analyzeDataSourceCoverage } from './dataSourceCoverage';

const instances: Instance[] = [
  {
    id: 1,
    name: 'rmq-prod-a',
    remark: '',
    type: 'DIRECT',
    endpoint: '10.0.0.1:9876',
    vendor: 'APACHE',
    topicCount: 12,
    consumerGroupCount: 8,
    gmtCreate: '2026-09-01T00:00:00',
    gmtModified: '2026-09-01T00:00:00',
  },
  {
    id: 2,
    name: 'rmq-prod-b',
    remark: '',
    type: 'PROXY_CLUSTER',
    endpoint: '10.0.0.2:8081',
    vendor: 'APACHE',
    topicCount: 20,
    consumerGroupCount: 10,
    gmtCreate: '2026-09-01T00:00:00',
    gmtModified: '2026-09-01T00:00:00',
  },
];

const dataSource = (overrides: Partial<DataSource>): DataSource => ({
  key: 'prom-prod',
  name: 'Prometheus prod',
  type: 'Prometheus',
  url: 'http://prometheus:9090',
  auth: 'None',
  status: 'healthy',
  ...overrides,
});

describe('analyzeDataSourceCoverage', () => {
  it('marks every instance covered by a global healthy source', () => {
    const summary = analyzeDataSourceCoverage([dataSource({})], instances);

    expect(summary.status).toBe('healthy');
    expect(summary.coveredInstanceCount).toBe(2);
    expect(summary.verifiedInstanceCount).toBe(2);
    expect(summary.uncoveredInstanceCount).toBe(0);
    expect(summary.globalDataSourceCount).toBe(1);
    expect(summary.instanceCoverage.map((coverage) => coverage.status)).toEqual([
      'healthy',
      'healthy',
    ]);
  });

  it('reports scoped gaps when an instance has no matching data source', () => {
    const summary = analyzeDataSourceCoverage(
      [dataSource({ instanceIds: ['rmq-prod-a'] })],
      instances,
    );

    expect(summary.status).toBe('critical');
    expect(summary.coveredInstanceCount).toBe(1);
    expect(summary.uncoveredInstanceCount).toBe(1);
    expect(summary.issues).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          code: 'INSTANCE_UNCOVERED',
          severity: 'critical',
          instanceId: 'rmq-prod-b',
        }),
      ]),
    );
  });

  it('reports stale instance bindings on data sources', () => {
    const summary = analyzeDataSourceCoverage(
      [dataSource({ instanceIds: ['rmq-prod-a', 'deleted-instance'] })],
      instances,
    );

    expect(summary.status).toBe('critical');
    expect(summary.issues).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          code: 'SOURCE_STALE_INSTANCE',
          severity: 'warning',
          dataSourceKey: 'prom-prod',
        }),
      ]),
    );
    expect(summary.sourceRefs[0].staleInstanceIds).toEqual(['deleted-instance']);
  });

  it('warns when multiple same-type sources cover the same instance', () => {
    const summary = analyzeDataSourceCoverage(
      [
        dataSource({ key: 'prom-a', name: 'Prometheus A', instanceIds: ['rmq-prod-a'] }),
        dataSource({
          key: 'prom-b',
          name: 'Prometheus B',
          url: 'http://prometheus-b:9090',
          instanceIds: ['rmq-prod-a'],
        }),
        dataSource({ key: 'thanos-global', name: 'Thanos', type: 'Thanos' }),
      ],
      instances,
    );

    expect(summary.status).toBe('warning');
    expect(summary.conflictedInstanceCount).toBe(1);
    expect(summary.issues).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          code: 'INSTANCE_MULTIPLE_SAME_TYPE',
          instanceId: 'rmq-prod-a',
          type: 'Prometheus',
        }),
      ]),
    );
  });

  it('separates untested coverage from unavailable coverage', () => {
    const untested = analyzeDataSourceCoverage(
      [dataSource({ status: undefined, instanceIds: ['rmq-prod-a'] })],
      [instances[0]],
    );
    const offline = analyzeDataSourceCoverage(
      [dataSource({ status: 'offline', instanceIds: ['rmq-prod-a'] })],
      [instances[0]],
    );

    expect(untested.status).toBe('warning');
    expect(untested.instanceCoverage[0].usableSourceCount).toBe(1);
    expect(untested.issues).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          code: 'INSTANCE_ONLY_UNTESTED',
          severity: 'warning',
        }),
      ]),
    );
    expect(offline.status).toBe('critical');
    expect(offline.instanceCoverage[0].usableSourceCount).toBe(0);
    expect(offline.issues).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          code: 'INSTANCE_NO_USABLE_SOURCE',
          severity: 'critical',
        }),
      ]),
    );
  });
});
