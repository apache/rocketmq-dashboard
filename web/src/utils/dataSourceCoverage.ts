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

import type { Instance } from '../api/instance';
import type { DataSource } from '../api/settings';

export type DataSourceCoverageStatus = 'healthy' | 'warning' | 'critical' | 'empty';
export type DataSourceCoverageSeverity = 'critical' | 'warning' | 'info';
export type DataSourceCoverageHealth = 'healthy' | 'warning' | 'unhealthy' | 'untested';
export type DataSourceScope = 'global' | 'scoped';

export type DataSourceCoverageIssueCode =
  | 'NO_INSTANCES'
  | 'NO_DATA_SOURCES'
  | 'INSTANCE_UNCOVERED'
  | 'INSTANCE_NO_USABLE_SOURCE'
  | 'INSTANCE_MULTIPLE_SAME_TYPE'
  | 'INSTANCE_ONLY_UNTESTED'
  | 'SOURCE_STALE_INSTANCE'
  | 'SOURCE_UNHEALTHY'
  | 'DUPLICATE_SOURCE_TARGET';

export interface DataSourceCoverageIssue {
  code: DataSourceCoverageIssueCode;
  severity: DataSourceCoverageSeverity;
  message: string;
  instanceId?: string;
  dataSourceKey?: string;
  dataSourceKeys?: string[];
  type?: string;
}

export interface DataSourceReference {
  key: string;
  name: string;
  type: string;
  url: string;
  auth: string;
  scope: DataSourceScope;
  status?: string | null;
  health: DataSourceCoverageHealth;
  configuredInstanceIds: string[];
  matchedInstanceIds: string[];
  staleInstanceIds: string[];
}

export interface DataSourceInstanceCoverage {
  instanceId: string;
  instanceName: string;
  endpoint: string;
  sourceRefs: DataSourceReference[];
  scopedSourceCount: number;
  globalSourceCount: number;
  usableSourceCount: number;
  verifiedSourceCount: number;
  status: DataSourceCoverageStatus;
  issues: DataSourceCoverageIssue[];
}

export interface DataSourceTypeCoverage {
  type: string;
  total: number;
  global: number;
  scoped: number;
  healthy: number;
  warning: number;
  unhealthy: number;
  untested: number;
  instanceCount: number;
}

export interface DataSourceCoverageSummary {
  status: DataSourceCoverageStatus;
  dataSourceCount: number;
  instanceCount: number;
  coveredInstanceCount: number;
  verifiedInstanceCount: number;
  uncoveredInstanceCount: number;
  conflictedInstanceCount: number;
  globalDataSourceCount: number;
  scopedDataSourceCount: number;
  unhealthyDataSourceCount: number;
  untestedDataSourceCount: number;
  issues: DataSourceCoverageIssue[];
  sourceRefs: DataSourceReference[];
  instanceCoverage: DataSourceInstanceCoverage[];
  typeCoverage: DataSourceTypeCoverage[];
}

const HEALTHY_STATUS = new Set(['healthy', 'success', 'ok', 'up']);
const WARNING_STATUS = new Set(['warning', 'degraded', 'connecting']);
const UNHEALTHY_STATUS = new Set(['error', 'offline', 'failed', 'down', 'unhealthy']);

const normalizeText = (value?: string | null) => value?.trim() ?? '';

const normalizeStatus = (status?: string | null): DataSourceCoverageHealth => {
  const value = normalizeText(status).toLowerCase();
  if (!value) return 'untested';
  if (HEALTHY_STATUS.has(value)) return 'healthy';
  if (WARNING_STATUS.has(value)) return 'warning';
  if (UNHEALTHY_STATUS.has(value)) return 'unhealthy';
  return 'warning';
};

const normalizeUrl = (url: string) => {
  const value = normalizeText(url);
  if (!value) return '';
  try {
    const parsed = new URL(value);
    parsed.hash = '';
    parsed.search = '';
    parsed.pathname = parsed.pathname.replace(/\/+$/, '');
    parsed.hostname = parsed.hostname.toLowerCase();
    return parsed.toString().replace(/\/+$/, '');
  } catch {
    return value.toLowerCase().replace(/\/+$/, '');
  }
};

const unique = (values: string[]) => [...new Set(values.filter(Boolean))];

const instanceAliases = (instance: Instance) =>
  unique([instance.name, String(instance.id), instance.endpoint].map(normalizeText));

const issueRank = (issue: DataSourceCoverageIssue) => {
  const severityRank: Record<DataSourceCoverageSeverity, number> = {
    critical: 0,
    warning: 1,
    info: 2,
  };
  return `${severityRank[issue.severity]}:${issue.code}:${issue.instanceId ?? ''}:${
    issue.dataSourceKey ?? ''
  }`;
};

const sortIssues = (issues: DataSourceCoverageIssue[]) =>
  [...issues].sort((left, right) => issueRank(left).localeCompare(issueRank(right)));

