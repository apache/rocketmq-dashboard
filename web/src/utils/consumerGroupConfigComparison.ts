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

import type { ConsumerGroup } from '../api/metadata';

export type ConsumerGroupComparisonStatus = 'MATCH' | 'DRIFT' | 'ONLY_SOURCE' | 'ONLY_TARGET';
export type ConsumerGroupConfigField =
  | 'namespace'
  | 'subscriptionMode'
  | 'consumeType'
  | 'subscriptionDataType'
  | 'deliveryOrderType'
  | 'retryMaxTimes';

export interface ConsumerGroupFieldDifference {
  field: ConsumerGroupConfigField;
  sourceValue: string | number;
  targetValue: string | number;
}

export interface ConsumerGroupComparisonRow {
  key: string;
  groupName: string;
  status: ConsumerGroupComparisonStatus;
  source?: ConsumerGroup;
  target?: ConsumerGroup;
  differences: ConsumerGroupFieldDifference[];
}

export interface ConsumerGroupComparisonSummary {
  total: number;
  matches: number;
  drifted: number;
  onlySource: number;
  onlyTarget: number;
}

export interface ConsumerGroupComparisonResult {
  rows: ConsumerGroupComparisonRow[];
  summary: ConsumerGroupComparisonSummary;
}

export const CONSUMER_GROUP_CONFIG_FIELDS: ConsumerGroupConfigField[] = [
  'namespace',
  'subscriptionMode',
  'consumeType',
  'subscriptionDataType',
  'deliveryOrderType',
  'retryMaxTimes',
];

const normalizedValue = (
  group: ConsumerGroup,
  field: ConsumerGroupConfigField,
): string | number => {
  const value = group[field];
  if (typeof value === 'string') return value.trim();
  if (typeof value === 'number') return value;
  return '';
};

const differencesBetween = (
  source: ConsumerGroup,
  target: ConsumerGroup,
): ConsumerGroupFieldDifference[] =>
  CONSUMER_GROUP_CONFIG_FIELDS.flatMap((field) => {
    const sourceValue = normalizedValue(source, field);
    const targetValue = normalizedValue(target, field);
    return sourceValue === targetValue ? [] : [{ field, sourceValue, targetValue }];
  });

export const compareConsumerGroupInventories = (
  sourceGroups: ConsumerGroup[],
  targetGroups: ConsumerGroup[],
): ConsumerGroupComparisonResult => {
  const sourceByName = new Map(sourceGroups.map((group) => [group.name, group]));
  const targetByName = new Map(targetGroups.map((group) => [group.name, group]));
  const names = [...new Set([...sourceByName.keys(), ...targetByName.keys()])].sort((left, right) =>
    left.localeCompare(right),
  );

  const rows = names.map((groupName): ConsumerGroupComparisonRow => {
    const source = sourceByName.get(groupName);
    const target = targetByName.get(groupName);
    if (!source) {
      return { key: groupName, groupName, status: 'ONLY_TARGET', target, differences: [] };
    }
    if (!target) {
      return { key: groupName, groupName, status: 'ONLY_SOURCE', source, differences: [] };
    }
    const differences = differencesBetween(source, target);
    return {
      key: groupName,
      groupName,
      status: differences.length === 0 ? 'MATCH' : 'DRIFT',
      source,
      target,
      differences,
    };
  });

  return {
    rows,
    summary: {
      total: rows.length,
      matches: rows.filter((row) => row.status === 'MATCH').length,
      drifted: rows.filter((row) => row.status === 'DRIFT').length,
      onlySource: rows.filter((row) => row.status === 'ONLY_SOURCE').length,
      onlyTarget: rows.filter((row) => row.status === 'ONLY_TARGET').length,
    },
  };
};

export const filterConsumerGroupComparisonRows = (
  rows: ConsumerGroupComparisonRow[],
  status: ConsumerGroupComparisonStatus | 'ALL',
  search: string,
) => {
  const normalizedSearch = search.trim().toLocaleLowerCase();
  return rows.filter(
    (row) =>
      (status === 'ALL' || row.status === status) &&
      (!normalizedSearch || row.groupName.toLocaleLowerCase().includes(normalizedSearch)),
  );
};

export const formatConsumerGroupDifferences = (differences: ConsumerGroupFieldDifference[]) =>
  differences
    .map(({ field, sourceValue, targetValue }) => `${field}: ${sourceValue} -> ${targetValue}`)
    .join('; ');
