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

import type {
  BrokerConfigDiffResult,
  BrokerConfigDifference,
  NameServerConfigDiffResult,
  NameServerConfigDifference,
} from '../api/cluster';

export type ConfigDriftKind = 'nameserver' | 'broker';
export type ConfigDriftStatus = 'clean' | 'review' | 'blocked';
export type ConfigDriftFieldStatus = 'alignable' | 'unconfigured' | 'ambiguous';

export interface ConfigDriftTarget {
  key: string;
  label: string;
  address: string;
  reachable: boolean;
  message?: string | null;
}

export interface ConfigDriftValueGroup {
  value: string | null;
  label: string;
  count: number;
  targets: string[];
}

export interface ConfigDriftFieldInsight {
  key: string;
  label: string;
  property?: string;
  status: ConfigDriftFieldStatus;
  targetCount: number;
  configuredCount: number;
  unconfiguredCount: number;
  distinctValueCount: number;
  majorityValue: string | null;
  majorityCount: number;
  valueGroups: ConfigDriftValueGroup[];
  minorityTargets: string[];
  unconfiguredTargets: string[];
}

export interface ConfigDriftInsightSummary {
  targetCount: number;
  reachableCount: number;
  unreachableCount: number;
  comparedFieldCount: number;
  differenceCount: number;
  alignableCount: number;
  unconfiguredCount: number;
  ambiguousCount: number;
}

export interface ConfigDriftInsights {
  kind: ConfigDriftKind;
  status: ConfigDriftStatus;
  complete: boolean;
  driftDetected: boolean;
  summary: ConfigDriftInsightSummary;
  targets: ConfigDriftTarget[];
  unreachableTargets: ConfigDriftTarget[];
  fields: ConfigDriftFieldInsight[];
  recommendations: string[];
  remediationPlan: string;
}

type FieldLabeler = (field: string) => string;

interface ConfigValue {
  targetKey: string;
  targetLabel: string;
  configured: boolean;
  value: string | null;
}

interface DifferenceInput {
  key: string;
  label: string;
  property?: string;
  values: ConfigValue[];
}

const valueLabel = (value: string | null): string => value ?? '<empty>';

const sortTexts = (items: string[]): string[] =>
  [...items].sort((left, right) => left.localeCompare(right));

const topValueGroups = (values: ConfigValue[]): ConfigDriftValueGroup[] => {
  const groups = new Map<string, ConfigDriftValueGroup>();
  values
    .filter((value) => value.configured)
    .forEach((value) => {
      const key = valueLabel(value.value);
      const group =
        groups.get(key) ??
        ({
          value: value.value,
          label: valueLabel(value.value),
          count: 0,
          targets: [],
        } satisfies ConfigDriftValueGroup);
      group.count += 1;
      group.targets.push(value.targetLabel);
      groups.set(key, group);
    });

  return [...groups.values()]
    .map((group) => ({ ...group, targets: sortTexts(group.targets) }))
    .sort((left, right) => right.count - left.count || left.label.localeCompare(right.label));
};

const summarizeDifference = (difference: DifferenceInput): ConfigDriftFieldInsight => {
  const configuredValues = difference.values.filter((value) => value.configured);
  const unconfiguredTargets = sortTexts(
    difference.values.filter((value) => !value.configured).map((value) => value.targetLabel),
  );
  const groups = topValueGroups(difference.values);
  const topGroup = groups[0];
  const secondGroup = groups[1];
  const hasSingleReferenceValue = groups.length === 1;
  const hasStrictMajority =
    topGroup !== undefined && (secondGroup === undefined || topGroup.count > secondGroup.count);

  const majorityValue = hasStrictMajority ? topGroup.value : null;
  const majorityCount = hasStrictMajority ? topGroup.count : 0;
  const minorityTargets =
    hasStrictMajority && topGroup
      ? sortTexts(
          groups
            .filter((group) => group.label !== topGroup.label)
            .flatMap((group) => group.targets),
        )
      : [];

  const status: ConfigDriftFieldStatus =
    configuredValues.length === 0 || !hasStrictMajority
      ? 'ambiguous'
      : hasSingleReferenceValue && unconfiguredTargets.length > 0
        ? 'unconfigured'
        : 'alignable';

  return {
    key: difference.key,
    label: difference.label,
    ...(difference.property ? { property: difference.property } : {}),
    status,
    targetCount: difference.values.length,
    configuredCount: configuredValues.length,
    unconfiguredCount: unconfiguredTargets.length,
    distinctValueCount: groups.length,
    majorityValue,
    majorityCount,
    valueGroups: groups,
    minorityTargets,
    unconfiguredTargets,
  };
};

