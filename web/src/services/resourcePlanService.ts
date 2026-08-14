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

import { listConsumerGroups } from './consumerService';
import { listTopics } from './topicService';
import type { ConsumerGroup, Topic } from '../api/metadata';

export type ResourcePlanResourceType = 'TOPIC' | 'CONSUMER_GROUP';
export type ResourcePlanAction = 'CREATE' | 'UPDATE' | 'SKIP' | 'CONFLICT' | 'INVALID';

export interface ResourcePlanTopicSpec {
  name: string;
  namespace?: string;
  clusterId?: string;
  type?: string;
  writeQueues?: number;
  readQueues?: number;
  perm?: string;
  remark?: string;
}

export interface ResourcePlanConsumerGroupSpec {
  name: string;
  namespace?: string;
  clusterId?: string;
  subscriptionMode?: string;
  consumeType?: string;
  subscribedTopics?: string[];
  subscriptionDataType?: string;
  deliveryOrderType?: string;
  retryMaxTimes?: number;
  delaySeconds?: number;
}

export interface ResourcePlanRequest {
  instanceId: string;
  topics?: ResourcePlanTopicSpec[];
  consumerGroups?: ResourcePlanConsumerGroupSpec[];
}

export interface ResourcePlanChange {
  field: string;
  currentValue?: string | null;
  desiredValue?: string | null;
}

export interface ResourcePlanEntry {
  resourceType: ResourcePlanResourceType;
  name: string;
  rowIndex: number;
  action: ResourcePlanAction;
  applicable: boolean;
  reason: string;
  changes: ResourcePlanChange[];
}

export interface ResourcePlanSummary {
  total: number;
  creates: number;
  updates: number;
  skips: number;
  conflicts: number;
  invalids: number;
  applicable: number;
}

export interface ResourcePlan {
  instanceId: string;
  summary: ResourcePlanSummary;
  entries: ResourcePlanEntry[];
}

export interface ResourceBundle {
  topics?: ResourcePlanTopicSpec[];
  consumerGroups?: ResourcePlanConsumerGroupSpec[];
}

export const RESOURCE_PLAN_SAMPLE = JSON.stringify(
  {
    topics: [
      {
        name: 'order-status-change',
        namespace: 'trade',
        type: 'NORMAL',
        writeQueues: 8,
        readQueues: 8,
        perm: 'RW',
        remark: 'Order status events',
      },
      {
        name: 'payment-callback',
        namespace: 'trade',
        type: 'FIFO',
        writeQueues: 4,
        readQueues: 4,
        perm: 'RW',
        remark: 'Payment callbacks with FIFO order',
      },
    ],
    consumerGroups: [
      {
        name: 'cg-order-status-sync',
        namespace: 'trade',
        subscriptionMode: 'Push',
        consumeType: 'CLUSTERING',
        subscribedTopics: ['order-status-change'],
        subscriptionDataType: 'NORMAL',
        retryMaxTimes: 16,
        delaySeconds: 0,
      },
    ],
  },
  null,
  2,
);

export function parseResourceBundle(text: string): ResourceBundle {
  let parsed: unknown;
  try {
    parsed = JSON.parse(text);
  } catch {
    throw new Error('Resource bundle must be valid JSON');
  }
  if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
    throw new Error('Resource bundle must be a JSON object');
  }
  const bundle = parsed as ResourceBundle;
  if (bundle.topics !== undefined && !Array.isArray(bundle.topics)) {
    throw new Error('topics must be an array');
  }
  if (bundle.consumerGroups !== undefined && !Array.isArray(bundle.consumerGroups)) {
    throw new Error('consumerGroups must be an array');
  }
  return {
    topics: bundle.topics ?? [],
    consumerGroups: bundle.consumerGroups ?? [],
  };
}

