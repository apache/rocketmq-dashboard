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

import type { ProducerConnection } from '../api/producer';

export type ProducerGroupFinding =
  | 'DUPLICATE_CLIENT_ID'
  | 'MIXED_VERSION'
  | 'MIXED_LANGUAGE'
  | 'INCOMPLETE_METADATA'
  | 'UNREPORTED_GROUP';

export type ProducerGroupHealth = 'HEALTHY' | 'WARNING' | 'CRITICAL';

export interface ProducerDistributionItem {
  value: string;
  count: number;
}

export interface ProducerGroupCompositionRow {
  key: string;
  producerGroup: string;
  connections: number;
  uniqueClients: number;
  uniqueAddresses: number;
  languages: ProducerDistributionItem[];
  versions: ProducerDistributionItem[];
  findings: ProducerGroupFinding[];
  health: ProducerGroupHealth;
  clientIds: string[];
  addresses: string[];
}

export interface ProducerGroupComposition {
  rows: ProducerGroupCompositionRow[];
  summary: {
    groups: number;
    connections: number;
    healthy: number;
    warning: number;
    critical: number;
    duplicateClientGroups: number;
    incompleteMetadataGroups: number;
  };
}

export interface ProducerGroupCompositionFilters {
  search: string;
  health: ProducerGroupHealth | 'ALL';
  finding: ProducerGroupFinding | 'ALL';
}

const normalized = (value?: string | null) => value?.trim() ?? '';

const distribution = (values: string[]): ProducerDistributionItem[] => {
  const counts = new Map<string, number>();
  values.forEach((value) => counts.set(value, (counts.get(value) ?? 0) + 1));
  return [...counts.entries()]
    .map(([value, count]) => ({ value, count }))
    .sort((left, right) => right.count - left.count || left.value.localeCompare(right.value));
};

const duplicateValues = (values: string[]) => {
  const seen = new Set<string>();
  const duplicates = new Set<string>();
  values.filter(Boolean).forEach((value) => {
    if (seen.has(value)) duplicates.add(value);
    seen.add(value);
  });
  return duplicates;
};

const buildGroupRow = (
  producerGroup: string,
  connections: ProducerConnection[],
): ProducerGroupCompositionRow => {
  const clientIds = connections.map((item) => normalized(item.clientId));
  const addresses = connections.map((item) => normalized(item.clientAddr));
  const languages = connections.map((item) => normalized(item.language)).filter(Boolean);
  const versions = connections.map((item) => normalized(item.versionDesc)).filter(Boolean);
  const findings: ProducerGroupFinding[] = [];
  if (duplicateValues(clientIds).size > 0) findings.push('DUPLICATE_CLIENT_ID');
  if (new Set(versions).size > 1) findings.push('MIXED_VERSION');
  if (new Set(languages).size > 1) findings.push('MIXED_LANGUAGE');
  if (
    connections.some(
      (item) =>
        !normalized(item.clientId) ||
        !normalized(item.clientAddr) ||
        !normalized(item.language) ||
        !normalized(item.versionDesc),
    )
  ) {
    findings.push('INCOMPLETE_METADATA');
  }
  if (producerGroup === '') findings.push('UNREPORTED_GROUP');
  const health: ProducerGroupHealth = findings.includes('DUPLICATE_CLIENT_ID')
    ? 'CRITICAL'
    : findings.length > 0
      ? 'WARNING'
      : 'HEALTHY';
  return {
    key: producerGroup || '__UNREPORTED__',
    producerGroup,
    connections: connections.length,
    uniqueClients: new Set(clientIds.filter(Boolean)).size,
    uniqueAddresses: new Set(addresses.filter(Boolean)).size,
    languages: distribution(languages),
    versions: distribution(versions),
    findings,
    health,
    clientIds: [...new Set(clientIds.filter(Boolean))].sort(),
    addresses: [...new Set(addresses.filter(Boolean))].sort(),
  };
};

export const analyzeProducerGroupComposition = (
  connections: ProducerConnection[],
): ProducerGroupComposition => {
  const groups = new Map<string, ProducerConnection[]>();
  connections.forEach((connection) => {
    const group = normalized(connection.producerGroup);
    groups.set(group, [...(groups.get(group) ?? []), connection]);
  });
  const rows = [...groups.entries()]
    .map(([group, items]) => buildGroupRow(group, items))
    .sort((left, right) => {
      const rank = { CRITICAL: 2, WARNING: 1, HEALTHY: 0 };
      return (
        rank[right.health] - rank[left.health] ||
        left.producerGroup.localeCompare(right.producerGroup)
      );
    });
  return {
    rows,
    summary: {
      groups: rows.length,
      connections: connections.length,
      healthy: rows.filter((row) => row.health === 'HEALTHY').length,
      warning: rows.filter((row) => row.health === 'WARNING').length,
      critical: rows.filter((row) => row.health === 'CRITICAL').length,
      duplicateClientGroups: rows.filter((row) => row.findings.includes('DUPLICATE_CLIENT_ID'))
        .length,
      incompleteMetadataGroups: rows.filter((row) => row.findings.includes('INCOMPLETE_METADATA'))
        .length,
    },
  };
};

export const filterProducerGroupComposition = (
  rows: ProducerGroupCompositionRow[],
  filters: ProducerGroupCompositionFilters,
) => {
  const search = filters.search.trim().toLocaleLowerCase();
  return rows.filter(
    (row) =>
      (filters.health === 'ALL' || row.health === filters.health) &&
      (filters.finding === 'ALL' || row.findings.includes(filters.finding)) &&
      (!search ||
        [
          row.producerGroup,
          row.clientIds.join(' '),
          row.addresses.join(' '),
          row.languages.map((item) => item.value).join(' '),
          row.versions.map((item) => item.value).join(' '),
        ].some((value) => value.toLocaleLowerCase().includes(search))),
  );
};