const buildRecommendations = (
  kind: ConfigDriftKind,
  status: ConfigDriftStatus,
  summary: ConfigDriftInsightSummary,
): string[] => {
  if (status === 'clean') {
    return ['当前配置差异结果未发现需要处理的漂移项，保留现有配置并按计划巡检即可。'];
  }

  const targetName = kind === 'broker' ? 'Broker' : 'NameServer';
  const recommendations: string[] = [];

  if (summary.unreachableCount > 0) {
    recommendations.push(
      `先恢复 ${summary.unreachableCount} 个不可达 ${targetName} 的管理链路，再重新运行配置差异检测。`,
    );
  }
  if (summary.alignableCount > 0) {
    recommendations.push(
      `有 ${summary.alignableCount} 个字段存在可参考的多数值，可把少数值节点列入变更评审。`,
    );
  }
  if (summary.unconfiguredCount > 0) {
    recommendations.push(
      `有 ${summary.unconfiguredCount} 个字段包含未配置节点，先确认默认值是否符合生产预期。`,
    );
  }
  if (summary.ambiguousCount > 0) {
    recommendations.push(
      `有 ${summary.ambiguousCount} 个字段没有明确多数值，需人工选择源配置，避免误把某个节点当成标准。`,
    );
  }

  return recommendations;
};

const targetLine = (targets: string[]) => (targets.length > 0 ? targets.join(', ') : '-');

const buildRemediationPlan = (kind: ConfigDriftKind, insights: ConfigDriftFieldInsight[]) => {
  const title =
    kind === 'broker'
      ? 'Broker configuration drift remediation checklist'
      : 'NameServer configuration drift remediation checklist';
  const lines = [title];

  if (insights.length === 0) {
    lines.push('- No drift fields were returned by the latest check.');
    return lines.join('\n');
  }

  insights.forEach((field) => {
    const property = field.property ? ` (${field.property})` : '';
    lines.push(`- ${field.label}${property}`);
    if (field.status === 'ambiguous') {
      lines.push('  - Pick the intended source-of-truth value before applying any change.');
    } else {
      lines.push(`  - Review reference value: ${valueLabel(field.majorityValue)}.`);
    }
    if (field.minorityTargets.length > 0) {
      lines.push(`  - Outlier targets: ${targetLine(field.minorityTargets)}.`);
    }
    if (field.unconfiguredTargets.length > 0) {
      lines.push(`  - Unconfigured targets: ${targetLine(field.unconfiguredTargets)}.`);
    }
  });

  return lines.join('\n');
};

function buildInsights(
  kind: ConfigDriftKind,
  complete: boolean,
  driftDetected: boolean,
  comparedFieldCount: number,
  targets: ConfigDriftTarget[],
  differences: DifferenceInput[],
): ConfigDriftInsights {
  const fields = differences.map(summarizeDifference);
  const unreachableTargets = targets.filter((target) => !target.reachable);
  const summary: ConfigDriftInsightSummary = {
    targetCount: targets.length,
    reachableCount: targets.length - unreachableTargets.length,
    unreachableCount: unreachableTargets.length,
    comparedFieldCount,
    differenceCount: fields.length,
    alignableCount: fields.filter((field) => field.status === 'alignable').length,
    unconfiguredCount: fields.filter((field) => field.unconfiguredCount > 0).length,
    ambiguousCount: fields.filter((field) => field.status === 'ambiguous').length,
  };
  const status: ConfigDriftStatus =
    !complete || summary.unreachableCount > 0
      ? 'blocked'
      : driftDetected || summary.differenceCount > 0
        ? 'review'
        : 'clean';

  return {
    kind,
    status,
    complete,
    driftDetected,
    summary,
    targets,
    unreachableTargets,
    fields,
    recommendations: buildRecommendations(kind, status, summary),
    remediationPlan: buildRemediationPlan(kind, fields),
  };
}

export function buildNameServerConfigDriftInsights(
  result: NameServerConfigDiffResult,
): ConfigDriftInsights {
  const targets = result.nodes.map<ConfigDriftTarget>((node) => ({
    key: node.address,
    label: node.address,
    address: node.address,
    reachable: node.reachable,
  }));
  const differences = result.differences.map<DifferenceInput>(
    (difference: NameServerConfigDifference) => ({
      key: difference.key,
      label: difference.key,
      values: difference.values.map((value) => ({
        targetKey: value.address,
        targetLabel: value.address,
        configured: value.configured,
        value: value.value,
      })),
    }),
  );

  return buildInsights(
    'nameserver',
    result.complete,
    result.driftDetected,
    result.comparedKeys.length,
    targets,
    differences,
  );
}

export function buildBrokerConfigDriftInsights(
  result: BrokerConfigDiffResult,
  labelField: FieldLabeler = (field) => field,
): ConfigDriftInsights {
  const targets = result.brokers.map<ConfigDriftTarget>((broker) => ({
    key: broker.address,
    label: broker.name || broker.address,
    address: broker.address,
    reachable: broker.reachable,
    message: broker.message,
  }));
  const differences = result.differences.map<DifferenceInput>(
    (difference: BrokerConfigDifference) => ({
      key: difference.field,
      label: labelField(difference.field),
      property: difference.brokerProperty,
      values: difference.values.map((value) => ({
        targetKey: value.address,
        targetLabel: value.brokerName || value.address,
        configured: value.configured,
        value: value.value,
      })),
    }),
  );

  return buildInsights(
    'broker',
    result.complete,
    result.driftDetected,
    result.comparedFields.length,
    targets,
    differences,
  );
}