export async function previewResourcePlan(request: ResourcePlanRequest): Promise<ResourcePlan> {
  if (!request.instanceId?.trim()) throw new Error('instanceId is required');
  const desiredTopics = request.topics ?? [];
  const desiredGroups = request.consumerGroups ?? [];
  const total = desiredTopics.length + desiredGroups.length;
  if (total === 0) throw new Error('At least one topic or consumer group is required');
  if (total > 200) throw new Error('Resource plan supports at most 200 resources');

  const [topics, groups] = await Promise.all([
    listTopics({ instanceId: request.instanceId }),
    listConsumerGroups({ instanceId: request.instanceId }),
  ]);
  const existingTopics = new Map(topics.map((topic) => [normalizeName(topic.name), topic]));
  const existingGroups = new Map(groups.map((group) => [normalizeName(group.name), group]));
  const entries = [
    ...planTopicEntries(desiredTopics, existingTopics),
    ...planConsumerGroupEntries(desiredGroups, existingGroups),
  ];

  return {
    instanceId: request.instanceId.trim(),
    summary: summarizeEntries(entries),
    entries,
  };
}

function planTopicEntries(
  desiredTopics: ResourcePlanTopicSpec[],
  existingTopics: Map<string, Topic>,
): ResourcePlanEntry[] {
  const seen = new Set<string>();
  return desiredTopics.map((topic, index) => {
    const name = normalizeName(topic?.name);
    if (!name) return invalidEntry('TOPIC', name, index, 'Topic name is required');
    if (seen.has(name))
      return invalidEntry('TOPIC', name, index, 'Duplicate topic in resource plan');
    seen.add(name);
    if (topic.writeQueues !== undefined && topic.writeQueues < 0) {
      return invalidEntry('TOPIC', name, index, 'Topic writeQueues must be zero or positive');
    }
    if (topic.readQueues !== undefined && topic.readQueues < 0) {
      return invalidEntry('TOPIC', name, index, 'Topic readQueues must be zero or positive');
    }

    const existing = existingTopics.get(name);
    if (!existing) {
      return entry(
        'TOPIC',
        name,
        index,
        'CREATE',
        true,
        'Topic does not exist in the selected instance',
      );
    }
    const changes = collectChanges([
      ['namespace', existing.namespace, topic.namespace],
      ['clusterId', existing.clusterId, topic.clusterId],
      ['type', existing.type, topic.type],
      ['writeQueues', existing.writeQueues, topic.writeQueues],
      ['readQueues', existing.readQueues, topic.readQueues],
      ['perm', existing.perm, topic.perm],
      ['remark', existing.remark, topic.remark],
    ]);
    if (changes.length === 0) {
      return entry(
        'TOPIC',
        name,
        index,
        'SKIP',
        false,
        'Topic already matches the desired configuration',
      );
    }
    return entry(
      'TOPIC',
      name,
      index,
      'UPDATE',
      true,
      'Topic exists with different configuration',
      changes,
    );
  });
}

