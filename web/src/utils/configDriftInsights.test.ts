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
import type { BrokerConfigDiffResult, NameServerConfigDiffResult } from '../api/cluster';
import {
  buildBrokerConfigDriftInsights,
  buildNameServerConfigDriftInsights,
} from './configDriftInsights';

const cleanNameServerDiff = (): NameServerConfigDiffResult => ({
  cluster: 'rocketmq1',
  complete: true,
  driftDetected: false,
  nodeCount: 2,
  reachableNodeCount: 2,
  comparedKeys: ['serverWorkerThreads', 'listenPort'],
  nodes: [
    { address: 'namesrv-a:9876', reachable: true },
    { address: 'namesrv-b:9876', reachable: true },
  ],
  differences: [],
});

const brokerDiff = (overrides: Partial<BrokerConfigDiffResult>): BrokerConfigDiffResult => ({
  cluster: 'cluster-prod',
  complete: true,
  driftDetected: true,
  brokerCount: 3,
  reachableBrokerCount: 3,
  comparedFields: ['flushDiskType', 'writeQueueNums'],
  brokers: [
    { name: 'broker-a', address: '10.0.0.1:10911', reachable: true },
    { name: 'broker-b', address: '10.0.0.2:10911', reachable: true },
    { name: 'broker-c', address: '10.0.0.3:10911', reachable: true },
  ],
  differences: [],
  ...overrides,
});

describe('config drift insights', () => {
  it('marks an aligned NameServer config diff as clean', () => {
    const insights = buildNameServerConfigDriftInsights(cleanNameServerDiff());

    expect(insights.status).toBe('clean');
    expect(insights.summary).toMatchObject({
      targetCount: 2,
      reachableCount: 2,
      unreachableCount: 0,
      comparedFieldCount: 2,
      differenceCount: 0,
    });
    expect(insights.recommendations).toEqual([
      '当前配置差异结果未发现需要处理的漂移项，保留现有配置并按计划巡检即可。',
    ]);
    expect(insights.remediationPlan).toContain('No drift fields');
  });

  it('blocks remediation guidance when a NameServer target is unreachable', () => {
    const insights = buildNameServerConfigDriftInsights({
      ...cleanNameServerDiff(),
      complete: false,
      driftDetected: true,
      reachableNodeCount: 1,
      nodes: [
        { address: 'namesrv-a:9876', reachable: true },
        { address: 'namesrv-b:9876', reachable: false },
      ],
      differences: [
        {
          key: 'serverWorkerThreads',
          values: [
            { address: 'namesrv-a:9876', configured: true, value: '8' },
            { address: 'namesrv-b:9876', configured: false, value: null },
          ],
        },
      ],
    });

    expect(insights.status).toBe('blocked');
    expect(insights.unreachableTargets.map((target) => target.label)).toEqual(['namesrv-b:9876']);
    expect(insights.recommendations[0]).toContain('先恢复 1 个不可达 NameServer');
    expect(insights.fields[0]).toMatchObject({
      key: 'serverWorkerThreads',
      status: 'unconfigured',
      majorityValue: '8',
      unconfiguredTargets: ['namesrv-b:9876'],
    });
  });

  it('finds majority Broker values and identifies outlier targets', () => {
    const insights = buildBrokerConfigDriftInsights(
      brokerDiff({
        differences: [
          {
            field: 'writeQueueNums',
            brokerProperty: 'defaultTopicQueueNums',
            values: [
              {
                brokerName: 'broker-a',
                address: '10.0.0.1:10911',
                configured: true,
                value: '8',
              },
              {
                brokerName: 'broker-b',
                address: '10.0.0.2:10911',
                configured: true,
                value: '8',
              },
              {
                brokerName: 'broker-c',
                address: '10.0.0.3:10911',
                configured: true,
                value: '16',
              },
            ],
          },
        ],
      }),
      (field) => (field === 'writeQueueNums' ? '写队列数' : field),
    );

    expect(insights.status).toBe('review');
    expect(insights.summary.alignableCount).toBe(1);
    expect(insights.fields[0]).toMatchObject({
      key: 'writeQueueNums',
      label: '写队列数',
      property: 'defaultTopicQueueNums',
      status: 'alignable',
      majorityValue: '8',
      majorityCount: 2,
      minorityTargets: ['broker-c'],
    });
    expect(insights.remediationPlan).toContain('Review reference value: 8');
    expect(insights.remediationPlan).toContain('Outlier targets: broker-c');
  });

  it('treats Broker fields with only unconfigured targets as ambiguous', () => {
    const insights = buildBrokerConfigDriftInsights(
      brokerDiff({
        differences: [
          {
            field: 'deleteWhen',
            brokerProperty: 'deleteWhen',
            values: [
              {
                brokerName: 'broker-a',
                address: '10.0.0.1:10911',
                configured: false,
                value: null,
              },
              {
                brokerName: 'broker-b',
                address: '10.0.0.2:10911',
                configured: false,
                value: null,
              },
            ],
          },
        ],
      }),
    );

    expect(insights.fields[0]).toMatchObject({
      status: 'ambiguous',
      configuredCount: 0,
      unconfiguredCount: 2,
      majorityValue: null,
    });
    expect(insights.recommendations).toEqual(
      expect.arrayContaining([expect.stringContaining('没有明确多数值')]),
    );
  });

  it('does not recommend a source value when Broker values are tied', () => {
    const insights = buildBrokerConfigDriftInsights(
      brokerDiff({
        differences: [
          {
            field: 'flushDiskType',
            brokerProperty: 'flushDiskType',
            values: [
              {
                brokerName: 'broker-a',
                address: '10.0.0.1:10911',
                configured: true,
                value: 'ASYNC_FLUSH',
              },
              {
                brokerName: 'broker-b',
                address: '10.0.0.2:10911',
                configured: true,
                value: 'SYNC_FLUSH',
              },
            ],
          },
        ],
      }),
    );

    expect(insights.fields[0]).toMatchObject({
      status: 'ambiguous',
      majorityValue: null,
      majorityCount: 0,
      minorityTargets: [],
    });
    expect(insights.remediationPlan).toContain(
      'Pick the intended source-of-truth value before applying any change.',
    );
  });
});