const statusFromIssues = (issues: DataSourceCoverageIssue[]): DataSourceCoverageStatus => {
  if (issues.some((issue) => issue.severity === 'critical')) return 'critical';
  if (issues.some((issue) => issue.severity === 'warning')) return 'warning';
  return 'healthy';
};

const hasUsableSource = (source: DataSourceReference) => source.health !== 'unhealthy';
const hasVerifiedSource = (source: DataSourceReference) =>
  source.health === 'healthy' || source.health === 'warning';

const buildSourceRefs = (
  dataSources: DataSource[],
  instances: Instance[],
): DataSourceReference[] => {
  const aliasToInstanceName = new Map<string, string>();
  instances.forEach((instance) => {
    instanceAliases(instance).forEach((alias) => aliasToInstanceName.set(alias, instance.name));
  });

  return dataSources.map((source) => {
    const configuredInstanceIds = unique((source.instanceIds ?? []).map(normalizeText));
    const matchedInstanceIds = unique(
      configuredInstanceIds
        .map((id) => aliasToInstanceName.get(id) ?? '')
        .filter((id) => Boolean(id)),
    );
    const staleInstanceIds = configuredInstanceIds.filter((id) => !aliasToInstanceName.has(id));
    return {
      key: source.key,
      name: source.name,
      type: source.type,
      url: source.url,
      auth: source.auth,
      scope: configuredInstanceIds.length === 0 ? 'global' : 'scoped',
      status: source.status,
      health: normalizeStatus(source.status),
      configuredInstanceIds,
      matchedInstanceIds,
      staleInstanceIds,
    };
  });
};

const sourceAppliesToInstance = (source: DataSourceReference, instance: Instance) => {
  if (source.scope === 'global') return true;
  const aliases = new Set(instanceAliases(instance));
  return source.configuredInstanceIds.some((id) => aliases.has(id));
};

const groupByType = (sources: DataSourceReference[]) =>
  sources.reduce((groups, source) => {
    const current = groups.get(source.type) ?? [];
    current.push(source);
    groups.set(source.type, current);
    return groups;
  }, new Map<string, DataSourceReference[]>());

const buildInstanceIssues = (
  instance: Instance,
  sourceRefs: DataSourceReference[],
): DataSourceCoverageIssue[] => {
  const issues: DataSourceCoverageIssue[] = [];
  if (sourceRefs.length === 0) {
    issues.push({
      code: 'INSTANCE_UNCOVERED',
      severity: 'critical',
      instanceId: instance.name,
      message: `${instance.name} has no applicable metrics data source.`,
    });
    return issues;
  }

  if (!sourceRefs.some(hasUsableSource)) {
    issues.push({
      code: 'INSTANCE_NO_USABLE_SOURCE',
      severity: 'critical',
      instanceId: instance.name,
      dataSourceKeys: sourceRefs.map((source) => source.key),
      message: `${instance.name} is only covered by unavailable data sources.`,
    });
  }

  const typeGroups = groupByType(sourceRefs);
  typeGroups.forEach((sources, type) => {
    if (sources.length > 1) {
      issues.push({
        code: 'INSTANCE_MULTIPLE_SAME_TYPE',
        severity: 'warning',
        instanceId: instance.name,
        dataSourceKeys: sources.map((source) => source.key),
        type,
        message: `${instance.name} matches ${sources.length} ${type} data sources.`,
      });
    }
  });

  if (sourceRefs.some((source) => source.health === 'unhealthy')) {
    issues.push({
      code: 'SOURCE_UNHEALTHY',
      severity: 'warning',
      instanceId: instance.name,
      dataSourceKeys: sourceRefs
        .filter((source) => source.health === 'unhealthy')
        .map((source) => source.key),
      message: `${instance.name} has unavailable data sources in its effective set.`,
    });
  }

  if (sourceRefs.every((source) => source.health === 'untested')) {
    issues.push({
      code: 'INSTANCE_ONLY_UNTESTED',
      severity: 'warning',
      instanceId: instance.name,
      dataSourceKeys: sourceRefs.map((source) => source.key),
      message: `${instance.name} is only covered by untested data sources.`,
    });
  }

  return issues;
};

const buildInstanceCoverage = (
  instances: Instance[],
  sourceRefs: DataSourceReference[],
): DataSourceInstanceCoverage[] =>
  instances.map((instance) => {
    const refs = sourceRefs.filter((source) => sourceAppliesToInstance(source, instance));
    const issues = buildInstanceIssues(instance, refs);
    return {
      instanceId: instance.name,
      instanceName: instance.name,
      endpoint: instance.endpoint,
      sourceRefs: refs,
      scopedSourceCount: refs.filter((source) => source.scope === 'scoped').length,
      globalSourceCount: refs.filter((source) => source.scope === 'global').length,
      usableSourceCount: refs.filter(hasUsableSource).length,
      verifiedSourceCount: refs.filter(hasVerifiedSource).length,
      status: statusFromIssues(issues),
      issues: sortIssues(issues),
    };
  });

