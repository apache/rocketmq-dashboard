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
  buildProducerConnectionSummary,
  fetchProducerGroups,
  fetchTopicList,
  queryProducerConnection,
} from './producer';

const mock = new MockAdapter(client);

describe('Producer API', () => {
  beforeEach(() => {
    mock.reset();
    vi.stubGlobal('localStorage', { getItem: vi.fn().mockReturnValue(null) });
  });

  afterEach(() => {
    mock.reset();
    vi.unstubAllGlobals();
  });

  it('fetches Studio topic records sorted alphabetically', async () => {
    mock.onGet('/topics').reply((config) => {
      expect(config.params.instanceId).toBe('instance-1');
      return [
        200,
        {
          code: 200,
          data: [{ name: 'order-events' }, { name: 'user-signup' }, { name: 'batch-process' }],
        },
      ];
    });

    const result = await fetchTopicList('instance-1');
    expect(result).toEqual(['batch-process', 'order-events', 'user-signup']);
  });

  it('keeps compatibility with legacy topicList responses', async () => {
    mock.onGet('/topics').reply(200, {
      topicList: ['order-events', 'user-signup', 'batch-process'],
    });

    const result = await fetchTopicList('instance-1');
    expect(result).toEqual(['batch-process', 'order-events', 'user-signup']);
  });

  it('handles empty topic list', async () => {
    mock.onGet('/topics').reply(200, { topicList: [] });

    const result = await fetchTopicList('instance-1');
    expect(result).toEqual([]);
  });

  it('normalizes duplicate and blank topic suggestions', async () => {
    mock.onGet('/topics').reply(200, {
      topicList: [' orders ', '', 'payments', 'orders'],
    });

    await expect(fetchTopicList('instance-1')).resolves.toEqual(['orders', 'payments']);
  });

  it('fetches active producer group suggestions', async () => {
    mock.onGet('/producer/groups').reply((config) => {
      expect(config.params.instanceId).toBe('instance-1');
      expect(config.params.topic).toBe('order-events');
      expect(config.params.query).toBe('pg');
      expect(config.params.limit).toBe(20);
      return [
        200,
        {
          code: 200,
          data: ['pg-order', 'pg-payment'],
        },
      ];
    });

    await expect(
      fetchProducerGroups('instance-1', { topic: 'order-events', query: 'pg', limit: 20 }),
    ).resolves.toEqual(['pg-order', 'pg-payment']);
  });

  it('normalizes producer group suggestions while preserving backend order', async () => {
    mock.onGet('/producer/groups').reply(200, {
      code: 200,
      data: [' pg-orders ', '', 'pg-payments', 'pg-orders'],
    });

    await expect(fetchProducerGroups('instance-1')).resolves.toEqual(['pg-orders', 'pg-payments']);
  });

  it('queries producer connections by topic and group', async () => {
    const connections = [
      {
        clientId: 'producer-1',
        clientAddr: '192.168.1.10',
        topic: 'order-events',
        producerGroup: 'order-producer',
        language: 'JAVA',
        versionDesc: '5.1.0',
      },
      {
        clientId: 'producer-2',
        clientAddr: '192.168.1.11',
        language: 'JAVA',
        versionDesc: '5.1.0',
      },
    ];
    mock.onGet('/producer/connection').reply((config) => {
      expect(config.params.topic).toBe('order-events');
      expect(config.params.producerGroup).toBe('order-producer');
      expect(config.params.instanceId).toBe('instance-1');
      return [200, { connectionSet: connections }];
    });

    const result = await queryProducerConnection('instance-1', 'order-events', 'order-producer');
    expect(result.connectionSet).toHaveLength(2);
    expect(result.connectionSet[0].clientId).toBe('producer-1');
    expect(result.connectionSet[0].producerGroup).toBe('order-producer');
    expect(result.summary.totalConnections).toBe(2);
    expect(result.summary.readiness).toBe('READY');
  });

  it('queries producer connections without a producer group for all-group scans', async () => {
    mock.onGet('/producer/connection').reply((config) => {
      expect(config.params.topic).toBe('order-events');
      expect(config.params.instanceId).toBe('instance-1');
      expect(config.params.producerGroup).toBeUndefined();
      return [
        200,
        {
          connectionSet: [
            {
              clientId: 'producer-1',
              clientAddr: '192.168.1.10',
              topic: 'order-events',
              producerGroup: 'pg-order',
              language: 'JAVA',
              versionDesc: '5.1.0',
            },
          ],
        },
      ];
    });

    const result = await queryProducerConnection('instance-1', 'order-events');
    expect(result.connectionSet[0].producerGroup).toBe('pg-order');
    expect(result.summary.totalConnections).toBe(1);
  });

  it('handles empty producer connections', async () => {
    mock.onGet('/producer/connection').reply(200, { connectionSet: [] });

    const result = await queryProducerConnection('instance-1', 'topic', 'group');
    expect(result.connectionSet).toEqual([]);
    expect(result.summary.readiness).toBe('UNAVAILABLE');
    expect(result.summary.warnings).toEqual(['NO_CONNECTIONS']);
  });

  it('uses backend producer connection summaries when provided', async () => {
    mock.onGet('/producer/connection').reply(200, {
      connectionSet: [],
      summary: {
        totalConnections: 0,
        uniqueClientCount: 0,
        uniqueAddressCount: 0,
        uniqueLanguageCount: 0,
        uniqueVersionCount: 0,
        languages: [],
        versions: [],
        duplicateClientIds: [],
        warnings: ['NO_CONNECTIONS'],
        readiness: 'UNAVAILABLE',
      },
    });

    const result = await queryProducerConnection('instance-1', 'topic', 'group');
    expect(result.summary).toEqual({
      totalConnections: 0,
      uniqueClientCount: 0,
      uniqueAddressCount: 0,
      uniqueLanguageCount: 0,
      uniqueVersionCount: 0,
      languages: [],
      versions: [],
      duplicateClientIds: [],
      warnings: ['NO_CONNECTIONS'],
      readiness: 'UNAVAILABLE',
    });
  });

  it('builds producer connection warning summaries for legacy responses', () => {
    const result = buildProducerConnectionSummary([
      {
        clientId: 'producer-a',
        clientAddr: '10.0.0.1',
        language: 'Java',
        versionDesc: '5.1.0',
      },
      {
        clientId: 'producer-a',
        clientAddr: '10.0.0.2',
        language: 'Go',
        versionDesc: '5.2.0',
      },
    ]);

    expect(result.readiness).toBe('WARNING');
    expect(result.duplicateClientIds).toEqual(['producer-a']);
    expect(result.warnings).toEqual(['DUPLICATE_CLIENT_ID', 'MIXED_CLIENT_VERSION']);
  });

  it('normalizes client identifiers and addresses consistently in summary counts', () => {
    const result = buildProducerConnectionSummary([
      {
        clientId: 'producer-a',
        clientAddr: '10.0.0.1',
        language: 'Java',
        versionDesc: '5.1.0',
      },
      {
        clientId: ' producer-a ',
        clientAddr: ' 10.0.0.1 ',
        language: 'Java',
        versionDesc: '5.1.0',
      },
    ]);

    expect(result.uniqueClientCount).toBe(1);
    expect(result.uniqueAddressCount).toBe(1);
    expect(result.duplicateClientIds).toEqual(['producer-a']);
    expect(result.warnings).toContain('DUPLICATE_CLIENT_ID');
  });
});