function planConsumerGroupEntries(
  desiredGroups: ResourcePlanConsumerGroupSpec[],
  existingGroups: Map<string, ConsumerGroup>,
): ResourcePlanEntry[] {
  const seen = new Set<string>();
  return desiredGroups.map((group, index) => {
    const name = normalizeName(group?.name);
    if (!name)
      return invalidEntry('CONSUMER_GROUP', name, index, 'Consumer group name is required');
    if (seen.has(name)) {
      return invalidEntry(
        'CONSUMER_GROUP',
        name,
        index,
        'Duplicate consumer group in resource plan',
      );
    }
    seen.add(name);
    if (group.retryMaxTimes !== undefined && group.retryMaxTimes < 0) {
      return invalidEntry(
        'CONSUMER_GROUP',
        name,
        index,
        'Consumer group retryMaxTimes must be zero or positive',
      );
    }
    if (group.delaySeconds !== undefined && group.delaySeconds < 0) {
      return invalidEntry(
        'CONSUMER_GROUP',
        name,
        index,
        'Consumer group delaySeconds must be zero or positive',
      );
    }
    if (!isStringArrayOrUndefined(group.subscribedTopics)) {
      return invalidEntry(
        'CONSUMER_GROUP',
        name,
        index,
        'Consumer group subscribedTopics must be an array of strings',
      );
    }

    const existing = existingGroups.get(name);
    if (!existing) {
      return entry(
        'CONSUMER_GROUP',
        name,
        index,
        'CREATE',
        true,
        'Consumer group does not exist in the selected instance',
      );
    }
    const changes = collectChanges([
      ['namespace', existing.namespace, group.namespace],
      ['clusterId', existing.clusterId, group.clusterId],
      ['subscriptionMode', existing.subscriptionMode, group.subscriptionMode],
      ['consumeType', existing.consumeType, group.consumeType],
      [
        'subscribedTopics',
        sortedTopics(existing.subscribedTopics),
        sortedTopics(group.subscribedTopics),
      ],
      ['subscriptionDataType', existing.subscriptionDataType, group.subscriptionDataType],
      ['deliveryOrderType', existing.deliveryOrderType, group.deliveryOrderType],
      ['retryMaxTimes', existing.retryMaxTimes, group.retryMaxTimes],
      ['delaySeconds', existing.delaySeconds, group.delaySeconds],
    ]);
    if (changes.length === 0) {
      return entry(
        'CONSUMER_GROUP',
        name,
        index,
        'SKIP',
        false,
        'Consumer group already matches the desired configuration',
      );
    }
    return entry(
      'CONSUMER_GROUP',
      name,
      index,
      'CONFLICT',
      false,
      'Consumer group exists with unsupported in-place changes',
      changes,
    );
  });
}

function collectChanges(rows: Array<[string, unknown, unknown]>): ResourcePlanChange[] {
  return rows
    .filter(([, , desiredValue]) => desiredValue !== undefined)
    .filter(
      ([, currentValue, desiredValue]) => comparable(currentValue) !== comparable(desiredValue),
    )
    .map(([field, currentValue, desiredValue]) => ({
      field,
      currentValue: currentValue == null ? null : String(currentValue),
      desiredValue: desiredValue == null ? null : String(desiredValue),
    }));
}

function summarizeEntries(entries: ResourcePlanEntry[]): ResourcePlanSummary {
  return entries.reduce<ResourcePlanSummary>(
    (summary, item) => {
      summary.total++;
      if (item.applicable) summary.applicable++;
      if (item.action === 'CREATE') summary.creates++;
      if (item.action === 'UPDATE') summary.updates++;
      if (item.action === 'SKIP') summary.skips++;
      if (item.action === 'CONFLICT') summary.conflicts++;
      if (item.action === 'INVALID') summary.invalids++;
      return summary;
    },
    { total: 0, creates: 0, updates: 0, skips: 0, conflicts: 0, invalids: 0, applicable: 0 },
  );
}

function invalidEntry(
  resourceType: ResourcePlanResourceType,
  name: string,
  index: number,
  reason: string,
): ResourcePlanEntry {
  return entry(resourceType, name, index, 'INVALID', false, reason);
}

function entry(
  resourceType: ResourcePlanResourceType,
  name: string,
  index: number,
  action: ResourcePlanAction,
  applicable: boolean,
  reason: string,
  changes: ResourcePlanChange[] = [],
): ResourcePlanEntry {
  return {
    resourceType,
    name,
    rowIndex: index + 1,
    action,
    applicable,
    reason,
    changes,
  };
}

function normalizeName(value: unknown): string {
  return typeof value === 'string' ? value.trim() : '';
}

function comparable(value: unknown): string | null {
  return value == null ? null : String(value).trim();
}

function sortedTopics(topics?: string[]): string | undefined {
  return topics
    ?.filter((topic) => topic.trim())
    .map((topic) => topic.trim())
    .sort()
    .join(',');
}

function isStringArrayOrUndefined(value: unknown): value is string[] | undefined {
  return (
    value === undefined ||
    (Array.isArray(value) && value.every((item) => typeof item === 'string'))
  );
}