const buildSourceIssues = (sourceRefs: DataSourceReference[]): DataSourceCoverageIssue[] => {
  const issues: DataSourceCoverageIssue[] = [];

  sourceRefs.forEach((source) => {
    if (source.staleInstanceIds.length > 0) {
      issues.push({
        code: 'SOURCE_STALE_INSTANCE',
        severity: 'warning',
        dataSourceKey: source.key,
        message: `${source.name} references unknown instances: ${source.staleInstanceIds.join(
          ', ',
        )}.`,
      });
    }
  });

  const duplicateGroups = sourceRefs.reduce((groups, source) => {
    const key = `${source.type}::${normalizeUrl(source.url)}`;
    const current = groups.get(key) ?? [];
    current.push(source);
    groups.set(key, current);
    return groups;
  }, new Map<string, DataSourceReference[]>());

  duplicateGroups.forEach((sources) => {
    if (sources.length <= 1) return;
    issues.push({
      code: 'DUPLICATE_SOURCE_TARGET',
      severity: 'warning',
      dataSourceKeys: sources.map((source) => source.key),
      type: sources[0].type,
      message: `${sources.length} ${sources[0].type} data sources point to the same endpoint.`,
    });
  });

  return issues;
};

const buildTypeCoverage = (
  sourceRefs: DataSourceReference[],
  instances: Instance[],
): DataSourceTypeCoverage[] => {
  const types = [...new Set(sourceRefs.map((source) => source.type))].sort((a, b) =>
    a.localeCompare(b),
  );

  return types.map((type) => {
    const typedSources = sourceRefs.filter((source) => source.type === type);
    const instanceCount = instances.filter((instance) =>
      typedSources.some((source) => sourceAppliesToInstance(source, instance)),
    ).length;
    return {
      type,
      total: typedSources.length,
      global: typedSources.filter((source) => source.scope === 'global').length,
      scoped: typedSources.filter((source) => source.scope === 'scoped').length,
      healthy: typedSources.filter((source) => source.health === 'healthy').length,
      warning: typedSources.filter((source) => source.health === 'warning').length,
      unhealthy: typedSources.filter((source) => source.health === 'unhealthy').length,
      untested: typedSources.filter((source) => source.health === 'untested').length,
      instanceCount,
    };
  });
};

export function analyzeDataSourceCoverage(
  dataSources: DataSource[],
  instances: Instance[],
): DataSourceCoverageSummary {
  const sourceRefs = buildSourceRefs(dataSources, instances);
  const instanceCoverage = buildInstanceCoverage(instances, sourceRefs);
  const issues: DataSourceCoverageIssue[] = [];

  if (instances.length === 0) {
    issues.push({
      code: 'NO_INSTANCES',
      severity: 'info',
      message: 'No instances are available for data source coverage analysis.',
    });
  }

  if (dataSources.length === 0) {
    issues.push({
      code: 'NO_DATA_SOURCES',
      severity: instances.length > 0 ? 'critical' : 'info',
      message: 'No metrics data sources are configured.',
    });
  }

  issues.push(...instanceCoverage.flatMap((coverage) => coverage.issues));
  issues.push(...buildSourceIssues(sourceRefs));

  const sortedIssues = sortIssues(issues);
  const hasCritical = sortedIssues.some((issue) => issue.severity === 'critical');
  const hasWarning = sortedIssues.some((issue) => issue.severity === 'warning');
  const status: DataSourceCoverageStatus =
    dataSources.length === 0 && instances.length === 0
      ? 'empty'
      : hasCritical
        ? 'critical'
        : hasWarning
          ? 'warning'
          : 'healthy';

  return {
    status,
    dataSourceCount: dataSources.length,
    instanceCount: instances.length,
    coveredInstanceCount: instanceCoverage.filter((coverage) => coverage.sourceRefs.length > 0)
      .length,
    verifiedInstanceCount: instanceCoverage.filter((coverage) => coverage.verifiedSourceCount > 0)
      .length,
    uncoveredInstanceCount: instanceCoverage.filter((coverage) =>
      coverage.issues.some((issue) => issue.code === 'INSTANCE_UNCOVERED'),
    ).length,
    conflictedInstanceCount: instanceCoverage.filter((coverage) =>
      coverage.issues.some((issue) => issue.code === 'INSTANCE_MULTIPLE_SAME_TYPE'),
    ).length,
    globalDataSourceCount: sourceRefs.filter((source) => source.scope === 'global').length,
    scopedDataSourceCount: sourceRefs.filter((source) => source.scope === 'scoped').length,
    unhealthyDataSourceCount: sourceRefs.filter((source) => source.health === 'unhealthy').length,
    untestedDataSourceCount: sourceRefs.filter((source) => source.health === 'untested').length,
    issues: sortedIssues,
    sourceRefs,
    instanceCoverage,
    typeCoverage: buildTypeCoverage(sourceRefs, instances),
  };
}
