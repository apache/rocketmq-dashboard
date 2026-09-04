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
import type { ProducerConnection } from '../api/producer';
import {
  analyzeProducerGroupComposition,
  filterProducerGroupComposition,
} from './producerGroupComposition';

const connection = (overrides: Partial<ProducerConnection>): ProducerConnection => ({
  clientId: 'client-1',
  clientAddr: '10.0.0.1',
  language: 'JAVA',
  versionDesc: '5.3.0',
  producerGroup: 'orders-producer',
  ...overrides,
});

describe('producerGroupComposition', () => {
  it('summarizes connections by producer group', () => {
    const result = analyzeProducerGroupComposition([
      connection({ clientId: 'client-1' }),
      connection({ clientId: 'client-2', clientAddr: '10.0.0.2' }),
      connection({ clientId: 'client-3', producerGroup: 'billing-producer' }),
    ]);
    expect(result.summary).toMatchObject({ groups: 2, connections: 3, healthy: 2 });
    expect(result.rows.find((row) => row.producerGroup === 'orders-producer')).toMatchObject({
      connections: 2,
      uniqueClients: 2,
      uniqueAddresses: 2,
    });
  });

  it('marks duplicate client identifiers critical within their group', () => {
    const result = analyzeProducerGroupComposition([
      connection({ clientAddr: '10.0.0.1' }),
      connection({ clientAddr: '10.0.0.2' }),
    ]);
    expect(result.rows[0]).toMatchObject({
      health: 'CRITICAL',
      findings: ['DUPLICATE_CLIENT_ID'],
    });
    expect(result.summary.duplicateClientGroups).toBe(1);
  });

  it('reports version and language diversity without claiming incompatibility', () => {
    const result = analyzeProducerGroupComposition([
      connection({ clientId: 'one', language: 'JAVA', versionDesc: '5.3.0' }),
      connection({ clientId: 'two', language: 'CPP', versionDesc: '2.1.0' }),
    ]);
    expect(result.rows[0]).toMatchObject({
      health: 'WARNING',
      findings: ['MIXED_VERSION', 'MIXED_LANGUAGE'],
    });
    expect(result.rows[0].versions).toEqual([
      { value: '2.1.0', count: 1 },
      { value: '5.3.0', count: 1 },
    ]);
  });

  it('keeps missing metadata and missing group identity explicit', () => {
    const result = analyzeProducerGroupComposition([
      connection({ producerGroup: ' ', language: '', versionDesc: '', clientAddr: '' }),
    ]);
    expect(result.rows[0]).toMatchObject({
      producerGroup: '',
      health: 'WARNING',
      findings: ['INCOMPLETE_METADATA', 'UNREPORTED_GROUP'],
    });
  });

  it('does not consider duplicate client ids across different groups a collision', () => {
    const result = analyzeProducerGroupComposition([
      connection({ producerGroup: 'orders' }),
      connection({ producerGroup: 'billing' }),
    ]);
    expect(result.summary.critical).toBe(0);
  });

  it('returns an empty, stable report for no connections', () => {
    expect(analyzeProducerGroupComposition([])).toEqual({
      rows: [],
      summary: {
        groups: 0,
        connections: 0,
        healthy: 0,
        warning: 0,
        critical: 0,
        duplicateClientGroups: 0,
        incompleteMetadataGroups: 0,
      },
    });
  });

  it('filters by health, finding, group, address, language, and version', () => {
    const rows = analyzeProducerGroupComposition([
      connection({ clientId: 'dup', clientAddr: '10.0.0.1' }),
      connection({ clientId: 'dup', clientAddr: '10.0.0.2' }),
      connection({
        clientId: 'healthy',
        producerGroup: 'billing',
        language: 'CPP',
        versionDesc: '2.1.0',
      }),
    ]).rows;
    const base = { search: '', health: 'ALL' as const, finding: 'ALL' as const };
    expect(filterProducerGroupComposition(rows, { ...base, health: 'CRITICAL' })).toHaveLength(1);
    expect(
      filterProducerGroupComposition(rows, { ...base, finding: 'DUPLICATE_CLIENT_ID' }),
    ).toHaveLength(1);
    expect(filterProducerGroupComposition(rows, { ...base, search: 'billing' })).toHaveLength(1);
    expect(filterProducerGroupComposition(rows, { ...base, search: '10.0.0.2' })[0].health).toBe(
      'CRITICAL',
    );
    expect(filterProducerGroupComposition(rows, { ...base, search: 'cpp' })[0].producerGroup).toBe(
      'billing',
    );
  });
});
