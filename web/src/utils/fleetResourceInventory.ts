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

import type { Instance, InstanceVendor } from '../api/instance';
import type { ConsumerGroup, Topic } from '../api/metadata';

export type FleetResourceKind = 'TOPIC' | 'CONSUMER_GROUP';

export interface FleetResourceRow {
  key: string;
  kind: FleetResourceKind;
  name: string;
  instanceId: string;
  vendor: InstanceVendor;
  clusterId: string;
  namespace: string;
  configuration: string;
  occurrenceCount: number;
  otherInstances: string[];
}

export interface FleetResourceSummary {
  instances: number;
  topics: number;
  consumerGroups: number;
  sharedNames: number;
  uniqueNames: number;
}

export interface FleetResourceInventory {
  rows: FleetResourceRow[];
  summary: FleetResourceSummary;
}

export interface FleetResourceFilters {
  kind: FleetResourceKind | 'ALL';
  instanceId: string | 'ALL';
  vendor: InstanceVendor | 'ALL';
  sharedOnly: boolean;
  search: string;
}

const normalizedVendor = (instance: Instance): InstanceVendor => instance.vendor ?? 'APACHE';

const topicRow = (instance: Instance, topic: Topic): FleetResourceRow => ({
  key: `TOPIC:${instance.name}:${topic.name}`,
  kind: 'TOPIC',
  name: topic.name,
  instanceId: instance.name,
  vendor: normalizedVendor(instance),
  clusterId: topic.clusterId ?? '',
  namespace: topic.namespace ?? '',
  configuration: `${topic.type} · ${topic.readQueues}/${topic.writeQueues} · ${topic.perm}`,
  occurrenceCount: 1,
  otherInstances: [],
});

const consumerGroupRow = (instance: Instance, group: ConsumerGroup): FleetResourceRow => ({
  key: `CONSUMER_GROUP:${instance.name}:${group.name}`,
  kind: 'CONSUMER_GROUP',
  name: group.name,
  instanceId: instance.name,
  vendor: normalizedVendor(instance),
  clusterId: group.clusterId ?? '',
  namespace: group.namespace ?? '',
  configuration: `${group.subscriptionMode} · ${group.consumeType} · retry ${group.retryMaxTimes}`,
  occurrenceCount: 1,
  otherInstances: [],
});

export const buildFleetResourceInventory = (
  instances: Instance[],
  topicsByInstance: Readonly<Record<string, Topic[]>>,
  groupsByInstance: Readonly<Record<string, ConsumerGroup[]>>,
): FleetResourceInventory => {
  const rows = instances.flatMap((instance) => [
    ...(topicsByInstance[instance.name] ?? []).map((topic) => topicRow(instance, topic)),
    ...(groupsByInstance[instance.name] ?? []).map((group) => consumerGroupRow(instance, group)),
  ]);
  const locations = new Map<string, Set<string>>();
  rows.forEach((row) => {
    const identity = `${row.kind}:${row.name}`;
    const instancesForName = locations.get(identity) ?? new Set<string>();
    instancesForName.add(row.instanceId);
    locations.set(identity, instancesForName);
  });

  const enrichedRows = rows
    .map((row) => {
      const instanceIds = [...(locations.get(`${row.kind}:${row.name}`) ?? [])].sort();
      return {
        ...row,
        occurrenceCount: instanceIds.length,
        otherInstances: instanceIds.filter((instanceId) => instanceId !== row.instanceId),
      };
    })
    .sort((left, right) => {
      const byKind = left.kind.localeCompare(right.kind);
      if (byKind !== 0) return byKind;
      const byName = left.name.localeCompare(right.name);
      return byName !== 0 ? byName : left.instanceId.localeCompare(right.instanceId);
    });

  const sharedNames = [...locations.values()].filter((instanceIds) => instanceIds.size > 1).length;
  return {
    rows: enrichedRows,
    summary: {
      instances: new Set(enrichedRows.map((row) => row.instanceId)).size,
      topics: enrichedRows.filter((row) => row.kind === 'TOPIC').length,
      consumerGroups: enrichedRows.filter((row) => row.kind === 'CONSUMER_GROUP').length,
      sharedNames,
      uniqueNames: locations.size,
    },
  };
};

export const filterFleetResourceRows = (
  rows: FleetResourceRow[],
  filters: FleetResourceFilters,
) => {
  const search = filters.search.trim().toLocaleLowerCase();
  return rows.filter(
    (row) =>
      (filters.kind === 'ALL' || row.kind === filters.kind) &&
      (filters.instanceId === 'ALL' || row.instanceId === filters.instanceId) &&
      (filters.vendor === 'ALL' || row.vendor === filters.vendor) &&
      (!filters.sharedOnly || row.occurrenceCount > 1) &&
      (!search ||
        [row.name, row.instanceId, row.clusterId, row.namespace, row.configuration].some((value) =>
          value.toLocaleLowerCase().includes(search),
        )),
  );
};

export const summarizeVisibleFleetResources = (rows: FleetResourceRow[]) => ({
  resources: rows.length,
  instances: new Set(rows.map((row) => row.instanceId)).size,
  names: new Set(rows.map((row) => `${row.kind}:${row.name}`)).size,
});
