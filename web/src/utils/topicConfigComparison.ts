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

import type { Topic } from '../api/metadata';

export type TopicComparisonStatus = 'MATCH' | 'DRIFT' | 'ONLY_SOURCE' | 'ONLY_TARGET';
export type TopicConfigField = 'type' | 'namespace' | 'writeQueues' | 'readQueues' | 'perm';

export interface TopicFieldDifference {
  field: TopicConfigField;
  sourceValue: string | number;
  targetValue: string | number;
}

export interface TopicComparisonRow {
  key: string;
  topicName: string;
  status: TopicComparisonStatus;
  source?: Topic;
  target?: Topic;
  differences: TopicFieldDifference[];
}

export interface TopicComparisonSummary {
  total: number;
  matches: number;
  drifted: number;
  onlySource: number;
  onlyTarget: number;
}

export interface TopicComparisonResult {
  rows: TopicComparisonRow[];
  summary: TopicComparisonSummary;
}

export const TOPIC_CONFIG_FIELDS: TopicConfigField[] = [
  'type',
  'namespace',
  'writeQueues',
  'readQueues',
  'perm',
];

const valueOf = (topic: Topic, field: TopicConfigField): string | number => {
  const value = topic[field];
  return typeof value === 'string' ? value.trim() : value;
};

const differencesBetween = (source: Topic, target: Topic): TopicFieldDifference[] =>
  TOPIC_CONFIG_FIELDS.flatMap((field) => {
    const sourceValue = valueOf(source, field);
    const targetValue = valueOf(target, field);
    return sourceValue === targetValue ? [] : [{ field, sourceValue, targetValue }];
  });

/** Compares stable Topic configuration and intentionally excludes runtime counters and timestamps. */
export const compareTopicInventories = (
  sourceTopics: Topic[],
  targetTopics: Topic[],
): TopicComparisonResult => {
  const sourceByName = new Map(sourceTopics.map((topic) => [topic.name, topic]));
  const targetByName = new Map(targetTopics.map((topic) => [topic.name, topic]));
  const names = [...new Set([...sourceByName.keys(), ...targetByName.keys()])].sort((left, right) =>
    left.localeCompare(right),
  );

  const rows = names.map((topicName): TopicComparisonRow => {
    const source = sourceByName.get(topicName);
    const target = targetByName.get(topicName);
    if (!source) {
      return {
        key: topicName,
        topicName,
        status: 'ONLY_TARGET',
        target,
        differences: [],
      };
    }
    if (!target) {
      return {
        key: topicName,
        topicName,
        status: 'ONLY_SOURCE',
        source,
        differences: [],
      };
    }
    const differences = differencesBetween(source, target);
    return {
      key: topicName,
      topicName,
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

export const filterTopicComparisonRows = (
  rows: TopicComparisonRow[],
  status: TopicComparisonStatus | 'ALL',
  search: string,
) => {
  const normalizedSearch = search.trim().toLocaleLowerCase();
  return rows.filter(
    (row) =>
      (status === 'ALL' || row.status === status) &&
      (!normalizedSearch || row.topicName.toLocaleLowerCase().includes(normalizedSearch)),
  );
};

export const formatTopicDifferences = (differences: TopicFieldDifference[]) =>
  differences
    .map(({ field, sourceValue, targetValue }) => `${field}: ${sourceValue} -> ${targetValue}`)
    .join('; ');
