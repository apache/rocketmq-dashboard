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

import { analyzeConsumerGroupHealth } from '../consumerGroupDiagnostics';
import type { ConsumerGroup, SubscriptionEntry } from '../../api/metadata';

function group(): ConsumerGroup {
  return {
    name: 'g',
    namespace: 'ns',
    clusterId: 'c',
    subscriptionMode: 'CLUSTERING',
    consumeType: 'PUSH',
    onlineInstances: 1,
    totalLag: 0,
    subscribedTopics: ['T'],
    subscriptionDataType: 'STATIC',
  } as unknown as ConsumerGroup;
}

function subscription(consistency: string): SubscriptionEntry {
  return {
    topic: 'T',
    expression: '*',
    type: 'TAG',
    filterMode: 'TAG',
    consistency,
  };
}

function subscriptionIssues(consistency: string) {
  const result = analyzeConsumerGroupHealth(group(), [subscription(consistency)], []);
  return result.issues.filter(
    (i) => i.code === 'SUBSCRIPTION_INCONSISTENT' || i.code === 'SUBSCRIPTION_UNKNOWN',
  );
}

describe('subscription consistency value domains', () => {
  it('treats the Aliyun boolean form true as consistent', () => {
    expect(subscriptionIssues('true')).toEqual([]);
  });

  it('escalates the Aliyun boolean form false to critical inconsistent', () => {
    const issues = subscriptionIssues('false');
    expect(issues).toHaveLength(1);
    expect(issues[0].code).toBe('SUBSCRIPTION_INCONSISTENT');
    expect(issues[0].severity).toBe('critical');
  });

  it('treats the Tencent numeric form 1 as consistent', () => {
    expect(subscriptionIssues('1')).toEqual([]);
  });

  it('escalates the Tencent numeric form 0 to critical inconsistent', () => {
    const issues = subscriptionIssues('0');
    expect(issues).toHaveLength(1);
    expect(issues[0].code).toBe('SUBSCRIPTION_INCONSISTENT');
    expect(issues[0].severity).toBe('critical');
  });

  it('keeps the Apache string form working (control)', () => {
    expect(subscriptionIssues('consistent')).toEqual([]);
    expect(subscriptionIssues('inconsistent')[0]?.code).toBe('SUBSCRIPTION_INCONSISTENT');
  });

  it('still reports unknown values as unknown (control)', () => {
    expect(subscriptionIssues('later')[0]?.code).toBe('SUBSCRIPTION_UNKNOWN');
  });
});
